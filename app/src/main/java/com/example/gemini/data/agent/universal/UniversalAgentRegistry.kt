package com.example.gemini.data.agent.universal

/** Local registry; intentionally has no cloud service or external controller dependency. */
object UniversalAgentRegistry {
    val builtIns: List<AgentManifest> = listOf(
        AgentManifest(
            id = "antigravity",
            displayName = "Antigravity",
            runtime = AgentRuntime.LOCAL_DAEMON,
            installStrategy = InstallStrategy.USER_PROVIDED,
            authMethods = setOf(AuthMethod.LOCAL_SESSION, AuthMethod.BROWSER_OAUTH),
            requiredTools = setOf("agy-ide-bridge", "agy-hub"),
            optionalTools = setOf("mcp", "skills", "git", "android-sdk", "gradle"),
            capabilities = setOf(AgentCapability.CHAT, AgentCapability.STREAMING, AgentCapability.FILE_EDIT,
                AgentCapability.TERMINAL, AgentCapability.MCP, AgentCapability.SKILLS, AgentCapability.PLAN_MODE,
                AgentCapability.APPROVALS),
            configurationScope = ConfigurationScope.MIXED,
            notes = "Uses the existing native gRPC hub integration."
        ),
        AgentManifest(
            id = "claude-code",
            displayName = "Claude Code",
            runtime = AgentRuntime.LINUX_CLI,
            installStrategy = InstallStrategy.OFFICIAL_INSTALLER,
            authMethods = setOf(AuthMethod.BROWSER_OAUTH, AuthMethod.API_KEY),
            requiredTools = setOf("claude-cli", "node-runtime", "glibc-runtime"),
            optionalTools = setOf("mcp", "skills", "git", "android-sdk", "gradle"),
            capabilities = setOf(AgentCapability.CHAT, AgentCapability.STREAMING, AgentCapability.FILE_EDIT,
                AgentCapability.TERMINAL, AgentCapability.MCP, AgentCapability.SKILLS, AgentCapability.PLAN_MODE,
                AgentCapability.APPROVALS, AgentCapability.SESSION_RESUME),
            configurationScope = ConfigurationScope.MIXED,
            notes = "Uses the existing official CLI stream-json bridge integration."
        ),
        AgentManifest(
            id = "openai-codex-cli",
            displayName = "OpenAI Codex CLI",
            runtime = AgentRuntime.LINUX_CLI,
            installStrategy = InstallStrategy.OFFICIAL_INSTALLER,
            authMethods = setOf(AuthMethod.DEVICE_OAUTH, AuthMethod.API_KEY),
            requiredTools = setOf("codex-cli", "node-runtime"),
            optionalTools = setOf("git", "android-sdk", "gradle", "mcp", "skills"),
            capabilities = setOf(AgentCapability.TERMINAL, AgentCapability.FILE_EDIT, AgentCapability.PLAN_MODE),
            configurationScope = ConfigurationScope.AGENT_PRIVATE,
            adapterStatus = AdapterStatus.PLANNED,
            notes = "Registry-ready only: a native chat adapter, session/auth UI and tested Android ARM64 runtime are still required before enabling it in the chat selector."
        )
    )

    fun find(id: String): AgentManifest? = builtIns.firstOrNull { it.id.equals(id, ignoreCase = true) }
    fun readyForChat(): List<AgentManifest> = builtIns.filter {
        it.adapterStatus == AdapterStatus.AVAILABLE && AgentCapability.CHAT in it.capabilities
    }
}
