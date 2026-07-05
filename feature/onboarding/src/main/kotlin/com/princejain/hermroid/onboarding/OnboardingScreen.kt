package com.princejain.hermroid.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.princejain.hermroid.design.R as DesignR
import com.princejain.hermroid.model.ServerAddress
import com.princejain.hermroid.model.ServerProtocol
import com.princejain.hermroid.network.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingState(
    val url: String = "",
    val protocolChoice: ServerProtocol = ServerProtocol.AUTO,
    val detectedProtocol: ServerProtocol? = null,
    val providers: List<AuthProvider> = emptyList(),
    val provider: String = "",
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val loading: Boolean = false,
    val needsPassword: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel : ViewModel() {
    private val mutable = MutableStateFlow(OnboardingState())
    val state = mutable.asStateFlow()
    private var desktopRpc: DesktopJsonRpcClient? = null

    fun url(value: String) = update { copy(url = value, detectedProtocol = null, error = null) }
    fun protocol(value: ServerProtocol) = update { copy(protocolChoice = value, detectedProtocol = null, needsPassword = false, error = null) }
    fun username(value: String) = update { copy(username = value, error = null) }
    fun password(value: String) = update { copy(password = value, error = null) }
    fun provider(value: String) = update { copy(provider = value, error = null) }
    fun toggle() = update { copy(showPassword = !showPassword) }

    fun connect() {
        if (mutable.value.loading) return
        viewModelScope.launch {
            update { copy(loading = true, error = null) }
            try {
                val address = ServerAddress.parse(mutable.value.url)
                val resolved = when (val selected = mutable.value.protocolChoice) {
                    ServerProtocol.DESKTOP, ServerProtocol.WEB_UI -> selected
                    ServerProtocol.AUTO -> when (val result = ProtocolDetector().detect(address)) {
                        is DetectionResult.Detected -> result.protocol
                        is DetectionResult.Unreachable -> error(result.message)
                        DetectionResult.Ambiguous -> error("Could not identify this server. Choose Official Desktop or hermes-webui below.")
                    }
                }
                update { copy(detectedProtocol = resolved) }
                when (resolved) {
                    ServerProtocol.DESKTOP -> connectDesktop(address)
                    ServerProtocol.WEB_UI -> connectWebUi(address)
                    ServerProtocol.AUTO -> error("Choose a server protocol")
                }
            } catch (e: Exception) {
                update { copy(loading = false, error = e.message ?: "Connection failed") }
            }
        }
    }

    private suspend fun connectDesktop(address: ServerAddress) {
        val api = DesktopAuthApi(address)
        val providers = api.providers()
        val passwordProviders = providers.filter { it.supportsPassword }
        if (passwordProviders.isEmpty()) error("This Desktop server only offers browser sign-in. Configure a password provider for mobile access.")
        val selected = mutable.value.provider.ifBlank { passwordProviders.first().name }
        if (mutable.value.password.isBlank()) {
            update { copy(loading = false, needsPassword = true, providers = passwordProviders, provider = selected) }
            return
        }
        api.login(selected, mutable.value.username, mutable.value.password)
        val ticket = api.ticket()
        desktopRpc?.disconnect()
        desktopRpc = DesktopJsonRpcClient(address).also { it.connect(ticket.ticket) }
        update { copy(loading = false, connected = true, needsPassword = false, password = "") }
    }

    private suspend fun connectWebUi(address: ServerAddress) {
        val api = AuthApi(address)
        require(api.health().status == "ok") { "Unexpected health response" }
        val auth = api.authStatus()
        if (auth.authEnabled == true) {
            if (auth.passwordAuthEnabled == false) error("This server uses passkeys, which Hermroid does not support yet")
            if (mutable.value.password.isBlank()) {
                update { copy(loading = false, needsPassword = true) }
                return
            }
            require(api.login(mutable.value.password).ok == true) { "Incorrect password" }
        }
        update { copy(loading = false, connected = true, needsPassword = false, password = "") }
    }

    private fun update(block: OnboardingState.() -> OnboardingState) { mutable.value = mutable.value.block() }
    override fun onCleared() { desktopRpc?.disconnect() }
}

@Composable fun OnboardingRoute(vm: OnboardingViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    OnboardingScreen(state, vm::url, vm::protocol, vm::provider, vm::username, vm::password, vm::toggle, vm::connect)
}

@Composable private fun OnboardingScreen(
    state: OnboardingState,
    onUrl: (String) -> Unit,
    onProtocol: (ServerProtocol) -> Unit,
    onProvider: (String) -> Unit,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            val wide = maxWidth > 700.dp
            Row(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center) {
                if (wide) Hero(Modifier.width(380.dp).align(Alignment.CenterVertically).padding(end = 56.dp))
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).align(Alignment.CenterVertically).padding(vertical = 32.dp)) {
                    if (!wide) Row(verticalAlignment = Alignment.CenterVertically) { HermesPortrait(66.dp); Spacer(Modifier.width(14.dp)); Wordmark() }
                    Spacer(Modifier.height(if (wide) 0.dp else 38.dp))
                    if (state.connected) Connected(state.url, state.detectedProtocol) else {
                        Text("Connect to Hermes", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Official Desktop and hermes-webui are supported.", color = MaterialTheme.colorScheme.onSurface.copy(.6f))
                        Spacer(Modifier.height(24.dp))
                        ConnectionCard(state, onUrl, onProtocol, onProvider, onUsername, onPassword, onToggle, onConnect)
                        Spacer(Modifier.height(18.dp))
                        Text("Credentials stay on this device. HTTPS is required except for localhost and Tailscale.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(.5f), lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun ConnectionCard(state: OnboardingState, onUrl:(String)->Unit, onProtocol:(ServerProtocol)->Unit, onProvider:(String)->Unit, onUsername:(String)->Unit, onPassword:(String)->Unit, onToggle:()->Unit, onConnect:()->Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(state.url, onUrl, Modifier.fillMaxWidth(), label = { Text("Server URL") }, placeholder = { Text("https://hermes.example.com") }, singleLine = true, enabled = !state.loading)
            Spacer(Modifier.height(14.dp))
            Text("Protocol", style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ServerProtocol.entries.forEach { protocol -> FilterChip(selected = state.protocolChoice == protocol, onClick = { onProtocol(protocol) }, label = { Text(protocol.label, maxLines = 1) }, enabled = !state.loading) }
            }
            state.detectedProtocol?.let { Spacer(Modifier.height(10.dp)); SuggestionChip(onClick = {}, label = { Text("Verified: ${it.label}") }) }
            if (state.needsPassword && state.detectedProtocol == ServerProtocol.DESKTOP) {
                Spacer(Modifier.height(14.dp))
                if (state.providers.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.providers.forEach { p -> FilterChip(selected = state.provider == p.name, onClick = { onProvider(p.name) }, label = { Text(p.displayName) }) } }
                OutlinedTextField(state.username, onUsername, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, enabled = !state.loading)
            }
            if (state.needsPassword || state.password.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(state.password, onPassword, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { TextButton(onClick = onToggle) { Text(if (state.showPassword) "Hide" else "Show") } }, enabled = !state.loading)
            }
            state.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onConnect, Modifier.fillMaxWidth().height(52.dp), enabled = state.url.isNotBlank() && !state.loading, shape = RoundedCornerShape(15.dp)) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(if (state.needsPassword) "Sign in" else "Detect and connect") }
        }
    }
}

@Composable private fun Hero(modifier:Modifier){ Column(modifier){ HermesPortrait(156.dp); Spacer(Modifier.height(22.dp)); Wordmark(); Spacer(Modifier.height(24.dp)); Text("Your Hermes agent, wherever you are.",fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(14.dp)); Text("Private by design. Hermroid talks directly to the server you control.",color=MaterialTheme.colorScheme.onSurface.copy(.62f),fontSize=17.sp,lineHeight=25.sp) } }
@Composable private fun Wordmark(){ Row(verticalAlignment=Alignment.CenterVertically){ Surface(shape=RoundedCornerShape(9.dp),color=MaterialTheme.colorScheme.secondary){ Text("H",Modifier.padding(horizontal=9.dp,vertical=5.dp),fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.onPrimary) }; Spacer(Modifier.width(9.dp)); Text("HERMROID",fontSize=18.sp,fontWeight=FontWeight.Black,letterSpacing=1.2.sp) } }
@Composable private fun HermesPortrait(size: androidx.compose.ui.unit.Dp){ Image(painterResource(DesignR.drawable.hermes_logo),contentDescription="Hermroid assistant portrait",contentScale=ContentScale.Crop,modifier=Modifier.size(size).clip(CircleShape).border(1.dp,MaterialTheme.colorScheme.outline.copy(.25f),CircleShape)) }
@Composable private fun Connected(url:String,protocol:ServerProtocol?){ Wordmark(); Spacer(Modifier.height(38.dp)); Text("Connected",fontSize=32.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(url,color=MaterialTheme.colorScheme.onSurface.copy(.6f)); protocol?.let{Spacer(Modifier.height(10.dp));SuggestionChip(onClick={},label={Text(it.label)})}; Spacer(Modifier.height(24.dp)); Card(shape=RoundedCornerShape(18.dp)){ Text("Server connection verified. Sessions and streaming chat are the next development milestone.",Modifier.padding(20.dp),lineHeight=22.sp) } }
