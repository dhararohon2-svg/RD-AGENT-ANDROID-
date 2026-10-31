package com.example.gemini.data.agent.universal

/** Result of a read-only preflight; planning never installs or removes packages itself. */
data class AgentDependencyPlan(
    val targetAgentId: String,
    val missingRequired: Set<String>,
    val missingOptional: Set<String>,
    val sharedToolsToKeep: Set<String>,
    val agentPrivateToolsToDeactivate: Set<String>,
    val canActivate: Boolean
)

/**
 * Computes safe switch plans from manifests and a caller-provided inventory. Shared dependencies are
 * never proposed for removal when changing agents. Actual installers must separately ask permission.
 */
object DependencyPlanner {
    private val sharedTools = setOf("git", "android-sdk", "gradle", "node-runtime", "python", "go", "mcp")

    fun planSwitch(
        from: AgentManifest?,
        to: AgentManifest,
        installedTools: Set<String>
    ): AgentDependencyPlan {
        val missingRequired = to.requiredTools - installedTools
        val missingOptional = to.optionalTools - installedTools
        val targetPrivate = to.requiredTools + to.optionalTools - sharedTools
        val sourcePrivate = (from?.requiredTools.orEmpty() + from?.optionalTools.orEmpty()) - sharedTools
        return AgentDependencyPlan(
            targetAgentId = to.id,
            missingRequired = missingRequired,
            missingOptional = missingOptional,
            sharedToolsToKeep = installedTools intersect sharedTools,
            agentPrivateToolsToDeactivate = sourcePrivate - targetPrivate,
            canActivate = missingRequired.isEmpty() && to.adapterStatus == AdapterStatus.AVAILABLE
        )
    }
}
