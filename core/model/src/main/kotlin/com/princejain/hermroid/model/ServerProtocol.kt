package com.princejain.hermroid.model

enum class ServerProtocol(val isResolved: Boolean, val label: String) {
    AUTO(false, "Auto detect"),
    DESKTOP(true, "Official Desktop"),
    WEB_UI(true, "hermes-webui"),
}

enum class ServerCapability { SESSIONS, STREAMING_CHAT, MODELS, PROFILES, TASKS, SKILLS, MEMORY, ANALYTICS, FILES, GIT, VOICE, APPROVALS, CLARIFICATIONS }

data class ServerIdentity(val protocol: ServerProtocol, val displayName: String, val capabilities: Set<ServerCapability>)
