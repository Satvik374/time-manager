package com.kairo.timemanager.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.timemanager.ai.AiTools
import com.kairo.timemanager.ai.ProviderFormat
import com.kairo.timemanager.ai.SystemPrompts
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.KairoChip
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.theme.ThemeMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            SectionHeader("AI provider")
            ProviderCard(state, test, viewModel)

            Spacer(Modifier.height(22.dp))
            SectionHeader("System prompt")
            SystemPromptCard(state, viewModel)

            Spacer(Modifier.height(22.dp))
            SectionHeader("Tools the coach may use")
            ToolsCard(state, viewModel)

            Spacer(Modifier.height(22.dp))
            SectionHeader("Recommendations")
            RecommendationsCard(state, viewModel)

            Spacer(Modifier.height(22.dp))
            SectionHeader("App")
            AppCard(state, viewModel)

            Spacer(Modifier.height(22.dp))
            AboutCard()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    state: SettingsUiState,
    test: ConnectionTest,
    viewModel: SettingsViewModel
) {
    var keyVisible by remember { mutableStateOf(false) }
    val ai = state.ai

    KairoCard {
        SettingSwitch(
            title = "AI coach enabled",
            subtitle = "Turn off to use Kairo as a plain planner.",
            checked = ai.enabled,
            onCheckedChange = { enabled -> viewModel.updateAi { it.copy(enabled = enabled) } }
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Kairo talks to any provider you point it at — a hosted API, a gateway, or a model " +
                "running on your own machine. Nothing is sent anywhere until you fill this in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "Wire format",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProviderFormat.entries.forEach { format ->
                KairoChip(
                    text = format.label,
                    selected = ai.format == format,
                    onClick = { viewModel.updateAi { it.copy(format = format) } }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = ai.baseUrl,
            onValueChange = { value -> viewModel.updateAi { it.copy(baseUrl = value) } },
            label = { Text("Base URL") },
            placeholder = { Text("https://your-provider.example/v1") },
            singleLine = true,
            supportingText = {
                Text("Kairo appends /${ai.format.endpointSuffix} automatically.")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ai.apiKey,
            onValueChange = { value -> viewModel.updateAi { it.copy(apiKey = value) } },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Hide key" else "Show key"
                    )
                }
            },
            supportingText = {
                Text("Stored on this device only. Leave blank for a local endpoint that needs no key.")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ai.model,
            onValueChange = { value -> viewModel.updateAi { it.copy(model = value) } },
            label = { Text("Model") },
            placeholder = { Text("the model id your provider expects") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        SliderRow(
            label = "Creativity",
            value = ai.temperature,
            range = 0f..1.5f,
            display = String.format("%.2f", ai.temperature),
            onChange = { value -> viewModel.updateAi { it.copy(temperature = value) } }
        )
        SliderRow(
            label = "Max reply length",
            value = ai.maxTokens.toFloat(),
            range = 256f..4096f,
            display = "${ai.maxTokens} tokens",
            onChange = { value -> viewModel.updateAi { it.copy(maxTokens = value.roundToInt()) } }
        )
        SliderRow(
            label = "Conversation memory",
            value = ai.historyTurns.toFloat(),
            range = 4f..60f,
            display = "${ai.historyTurns} turns",
            onChange = { value -> viewModel.updateAi { it.copy(historyTurns = value.roundToInt()) } }
        )

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    enabled = ai.isConfigured && test != ConnectionTest.Running,
                    onClick = viewModel::testConnection
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (test == ConnectionTest.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        "Test connection",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        when (test) {
            is ConnectionTest.Success -> TestResult(
                icon = Icons.Filled.CheckCircle,
                color = MaterialTheme.colorScheme.secondary,
                text = "Connected. Provider replied: \"${test.reply}\""
            )
            is ConnectionTest.Failure -> TestResult(
                icon = Icons.Filled.ErrorOutline,
                color = MaterialTheme.colorScheme.error,
                text = test.message
            )
            else -> Unit
        }
    }
}

@Composable
private fun TestResult(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    text: String
) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SystemPromptCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    var draft by remember(state.ai.systemPrompt) { mutableStateOf(state.ai.systemPrompt) }
    val dirty = draft != state.ai.systemPrompt

    KairoCard {
        Text(
            "This is the coach's brief. It ships with a working default; edit it freely. " +
                "Today's date, your streak and a performance summary are appended automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SystemPrompts.presets.forEach { (name, prompt) ->
                KairoChip(
                    text = name,
                    selected = state.ai.systemPrompt.trim() == prompt.trim(),
                    onClick = {
                        draft = prompt
                        viewModel.setSystemPrompt(prompt)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("System prompt") },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KairoChip(
                text = if (dirty) "Save prompt" else "Saved",
                selected = dirty,
                onClick = { if (dirty) viewModel.setSystemPrompt(draft) }
            )
            KairoChip(
                text = "Reset to default",
                onClick = {
                    draft = SystemPrompts.DEFAULT
                    viewModel.resetSystemPrompt()
                }
            )
        }
    }
}

@Composable
private fun ToolsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val ai = state.ai
    KairoCard {
        SettingSwitch(
            title = "Let the coach edit your timetable",
            subtitle = "Off means it can only talk — it can't read or change anything.",
            checked = ai.toolsEnabled,
            onCheckedChange = { enabled -> viewModel.updateAi { it.copy(toolsEnabled = enabled) } }
        )

        if (ai.toolsEnabled) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KairoChip(text = "Enable all", onClick = { viewModel.setAllTools(true) })
                KairoChip(text = "Disable all", onClick = { viewModel.setAllTools(false) })
            }
            Spacer(Modifier.height(6.dp))

            listOf(false, true).forEach { mutating ->
                val group = AiTools.all.filter { it.mutating == mutating }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (mutating) "Can change things" else "Read only",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                group.forEach { tool ->
                    val enabled = ai.isToolEnabled(tool.name)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleTool(tool.name) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (enabled) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                tool.humanLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                tool.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.toggleTool(tool.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val ai = state.ai
    KairoCard {
        SettingSwitch(
            title = "Occasional suggestions",
            subtitle = "The coach posts at most one optional card at a time. You accept or dismiss it.",
            checked = ai.recommendationsEnabled,
            onCheckedChange = { enabled ->
                viewModel.updateAi { it.copy(recommendationsEnabled = enabled) }
            }
        )
        if (ai.recommendationsEnabled) {
            Spacer(Modifier.height(10.dp))
            SliderRow(
                label = "At most one every",
                value = ai.recommendationIntervalHours.toFloat(),
                range = 4f..72f,
                display = "${ai.recommendationIntervalHours}h",
                onChange = { value ->
                    viewModel.updateAi { it.copy(recommendationIntervalHours = value.roundToInt()) }
                }
            )
            Spacer(Modifier.height(4.dp))
            SettingSwitch(
                title = "Notify me about suggestions",
                subtitle = "Off by default — suggestions just wait quietly on the Today tab.",
                checked = ai.proactiveNotifications,
                onCheckedChange = { enabled ->
                    viewModel.updateAi { it.copy(proactiveNotifications = enabled) }
                }
            )
        }
    }
}

@Composable
private fun AppCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    val app = state.app
    KairoCard {
        SettingSwitch(
            title = "Block reminders",
            subtitle = "Notify before a block with a reminder starts.",
            checked = app.remindersEnabled,
            onCheckedChange = { enabled -> viewModel.updateApp { it.copy(remindersEnabled = enabled) } }
        )
        Spacer(Modifier.height(6.dp))
        SettingSwitch(
            title = "24-hour clock",
            subtitle = "Currently showing ${TimeUtils.formatTime(13 * 60 + 30, app.use24HourClock)}.",
            checked = app.use24HourClock,
            onCheckedChange = { enabled -> viewModel.updateApp { it.copy(use24HourClock = enabled) } }
        )
        Spacer(Modifier.height(6.dp))
        SettingSwitch(
            title = "Match system colours",
            subtitle = "Use the wallpaper palette on Android 12 and newer.",
            checked = app.dynamicColor,
            onCheckedChange = { enabled -> viewModel.updateApp { it.copy(dynamicColor = enabled) } }
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to "System",
                ThemeMode.LIGHT to "Light",
                ThemeMode.DARK to "Dark"
            ).forEach { (mode, label) ->
                KairoChip(
                    text = label,
                    selected = app.themeMode == mode,
                    onClick = { viewModel.updateApp { it.copy(themeMode = mode) } }
                )
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Kairo 1.0",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your schedule, stats, streak and conversation live in a database on this device. " +
                    "Only the messages you send to the coach leave it, and only to the provider you " +
                    "configured above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Cross-device sync over the web is planned but not built yet — nothing syncs today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range
        )
    }
}
