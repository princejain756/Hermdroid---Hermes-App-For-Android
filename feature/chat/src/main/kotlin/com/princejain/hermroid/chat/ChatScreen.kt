package com.princejain.hermroid.chat

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.princejain.hermroid.automation.ActionResult
import com.princejain.hermroid.automation.AndroidAction
import com.princejain.hermroid.automation.AndroidActionExecutor
import com.princejain.hermroid.automation.FileCompressor
import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.HermesSession
import com.princejain.hermroid.network.HermesChatApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatRoute(api: HermesChatApi, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("hermroid_settings", Context.MODE_PRIVATE) }
    var trustedMode by remember { mutableStateOf(preferences.getBoolean("trusted_mode", false)) }
    val scope = rememberCoroutineScope()
    val actionExecutor = remember { AndroidActionExecutor(context.applicationContext) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runCatching { withContext(Dispatchers.IO) { FileCompressor(context).compress(uris) } }
                .onSuccess { zip ->
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, zip)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, "Share compressed files"))
                }
        }
    }
    val execute: (AndroidAction) -> ActionResult = { action ->
        when (action) {
            AndroidAction.CompressFiles -> {
                filePicker.launch(arrayOf("*/*"))
                ActionResult.Completed("Choose files to create a ZIP archive")
            }
            else -> actionExecutor.execute(action).also { result ->
                if (result is ActionResult.RequiresAccessibilityPermission) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }
    val vm: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(api, execute) { trustedMode },
    )
    val state by vm.state.collectAsState()
    ChatScreen(
        state = state,
        onDraft = vm::draft,
        onSend = vm::send,
        onInterrupt = vm::interrupt,
        onSession = vm::openSession,
        onNewSession = vm::newSession,
        onModel = vm::selectModel,
        onApproval = vm::answerApproval,
        onClarification = vm::answerClarification,
        onApproveAndroid = vm::approveAndroidAction,
        onCancelAndroid = vm::cancelAndroidAction,
        trustedMode = trustedMode,
        onTrustedMode = { enabled ->
            trustedMode = enabled
            preferences.edit().putBoolean("trusted_mode", enabled).apply()
        },
        onAccessibilitySettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        onHomeSettings = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
        onDisconnect = onDisconnect,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    onSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onModel: (String) -> Unit,
    onApproval: (String) -> Unit,
    onClarification: (String) -> Unit,
    onApproveAndroid: () -> Unit,
    onCancelAndroid: () -> Unit,
    trustedMode: Boolean,
    onTrustedMode: (Boolean) -> Unit,
    onAccessibilitySettings: () -> Unit,
    onHomeSettings: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showModels by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 760.dp
        if (tablet) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                SessionPane(state, onSession, onNewSession, onDisconnect, trustedMode, onTrustedMode, onAccessibilitySettings, onHomeSettings, Modifier.width(310.dp).fillMaxHeight())
                VerticalDivider()
                Conversation(state, onDraft, onSend, onInterrupt, { showModels = true }, null, Modifier.weight(1f))
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.widthIn(max = 330.dp)) {
                        SessionPane(
                            state,
                            { scope.launch { drawer.close() }; onSession(it) },
                            { scope.launch { drawer.close() }; onNewSession() },
                            onDisconnect,
                            trustedMode,
                            onTrustedMode,
                            onAccessibilitySettings,
                            onHomeSettings,
                            Modifier.fillMaxSize(),
                        )
                    }
                },
            ) {
                Conversation(
                    state,
                    onDraft,
                    onSend,
                    onInterrupt,
                    { showModels = true },
                    { scope.launch { drawer.open() } },
                    Modifier.fillMaxSize().systemBarsPadding(),
                )
            }
        }
    }
    if (showModels) {
        ModelPickerSheet(state.models, state.selectedModelId, onModel) { showModels = false }
    }
    state.approval?.let { ApprovalDialog(it, onApproval) }
    state.clarification?.let { ClarificationDialog(it, onClarification) }
    state.pendingAndroidAction?.let { action ->
        AndroidActionDialog(action, onApproveAndroid, onCancelAndroid)
    }
}

@Composable
private fun AndroidActionDialog(action: AndroidAction, onApprove: () -> Unit, onCancel: () -> Unit) {
    val description = when (action) {
        is AndroidAction.OpenApp -> "Open ${action.appName}"
        is AndroidAction.Global -> "Perform Android navigation: ${action.action.name.lowercase()}"
        is AndroidAction.TapText -> "Tap the control labelled “${action.text}”"
        is AndroidAction.InputText -> "Enter text into the focused field: ${action.text}"
        is AndroidAction.WhatsAppMessage -> "Open a WhatsApp message to ${action.phone}: ${action.message}"
        is AndroidAction.SetLauncherColumns -> "Change the Hermroid home screen to ${action.columns} columns"
        AndroidAction.CompressFiles -> "Choose files and create a ZIP archive"
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Allow Android action?") },
        text = { Text(description) },
        confirmButton = { Button(onClick = onApprove) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun ApprovalDialog(request: ApprovalRequest, onChoice: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice("deny") },
        icon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
        title = { Text("Approval required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(request.description)
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(request.command, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Text("Review the complete command before allowing it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { onChoice("once") }) { Text("Allow once") }
                Row {
                    TextButton(onClick = { onChoice("session") }) { Text("This session") }
                    if (request.allowPermanent) TextButton(onClick = { onChoice("always") }) { Text("Always") }
                }
            }
        },
        dismissButton = { TextButton(onClick = { onChoice("deny") }) { Text("Deny") } },
    )
}

@Composable
private fun ClarificationDialog(request: ClarificationRequest, onAnswer: (String) -> Unit) {
    var answer by remember(request.requestId) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Hermes has a question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.question)
                request.choices.forEach { choice ->
                    OutlinedButton(onClick = { onAnswer(choice) }, modifier = Modifier.fillMaxWidth()) { Text(choice) }
                }
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type another answer") },
                )
            }
        },
        confirmButton = { Button(onClick = { onAnswer(answer.trim()) }, enabled = answer.isNotBlank()) { Text("Answer") } },
    )
}

@Composable
private fun SessionPane(
    state: ChatUiState,
    onSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onDisconnect: () -> Unit,
    trustedMode: Boolean,
    onTrustedMode: (Boolean) -> Unit,
    onAccessibilitySettings: () -> Unit,
    onHomeSettings: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.background(MaterialTheme.colorScheme.surface).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.secondary) {
                Text("H", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.width(10.dp))
            Text("HERMROID", fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNewSession) { Icon(Icons.Rounded.Add, contentDescription = "New session") }
        }
        Spacer(Modifier.height(22.dp))
        Text("Sessions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(state.sessions, key = { it.id }) { session ->
                SessionRow(session, session.id == state.currentSessionId) { onSession(session.id) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trusted mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("Skip action confirmations", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = trustedMode, onCheckedChange = onTrustedMode)
        }
        TextButton(onClick = onAccessibilitySettings) { Text("Enable device control") }
        TextButton(onClick = onHomeSettings) { Text("Set Hermroid as Home app") }
        TextButton(onClick = onDisconnect) { Icon(Icons.Rounded.Close, null); Spacer(Modifier.width(8.dp)); Text("Disconnect server") }
    }
}

@Composable
private fun SessionRow(session: HermesSession, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color).clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (session.preview.isNotBlank()) Text(session.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Conversation(
    state: ChatUiState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    onModels: () -> Unit,
    onMenu: (() -> Unit)?,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }
    Column(modifier.background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.AutoMirrored.Rounded.MenuOpen, "Open sessions") }
            Column(Modifier.weight(1f)) {
                Text(state.sessions.firstOrNull { it.id == state.currentSessionId }?.title ?: "New conversation", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("hermes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onModels) { Icon(Icons.Rounded.Psychology, "Choose model") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        if (state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.messages.isEmpty()) item { EmptyConversation() }
                items(state.messages, key = { it.id }) { MessageCard(it) }
                if (state.thinking.isNotBlank() || state.tools.isNotEmpty()) item { ActivityCard(state) }
                state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        Composer(state, onDraft, onSend, onInterrupt, onModels)
    }
}

@Composable
private fun EmptyConversation() {
    Column(Modifier.fillMaxWidth().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Rounded.Psychology, null, Modifier.padding(18.dp).size(30.dp)) }
        Spacer(Modifier.height(18.dp))
        Text("What are we building?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Ask Hermes to research, code, organize, or operate tools.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageCard(message: ChatMessage) {
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(if (user) .86f else 1f),
            shape = RoundedCornerShape(if (user) 20.dp else 16.dp),
            color = if (user) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.padding(horizontal = if (user) 16.dp else 4.dp, vertical = 12.dp)) {
                if (!user) Text("HERMES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                if (!user) Spacer(Modifier.height(7.dp))
                Text(message.text.ifEmpty { "Thinking…" }, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
                if (message.reasoning.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(message.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(state: ChatUiState) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.thinking.isNotBlank()) Text(state.thinking, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.tools.forEach { tool ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tool.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(tool.summary, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Composer(state: ChatUiState, onDraft: (String) -> Unit, onSend: () -> Unit, onInterrupt: () -> Unit, onModels: () -> Unit) {
    Surface(shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraft,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask anything…") },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                trailingIcon = {
                    FilledIconButton(
                        onClick = if (state.busy) onInterrupt else onSend,
                        enabled = state.busy || state.draft.isNotBlank(),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(if (state.busy) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, if (state.busy) "Stop" else "Send", Modifier.size(19.dp))
                    }
                },
            )
            TextButton(onClick = onModels, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Rounded.Psychology, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(state.models.firstOrNull { it.id == state.selectedModelId }?.label ?: "Choose model", maxLines = 1)
            }
        }
    }
}
