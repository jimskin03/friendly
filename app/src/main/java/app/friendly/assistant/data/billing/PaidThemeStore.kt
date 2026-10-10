package app.friendly.assistant.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.ui.theme.PresetThemes
import app.friendly.assistant.ui.theme.isPaidThemeId
import app.friendly.assistant.ui.theme.presets.PaidThemes
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "PaidThemeStore"
private const val PREFS = "paid_theme_entitlements"
private const val KEY_OWNED = "owned_products"
private const val CONNECT_TIMEOUT_MS = 10_000L

/**
 * Play product ids for the paid themes: one non-consumable in-app product per theme
 * ("theme_" + theme id) plus one bundle that unlocks all of them.
 */
object PaidThemeProducts {
    const val BUNDLE = "theme_pack_all"
    private const val PREFIX = "theme_"

    fun forTheme(themeId: String): String = PREFIX + themeId

    fun themeFor(productId: String): String? =
        PaidThemes.firstOrNull { forTheme(it.id) == productId }?.id

    val themeProducts: List<String> get() = PaidThemes.map { forTheme(it.id) }
    val all: List<String> get() = themeProducts + BUNDLE
}

enum class StoreStatus {
    /** Build unlocks every paid theme (sideloaded nightly). No store UI. */
    Unlocked,

    /** Talking to Google Play. */
    Connecting,

    /** Prices loaded, purchases possible. */
    Ready,

    /** No Play billing here (not the Play Store version, no Play Store, or no products). */
    Unavailable,

    /** Play is there but did not answer (network, service down). Worth a retry. */
    Offline,
}

data class ProductOffer(
    val productId: String,
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
)

data class PaidThemeStoreState(
    val status: StoreStatus = StoreStatus.Connecting,
    val offers: Map<String, ProductOffer> = emptyMap(),
    /** Product ids the user owns (purchase state PURCHASED). */
    val owned: Set<String> = emptySet(),
    /** Product ids waiting on a slow payment method. */
    val pending: Set<String> = emptySet(),
    /** Product id whose purchase sheet is open. */
    val purchasing: String? = null,
) {
    val ownsBundle: Boolean get() = PaidThemeProducts.BUNDLE in owned

    fun isUnlocked(themeId: String): Boolean =
        status == StoreStatus.Unlocked || ownsBundle || PaidThemeProducts.forTheme(themeId) in owned

    fun isPending(themeId: String): Boolean =
        PaidThemeProducts.forTheme(themeId) in pending || PaidThemeProducts.BUNDLE in pending

    fun priceFor(themeId: String): String? = offers[PaidThemeProducts.forTheme(themeId)]?.formattedPrice

    /** The bundle is offered only before any single theme is bought, so nobody pays twice. */
    val bundleOffer: ProductOffer?
        get() = offers[PaidThemeProducts.BUNDLE]?.takeIf {
            status == StoreStatus.Ready && owned.isEmpty() && pending.isEmpty()
        }

    /** Whole-percent saving of the bundle against buying every theme on its own. */
    val bundleSavingsPercent: Int?
        get() {
            val bundle = bundleOffer ?: return null
            val singles = PaidThemeProducts.themeProducts.map { offers[it] ?: return null }
            if (singles.any { it.currencyCode != bundle.currencyCode }) return null
            val total = singles.sumOf { it.priceMicros }
            if (total <= 0 || bundle.priceMicros >= total) return null
            return (((total - bundle.priceMicros) * 100) / total).toInt().takeIf { it >= 5 }
        }
}

sealed interface StoreEvent {
    data class Purchased(val productId: String) : StoreEvent
    data object Pending : StoreEvent
    data object Restored : StoreEvent
    data object NothingToRestore : StoreEvent
    data object Unavailable : StoreEvent
    data object Failed : StoreEvent
}

/**
 * Google Play Billing for the paid themes. Owns the BillingClient, keeps the entitlement in
 * its own SharedPreferences (not in Settings, so settings backups cannot carry it), restores
 * purchases on start, acknowledges new ones, and never throws into the UI.
 */
class PaidThemeStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
) : PurchasesUpdatedListener {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val buildUnlocks = BuildConfig.UNLOCK_PAID_THEMES

    private val _state = MutableStateFlow(
        PaidThemeStoreState(
            status = if (buildUnlocks) StoreStatus.Unlocked else StoreStatus.Connecting,
            owned = if (buildUnlocks) emptySet() else cachedOwned(),
        )
    )
    val state: StateFlow<PaidThemeStoreState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StoreEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<StoreEvent> = _events.asSharedFlow()

    private var client: BillingClient? = null
    private val details = mutableMapOf<String, ProductDetails>()
    private val connectLock = Mutex()
    private val refreshLock = Mutex()

    /** Called once from Application.onCreate. */
    fun start() {
        if (buildUnlocks) return
        scope.launch { refresh() }
    }

    /** Reload prices and owned purchases. Safe to call often (theme page open, retry). */
    suspend fun refresh(userInitiated: Boolean = false) {
        if (buildUnlocks) return
        refreshLock.withLock {
            val billing = connect()
            if (billing == null) {
                if (userInitiated) {
                    _events.tryEmit(
                        if (_state.value.status == StoreStatus.Unavailable) StoreEvent.Unavailable else StoreEvent.Failed
                    )
                }
                return
            }
            loadProducts(billing)
            val restored = restorePurchases(billing)
            if (userInitiated) {
                _events.tryEmit(
                    when {
                        restored == null -> StoreEvent.Failed
                        restored.isNotEmpty() -> StoreEvent.Restored
                        else -> StoreEvent.NothingToRestore
                    }
                )
            }
        }
    }

    fun retry() {
        scope.launch {
            _state.update { it.copy(status = StoreStatus.Connecting) }
            refresh()
        }
    }

    fun restore() {
        scope.launch { refresh(userInitiated = true) }
    }

    /** Opens the Play purchase sheet for [productId]. */
    fun purchase(activity: Activity, productId: String) {
        if (buildUnlocks || _state.value.purchasing != null) return
        scope.launch {
            runCatching {
                val billing = connect()
                if (billing == null) {
                    _events.tryEmit(StoreEvent.Unavailable)
                    return@launch
                }
                val product = details[productId] ?: run {
                    loadProducts(billing)
                    details[productId]
                }
                if (product == null) {
                    _events.tryEmit(StoreEvent.Unavailable)
                    return@launch
                }
                val offerToken = product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                    ?: product.oneTimePurchaseOfferDetails?.offerToken
                val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(product)
                    .apply { if (offerToken != null) setOfferToken(offerToken) }
                    .build()
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productParams))
                    .build()
                _state.update { it.copy(purchasing = productId) }
                val result = billing.launchBillingFlow(activity, flowParams)
                if (result.responseCode != BillingResponseCode.OK) {
                    handleFlowError(result)
                }
            }.onFailure {
                Log.e(TAG, "purchase($productId) failed", it)
                _state.update { s -> s.copy(purchasing = null) }
                _events.tryEmit(StoreEvent.Failed)
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> scope.launch {
                handlePurchases(purchases.orEmpty(), fromUser = true)
                _state.update { it.copy(purchasing = null) }
            }

            else -> handleFlowError(result)
        }
    }

    private fun handleFlowError(result: BillingResult) {
        Log.i(TAG, "purchase flow: code=${result.responseCode} ${result.debugMessage}")
        _state.update { it.copy(purchasing = null) }
        when (result.responseCode) {
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh(userInitiated = true) }
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingResponseCode.ITEM_UNAVAILABLE -> _events.tryEmit(StoreEvent.Unavailable)

            else -> _events.tryEmit(StoreEvent.Failed)
        }
    }

    private suspend fun connect(): BillingClient? = connectLock.withLock {
        client?.takeIf { it.isReady }?.let { return@withLock it }
        runCatching {
            val billing = client ?: BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build()
                .also { client = it }
            val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    billing.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) cont.resume(result)
                        }

                        override fun onBillingServiceDisconnected() {
                            Log.i(TAG, "billing service disconnected")
                        }
                    })
                }
            }
            when (result?.responseCode) {
                BillingResponseCode.OK -> billing
                BillingResponseCode.BILLING_UNAVAILABLE,
                BillingResponseCode.FEATURE_NOT_SUPPORTED,
                BillingResponseCode.DEVELOPER_ERROR -> {
                    Log.i(TAG, "billing unavailable: ${result.debugMessage}")
                    dropClient()
                    _state.update { it.copy(status = StoreStatus.Unavailable) }
                    null
                }

                else -> {
                    Log.i(TAG, "billing setup failed: code=${result?.responseCode} ${result?.debugMessage}")
                    dropClient()
                    _state.update { it.copy(status = StoreStatus.Offline) }
                    null
                }
            }
        }.getOrElse {
            // No Play Store, or a broken Play services install: stay calm, never crash.
            Log.w(TAG, "billing connect failed", it)
            dropClient()
            _state.update { s -> s.copy(status = StoreStatus.Unavailable) }
            null
        }
    }

    private fun dropClient() {
        runCatching { client?.endConnection() }
        client = null
    }

    private suspend fun loadProducts(billing: BillingClient) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PaidThemeProducts.all.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(ProductType.INAPP)
                        .build()
                }
            )
            .build()
        val result = runCatching { billing.queryProductDetails(params) }.getOrNull()
        val code = result?.billingResult?.responseCode
        if (code != BillingResponseCode.OK) {
            Log.i(TAG, "queryProductDetails: code=$code ${result?.billingResult?.debugMessage}")
            _state.update {
                it.copy(status = if (code == BillingResponseCode.BILLING_UNAVAILABLE) StoreStatus.Unavailable else StoreStatus.Offline)
            }
            return
        }
        val list = result.productDetailsList.orEmpty()
        list.forEach { details[it.productId] = it }
        val offers = list.mapNotNull { pd ->
            val offer = pd.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: pd.oneTimePurchaseOfferDetails
            offer?.let {
                pd.productId to ProductOffer(
                    productId = pd.productId,
                    formattedPrice = it.formattedPrice,
                    priceMicros = it.priceAmountMicros,
                    currencyCode = it.priceCurrencyCode,
                )
            }
        }.toMap()
        // No products means this install cannot buy (products not live, or not the Play version).
        val sellable = PaidThemeProducts.themeProducts.any { it in offers }
        _state.update {
            it.copy(offers = offers, status = if (sellable) StoreStatus.Ready else StoreStatus.Unavailable)
        }
    }

    /** Returns the owned product ids, or null when Play could not answer. */
    private suspend fun restorePurchases(billing: BillingClient): Set<String>? {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        val result = runCatching { billing.queryPurchasesAsync(params) }.getOrNull()
        if (result?.billingResult?.responseCode != BillingResponseCode.OK) {
            Log.i(TAG, "queryPurchases: code=${result?.billingResult?.responseCode}")
            return null
        }
        val owned = handlePurchases(result.purchasesList, fromUser = false)
        enforceEntitlement()
        return owned
    }

    /**
     * Applies purchases from Play. A restore replaces the owned set (refunds drop out); a fresh
     * purchase adds to it. Acknowledges anything not yet acknowledged (Play refunds after 3 days).
     */
    private suspend fun handlePurchases(purchases: List<Purchase>, fromUser: Boolean): Set<String> {
        val known = PaidThemeProducts.all.toSet()
        val purchased = mutableSetOf<String>()
        val pending = mutableSetOf<String>()
        for (purchase in purchases) {
            val products = purchase.products.filter { it in known }
            if (products.isEmpty()) continue
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    purchased += products
                    if (!purchase.isAcknowledged) acknowledge(purchase)
                }

                Purchase.PurchaseState.PENDING -> pending += products
                else -> Unit
            }
        }
        _state.update { s ->
            val owned = if (fromUser) s.owned + purchased else purchased.toSet()
            s.copy(owned = owned, pending = if (fromUser) s.pending - purchased + pending else pending)
        }
        saveOwned(_state.value.owned)
        if (fromUser) {
            purchased.firstOrNull()?.let { _events.tryEmit(StoreEvent.Purchased(it)) }
            if (purchased.isEmpty() && pending.isNotEmpty()) _events.tryEmit(StoreEvent.Pending)
        }
        return purchased
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val billing = client ?: return
        val result = runCatching { billing.acknowledgePurchase(params) }.getOrNull()
        if (result?.responseCode != BillingResponseCode.OK) {
            // Retried on the next refresh.
            Log.w(TAG, "acknowledge failed: code=${result?.responseCode} ${result?.debugMessage}")
        }
    }

    /** After a confirmed restore, fall back to the default theme if the active paid one is no longer owned. */
    private suspend fun enforceEntitlement() {
        val settings = withTimeoutOrNull(5_000) { settingsStore.settingsFlow.first { !it.init } } ?: return
        if (isPaidThemeId(settings.themeId) && !_state.value.isUnlocked(settings.themeId)) {
            Log.i(TAG, "paid theme ${settings.themeId} not owned; reverting to default")
            settingsStore.update { it.copy(themeId = PresetThemes.first().id) }
        }
    }

    private fun cachedOwned(): Set<String> =
        prefs.getStringSet(KEY_OWNED, emptySet()).orEmpty().toSet()

    private fun saveOwned(owned: Set<String>) {
        prefs.edit().putStringSet(KEY_OWNED, owned).apply()
    }
}
