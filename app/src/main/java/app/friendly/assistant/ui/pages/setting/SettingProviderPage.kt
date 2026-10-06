package app.friendly.assistant.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Tick01
import app.friendly.assistant.R
import app.friendly.assistant.ui.components.ai.ProviderBalanceText
import app.friendly.assistant.ui.components.nav.BackButton
import app.friendly.assistant.ui.components.ui.AutoAIIcon
import app.friendly.assistant.ui.context.LocalToaster
import app.friendly.assistant.ui.hooks.useEditState
import app.friendly.assistant.ui.pages.setting.components.ProviderConfigure
import app.friendly.assistant.ui.pages.setting.components.ProviderConnectionTester
import app.friendly.assistant.ui.pages.setting.components.SettingProviderBalanceOption
import app.friendly.assistant.ui.pages.setting.components.isUsingDefaultBaseUrl
import app.friendly.assistant.ui.pages.setting.components.resetBaseUrlToDefault
import app.friendly.assistant.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
fun SettingProviderPage(
    initialProviderId: Uuid? = null,
    vm: SettingVM = koinViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val providerManager = koinInject<ProviderManager>()
    val scope = rememberCoroutineScope()

    var selectedProviderId by rememberSaveable(initialProviderId) {
        mutableStateOf(initialProviderId?.toString() ?: settings.providers.firstOrNull()?.id?.toString())
    }

    val selectedProvider = settings.providers.find { it.id.toString() == selectedProviderId }
        ?: settings.providers.firstOrNull()

    var currentConfig by remember(selectedProvider?.id) {
        mutableStateOf(selectedProvider)
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider != null && currentConfig?.id != selectedProvider.id) {
            currentConfig = selectedProvider
        }
    }

    val onUpdateSettingsProvider: (ProviderSetting) -> Unit = { updated ->
        currentConfig = updated
        val newProviders = settings.providers.map {
            if (it.id == updated.id) updated else it
        }
        vm.updateSettings(settings.copy(providers = newProviders))
    }

    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showDeleteProviderDialog by remember { mutableStateOf(false) }

    val addModelDialogState = useEditState<Model> { newModel ->
        currentConfig?.let { prov ->
            val updated = prov.addModel(newModel.copy(displayName = newModel.displayName.trim()))
            onUpdateSettingsProvider(updated)
        }
    }

    var isFetchingModels by remember { mutableStateOf(false) }
    var fetchedModels by remember(selectedProvider?.id) { mutableStateOf<List<Model>>(emptyList()) }
    var showModelPickerSheet by remember { mutableStateOf(false) }
    var modelSearchQuery by remember { mutableStateOf("") }

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
                title = {
                    Text(
                        text = stringResource(R.string.setting_page_providers),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = { showAddProviderDialog = true }) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = stringResource(R.string.setting_provider_page_add_provider)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Provider selector chips
            Text(
                text = stringResource(R.string.setting_page_providers),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(settings.providers, key = { it.id }) { provider ->
                    val isSelected = provider.id.toString() == selectedProvider?.id?.toString()
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedProviderId = provider.id.toString()
                            currentConfig = provider
                        },
                        leadingIcon = {
                            AutoAIIcon(name = provider.name, modifier = Modifier.size(18.dp))
                        },
                        label = {
                            Text(provider.name)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showAddProviderDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(HugeIcons.Add01, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add))
                    }
                }
            }

            if (currentConfig != null) {
                val activeProvider = currentConfig!!

                // Provider Configuration Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CustomColors.listItemColors.containerColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AutoAIIcon(activeProvider.name, modifier = Modifier.size(28.dp))
                            OutlinedTextField(
                                value = activeProvider.name,
                                onValueChange = { newName ->
                                    val updated = activeProvider.copyProvider(name = newName)
                                    currentConfig = updated
                                    onUpdateSettingsProvider(updated)
                                },
                                label = { Text(stringResource(R.string.setting_provider_page_name)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (activeProvider.enabled) {
                                        stringResource(R.string.setting_provider_page_enabled)
                                    } else {
                                        stringResource(R.string.setting_provider_page_disabled)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Switch(
                                    checked = activeProvider.enabled,
                                    onCheckedChange = { isEnabled ->
                                        val updated = activeProvider.copyProvider(enabled = isEnabled)
                                        onUpdateSettingsProvider(updated)
                                    }
                                )
                            }
                        }

                        ProviderConfigure(
                            provider = activeProvider,
                            onEdit = { updated ->
                                currentConfig = updated
                            }
                        )

                        if (activeProvider is ProviderSetting.OpenAI) {
                            SettingProviderBalanceOption(
                                provider = activeProvider,
                                balanceOption = activeProvider.balanceOption,
                                onEdit = {
                                    val updated = activeProvider.copyProvider(balanceOption = it)
                                    currentConfig = updated
                                }
                            )
                            ProviderBalanceText(
                                providerSetting = activeProvider,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        // Action Row: Test, Fetch, Add Model, Reset URL, Delete, Save
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProviderConnectionTester(internalProvider = activeProvider)

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isFetchingModels = true
                                        try {
                                            val list = providerManager.getProviderByType(activeProvider)
                                                .listModels(activeProvider)
                                                .sortedBy { it.modelId }
                                                .toList()
                                            fetchedModels = list
                                            if (list.isEmpty()) {
                                                toaster.show("No models found from provider", type = ToastType.Info)
                                            } else {
                                                showModelPickerSheet = true
                                            }
                                        } catch (e: Exception) {
                                            toaster.show("Fetch failed: ${e.message}", type = ToastType.Error)
                                        } finally {
                                            isFetchingModels = false
                                        }
                                    }
                                }
                            ) {
                                if (isFetchingModels) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(HugeIcons.Refresh03, contentDescription = "Fetch Models")
                                }
                            }

                            IconButton(
                                onClick = {
                                    addModelDialogState.open(Model())
                                }
                            ) {
                                Icon(
                                    HugeIcons.Add01,
                                    contentDescription = stringResource(R.string.setting_provider_page_add_model)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val reset = activeProvider.resetBaseUrlToDefault()
                                    currentConfig = reset
                                    onUpdateSettingsProvider(reset)
                                },
                                enabled = !activeProvider.isUsingDefaultBaseUrl()
                            ) {
                                Icon(
                                    imageVector = HugeIcons.Refresh03,
                                    contentDescription = stringResource(R.string.setting_model_page_reset_to_default)
                                )
                            }

                            if (!activeProvider.builtIn) {
                                IconButton(onClick = { showDeleteProviderDialog = true }) {
                                    Icon(HugeIcons.Delete01, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Button(
                                onClick = {
                                    val providerToSave = activeProvider.copyProvider(name = activeProvider.name.trim())
                                    onUpdateSettingsProvider(providerToSave)
                                    toaster.show(
                                        context.getString(R.string.setting_provider_page_save_success),
                                        type = ToastType.Success
                                    )
                                }
                            ) {
                                Icon(HugeIcons.Tick01, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.setting_provider_page_save))
                            }
                        }
                    }
                }

                // Models Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${stringResource(R.string.setting_provider_page_models)} (${activeProvider.models.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = { addModelDialogState.open(Model()) }
                    ) {
                        Icon(HugeIcons.Add01, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.setting_provider_page_add_model))
                    }
                }

                if (activeProvider.models.size > 4) {
                    OutlinedTextField(
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val displayedModels = remember(activeProvider.models, modelSearchQuery) {
                    if (modelSearchQuery.isBlank()) activeProvider.models
                    else activeProvider.models.filter {
                        it.displayName.contains(modelSearchQuery, ignoreCase = true) ||
                            it.modelId.contains(modelSearchQuery, ignoreCase = true)
                    }
                }

                if (displayedModels.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.setting_provider_page_no_models),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.setting_provider_page_add_models_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayedModels.forEach { modelItem ->
                            ModelCard(
                                model = modelItem,
                                onDelete = {
                                    val updated = activeProvider.delModel(modelItem)
                                    onUpdateSettingsProvider(updated)
                                },
                                onEdit = { editedModel ->
                                    val updated = activeProvider.editModel(editedModel)
                                    onUpdateSettingsProvider(updated)
                                },
                                parentProvider = activeProvider
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Model Bottom Sheet
    if (addModelDialogState.isEditing) {
        addModelDialogState.currentState?.let { modelState ->
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            ModalBottomSheet(
                onDismissRequest = { addModelDialogState.dismiss() },
                sheetState = sheetState,
                sheetGesturesEnabled = false
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_add_model),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        ModelSettingsForm(
                            model = modelState,
                            onModelChange = { addModelDialogState.currentState = it },
                            isEdit = false,
                            parentProvider = currentConfig
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { addModelDialogState.dismiss() }) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(
                            onClick = {
                                if (modelState.modelId.isNotBlank() && modelState.displayName.isNotBlank()) {
                                    addModelDialogState.confirm()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.setting_provider_page_add))
                        }
                    }
                }
            }
        }
    }

    // Fetched Model Picker Sheet
    if (showModelPickerSheet && currentConfig != null) {
        val activeProvider = currentConfig!!
        ModalBottomSheet(
            onDismissRequest = { showModelPickerSheet = false }
        ) {
            var filterText by remember { mutableStateOf("") }
            val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
            val filteredModels = fetchedModels.filter {
                if (filterKeywords.isEmpty()) true
                else filterKeywords.all { kw ->
                    it.modelId.contains(kw, ignoreCase = true) || it.displayName.contains(kw, ignoreCase = true)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_avaliable_models),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val unselectedCount = filteredModels.count { m ->
                        activeProvider.models.none { it.modelId == m.modelId }
                    }
                    TextButton(
                        onClick = {
                            if (unselectedCount > 0) {
                                val updated = activeProvider.copyProvider(
                                    models = activeProvider.models + filteredModels.filter { m ->
                                        activeProvider.models.none { it.modelId == m.modelId }
                                    }.map { m ->
                                        m.copy(
                                            inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(m.modelId),
                                            outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(m.modelId),
                                            abilities = ModelRegistry.MODEL_ABILITIES.getData(m.modelId).ifEmpty { listOf(ModelAbility.TOOL) }
                                        )
                                    }
                                )
                                onUpdateSettingsProvider(updated)
                            } else {
                                val updated = activeProvider.copyProvider(
                                    models = activeProvider.models.filter { m ->
                                        filteredModels.none { it.modelId == m.modelId }
                                    }
                                )
                                onUpdateSettingsProvider(updated)
                            }
                        }
                    ) {
                        Text(
                            if (unselectedCount > 0) stringResource(R.string.setting_provider_page_select_all, unselectedCount)
                            else stringResource(R.string.setting_provider_page_deselect_models)
                        )
                    }
                }

                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels) { item ->
                        val isAdded = activeProvider.models.any { it.modelId == item.modelId }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                AutoAIIcon(item.modelId, Modifier.size(32.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.modelId, style = MaterialTheme.typography.titleSmall)
                                }
                                IconButton(
                                    onClick = {
                                        if (isAdded) {
                                            val toDel = activeProvider.models.firstOrNull { it.modelId == item.modelId } ?: item
                                            val updated = activeProvider.delModel(toDel)
                                            onUpdateSettingsProvider(updated)
                                        } else {
                                            val updated = activeProvider.addModel(
                                                item.copy(
                                                    inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(item.modelId),
                                                    outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(item.modelId),
                                                    abilities = ModelRegistry.MODEL_ABILITIES.getData(item.modelId).ifEmpty { listOf(ModelAbility.TOOL) }
                                                )
                                            )
                                            onUpdateSettingsProvider(updated)
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isAdded) HugeIcons.Cancel01 else HugeIcons.Add01,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Provider Dialog
    if (showAddProviderDialog) {
        var newProvider by remember { mutableStateOf<ProviderSetting>(ProviderSetting.OpenAI(name = "Custom Provider")) }
        AlertDialog(
            onDismissRequest = { showAddProviderDialog = false },
            title = { Text(stringResource(R.string.setting_provider_page_add_provider)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newProvider.name,
                        onValueChange = { newProvider = newProvider.copyProvider(name = it) },
                        label = { Text(stringResource(R.string.setting_provider_page_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ProviderConfigure(
                        provider = newProvider,
                        onEdit = { newProvider = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toAdd = newProvider.copyProvider(name = newProvider.name.trim())
                        val newSettings = settings.copy(providers = settings.providers + toAdd)
                        vm.updateSettings(newSettings)
                        selectedProviderId = toAdd.id.toString()
                        currentConfig = toAdd
                        showAddProviderDialog = false
                    }
                ) {
                    Text(stringResource(R.string.setting_provider_page_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddProviderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete Provider Dialog
    if (showDeleteProviderDialog && currentConfig != null) {
        val target = currentConfig!!
        AlertDialog(
            onDismissRequest = { showDeleteProviderDialog = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.setting_provider_page_delete_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newSettings = settings.copy(providers = settings.providers - target)
                        vm.updateSettings(newSettings)
                        selectedProviderId = newSettings.providers.firstOrNull()?.id?.toString()
                        currentConfig = newSettings.providers.firstOrNull()
                        showDeleteProviderDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProviderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
