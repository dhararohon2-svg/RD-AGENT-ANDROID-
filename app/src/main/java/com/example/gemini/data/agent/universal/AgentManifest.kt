package com.example.gemini.data.agent.universal

/**
 * Provider-neutral description of a coding agent. Agent-specific adapters may consume this
 * manifest without making the rest of the app depend on a vendor's CLI or configuration format.
 */
data class AgentManifest(
    val id: String,
    val displayName: String,
    val version: String = "managed-by-agent",
    val runtime: AgentRuntime,
    val installStrategy: InstallStrategy,
    val authMethods: Set<AuthMethod>,
    val requiredTools: Set<String> = emptySet(),
    val optionalTools: Set<String> = emptySet(),
    val capabilities: Set<AgentCapability> = emptySet(),
    val configurationScope: ConfigurationScope = ConfigurationScope.AGENT_PRIVATE,
    val adapterStatus: AdapterStatus = AdapterStatus.AVAILABLE,
    val notes: String = ""
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]{1,63}"))) { "Invalid agent id: $id" }
    }
}

enum class AgentRuntime { ANDROID_NATIVE, LINUX_CLI, LOCAL_DAEMON, REMOTE_API }
enum class InstallStrategy { BUNDLED, OFFICIAL_INSTALLER, PACKAGE_MANAGER, USER_PROVIDED }
enum class AuthMethod { DEVICE_OAUTH, BROWSER_OAUTH, API_KEY, LOCAL_SESSION, NONE }
enum class ConfigurationScope { SHARED_WORKSPACE, AGENT_PRIVATE, MIXED }
enum class AdapterStatus { AVAILABLE, EXPERIMENTAL, PLANNED, INCOMPATIBLE }
enum class AgentCapability { CHAT, STREAMING, FILE_EDIT, TERMINAL, MCP, SKILLS, PLAN_MODE, APPROVALS, SESSION_RESUME }
