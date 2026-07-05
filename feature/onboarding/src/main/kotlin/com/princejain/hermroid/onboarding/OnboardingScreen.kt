package com.princejain.hermroid.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.princejain.hermroid.model.ServerAddress
import com.princejain.hermroid.network.AuthApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingState(val url:String="", val password:String="", val showPassword:Boolean=false, val loading:Boolean=false, val needsPassword:Boolean=false, val connected:Boolean=false, val error:String?=null)
class OnboardingViewModel : ViewModel() {
    private val mutable = MutableStateFlow(OnboardingState()); val state = mutable.asStateFlow()
    fun url(value:String){ mutable.value=mutable.value.copy(url=value,error=null) }
    fun password(value:String){ mutable.value=mutable.value.copy(password=value,error=null) }
    fun toggle(){ mutable.value=mutable.value.copy(showPassword=!mutable.value.showPassword) }
    fun connect(){ if(mutable.value.loading)return; viewModelScope.launch { mutable.value=mutable.value.copy(loading=true,error=null); try { val api=AuthApi(ServerAddress.parse(mutable.value.url)); require(api.health().status=="ok"){"Unexpected health response"}; val auth=api.authStatus(); if(auth.authEnabled==true){ if(auth.passwordAuthEnabled==false) error("This server uses passkeys, which Hermroid does not support yet"); if(mutable.value.password.isBlank()){ mutable.value=mutable.value.copy(loading=false,needsPassword=true); return@launch }; require(api.login(mutable.value.password).ok==true){"Incorrect password"} }; mutable.value=mutable.value.copy(loading=false,connected=true) }catch(e:Exception){ mutable.value=mutable.value.copy(loading=false,error=e.message?:"Connection failed") } } }
}

@Composable fun OnboardingRoute(vm:OnboardingViewModel=viewModel()) { val state by vm.state.collectAsState(); OnboardingScreen(state,vm::url,vm::password,vm::toggle,vm::connect) }
@Composable private fun OnboardingScreen(state:OnboardingState,onUrl:(String)->Unit,onPassword:(String)->Unit,onToggle:()->Unit,onConnect:()->Unit){
    Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background){ BoxWithConstraints { val wide=maxWidth>700.dp; Row(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal=24.dp), horizontalArrangement=Arrangement.Center){
        if(wide) Column(Modifier.width(380.dp).align(Alignment.CenterVertically).padding(end=56.dp)){ Wordmark(); Spacer(Modifier.height(24.dp)); Text("Your Hermes agent, wherever you are.",fontSize=35.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(14.dp)); Text("Private by design. Hermroid talks directly to the server you control.",color=MaterialTheme.colorScheme.onSurface.copy(.62f),fontSize=17.sp,lineHeight=25.sp) }
        Column(Modifier.widthIn(max=520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).align(Alignment.CenterVertically).padding(vertical=32.dp)){ if(!wide) Wordmark(); Spacer(Modifier.height(if(wide) 0.dp else 38.dp)); if(state.connected) Connected(state.url) else { Text("Connect to Hermes",fontSize=28.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Enter the address of your hermes-webui server.",color=MaterialTheme.colorScheme.onSurface.copy(.6f)); Spacer(Modifier.height(28.dp)); Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){ Column(Modifier.padding(20.dp)){ OutlinedTextField(state.url,onUrl,Modifier.fillMaxWidth(),label={Text("Server URL")},placeholder={Text("https://hermes.example.com")},singleLine=true,enabled=!state.loading); if(state.needsPassword||state.password.isNotEmpty()){ Spacer(Modifier.height(14.dp)); OutlinedTextField(state.password,onPassword,Modifier.fillMaxWidth(),label={Text("Password")},singleLine=true,visualTransformation=if(state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={TextButton(onClick=onToggle){Text(if(state.showPassword)"Hide" else "Show")}},enabled=!state.loading) }; state.error?.let{ Spacer(Modifier.height(12.dp)); Text(it,color=MaterialTheme.colorScheme.error) }; Spacer(Modifier.height(20.dp)); Button(onClick=onConnect,Modifier.fillMaxWidth().height(52.dp),enabled=state.url.isNotBlank()&&!state.loading,shape=RoundedCornerShape(15.dp)){ if(state.loading) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary) else Text(if(state.needsPassword)"Sign in" else "Test and connect") } } }; Spacer(Modifier.height(18.dp)); Text("Credentials stay on this device. HTTPS is required except for localhost and Tailscale.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(.5f),lineHeight=18.sp) } }
    } } } }
@Composable private fun Wordmark(){ Row(verticalAlignment=Alignment.CenterVertically){ Surface(shape=RoundedCornerShape(9.dp),color=MaterialTheme.colorScheme.secondary){ Text("H",Modifier.padding(horizontal=9.dp,vertical=5.dp),fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.onPrimary) }; Spacer(Modifier.width(9.dp)); Text("HERMROID",fontSize=18.sp,fontWeight=FontWeight.Black,letterSpacing=1.2.sp) } }
@Composable private fun Connected(url:String){ Wordmark(); Spacer(Modifier.height(38.dp)); Text("Connected",fontSize=32.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(url,color=MaterialTheme.colorScheme.onSurface.copy(.6f)); Spacer(Modifier.height(24.dp)); Card(shape=RoundedCornerShape(18.dp)){ Text("Server connection verified. Sessions and streaming chat are the next development milestone.",Modifier.padding(20.dp),lineHeight=22.sp) } }
