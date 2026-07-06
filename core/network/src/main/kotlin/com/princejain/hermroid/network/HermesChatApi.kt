package com.princejain.hermroid.network

import com.princejain.hermroid.model.HermesModel
import com.princejain.hermroid.model.HermesSession
import kotlinx.coroutines.flow.SharedFlow

interface HermesChatApi {
    val events: SharedFlow<DesktopEvent>
    suspend fun listSessions(): List<HermesSession>
    suspend fun createSession(columns: Int = 80): String
    suspend fun resumeSession(sessionId: String, columns: Int = 80): SessionSnapshot
    suspend fun models(sessionId: String): List<HermesModel>
    suspend fun selectModel(sessionId: String, modelId: String): String
    suspend fun submit(sessionId: String, text: String): Boolean
    suspend fun interrupt(sessionId: String): Boolean
    suspend fun respondToApproval(sessionId: String, choice: String, requestId: String? = null): Boolean
    suspend fun respondToClarification(sessionId: String, requestId: String, answer: String): Boolean
    fun disconnect()
}
