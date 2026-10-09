package app.friendly.assistant.di

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.serialization.json.Json
import me.rerere.ai.provider.ProviderManager
import me.rerere.common.http.AcceptLanguageBuilder
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.data.ai.AIRequestInterceptor
import app.friendly.assistant.data.ai.LogRedactor
import app.friendly.assistant.data.ai.RequestLoggingInterceptor
import app.friendly.assistant.data.ai.transformers.AssistantTemplateLoader
import app.friendly.assistant.data.ai.GenerationLoop
import app.friendly.assistant.data.ai.transformers.TemplateTransformer
import app.friendly.assistant.data.api.FriendlyAPI
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.sync.BackupManager
import app.friendly.assistant.data.db.AppDatabaseFactory
import app.friendly.assistant.data.db.AppDatabase
import app.friendly.assistant.data.db.fts.MessageFtsManager
import app.friendly.assistant.data.ai.mcp.McpManager
import app.friendly.assistant.data.network.SettingsProxySelector
import app.friendly.assistant.data.network.SettingsProxyAuthenticator
import app.friendly.assistant.data.network.SettingsSocks5Authenticator
import app.friendly.assistant.data.sync.webdav.WebDavSync
import me.rerere.search.SearchService
import app.friendly.assistant.data.sync.S3Sync
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

val dataSourceModule = module {
    single {
        SettingsStore(context = get(), scope = get())
    }

    single {
        val context: Context = get()
        AppDatabaseFactory.create(context)
    }

    single {
        AssistantTemplateLoader(settingsStore = get())
    }

    single {
        PebbleEngine.Builder()
            .loader(get<AssistantTemplateLoader>())
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()
    }

    single { TemplateTransformer(engine = get(), settingsStore = get()) }

    single {
        get<AppDatabase>().conversationDao()
    }

    single {
        get<AppDatabase>().memoryDao()
    }

    single {
        get<AppDatabase>().genMediaDao()
    }

    single {
        get<AppDatabase>().messageNodeDao()
    }

    single {
        get<AppDatabase>().managedFileDao()
    }

    single {
        get<AppDatabase>().favoriteDao()
    }

    single {
        get<AppDatabase>().workspaceDao()
    }

    single {
        get<AppDatabase>().folderDao()
    }

    single {
        MessageFtsManager(get())
    }

    single { McpManager(settingsStore = get(), appScope = get(), filesManager = get()) }

    single {
        GenerationLoop(
            context = get(),
            providerManager = get(),
            json = get(),
        )
    }

    single<OkHttpClient> {
        val settingsStore: SettingsStore = get()
        val acceptLang = AcceptLanguageBuilder.fromAndroid(get())
            .build()
        java.net.Authenticator.setDefault(SettingsSocks5Authenticator(settingsStore))
        val initialNetworkSetting = settingsStore.settingsFlow.value.networkSetting
        val appliedProxySetting = AtomicReference(
            Triple(
                initialNetworkSetting.proxyUrl,
                initialNetworkSetting.proxyUsername,
                initialNetworkSetting.proxyPassword,
            )
        )
        lateinit var client: OkHttpClient
        client = OkHttpClient.Builder()
            .proxySelector(SettingsProxySelector(settingsStore))
            .proxyAuthenticator(SettingsProxyAuthenticator(settingsStore))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val networkSetting = settingsStore.settingsFlow.value.networkSetting
                val currentProxySetting = Triple(
                    networkSetting.proxyUrl,
                    networkSetting.proxyUsername,
                    networkSetting.proxyPassword,
                )
                if (appliedProxySetting.getAndSet(currentProxySetting) != currentProxySetting) {
                    client.connectionPool.evictAll()
                }

                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    val userAgent = settingsStore.settingsFlow.value.networkSetting.userAgent
                        .trim()
                        .ifEmpty { "Friendly-Android/${BuildConfig.VERSION_NAME}" }
                    requestBuilder.addHeader(HttpHeaders.UserAgent, userAgent)
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                    contentTypeHeader.contains(";") &&
                    contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(AIRequestInterceptor())
            .addInterceptor(LogRedactor.httpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
        client.also { SearchService.init(it, get()) }
    }


    single {
        ProviderManager(client = get(), context = get())
    }

    single { BackupManager(context = get(), database = get(), settingsStore = get(), json = get()) }

    single {
        WebDavSync(
            backupManager = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(20, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.MINUTES)
                    writeTimeout(120, TimeUnit.SECONDS)
                    followSslRedirects(true)
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                }
            }
        }
    }

    single {
        S3Sync(
            backupManager = get(),
            context = get(),
            httpClient = get()
        )
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl("https://api.rikka-ai.com")
            .addConverterFactory(get<Json>().asConverterFactory("application/json; charset=UTF8".toMediaType()))
            .build()
    }

    single<FriendlyAPI> {
        get<Retrofit>().create(FriendlyAPI::class.java)
    }
}
