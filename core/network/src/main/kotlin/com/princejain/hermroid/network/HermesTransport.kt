package com.princejain.hermroid.network
import com.princejain.hermroid.model.ServerIdentity
interface HermesTransport { val identity: ServerIdentity; suspend fun verifyConnection(): ConnectionVerification; suspend fun close() }
sealed interface ConnectionVerification { data object Connected: ConnectionVerification; data class PasswordRequired(val providers: List<AuthProvider>): ConnectionVerification }
data class AuthProvider(val name:String,val displayName:String,val supportsPassword:Boolean)
