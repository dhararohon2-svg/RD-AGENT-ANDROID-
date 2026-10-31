package com.example.gemini.data

import com.example.gemini.data.agent.universal.AdapterStatus
import com.example.gemini.data.agent.universal.AgentCapability
import com.example.gemini.data.agent.universal.DependencyPlanner
import com.example.gemini.data.agent.universal.UniversalAgentRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalAgentRegistryTest {
    @Test fun currentIntegrationsAreRegisteredAsChatReady() {
        val ready = UniversalAgentRegistry.readyForChat().map { it.id }.toSet()
        assertEquals(setOf("antigravity", "claude-code"), ready)
    }

    @Test fun codexIsRegisteredWithoutBeingMisrepresentedAsIntegrated() {
        val codex = UniversalAgentRegistry.find("openai-codex-cli")
        assertNotNull(codex)
        assertEquals(AdapterStatus.PLANNED, codex!!.adapterStatus)
        assertTrue(AgentCapability.FILE_EDIT in codex.capabilities)
        assertTrue(UniversalAgentRegistry.readyForChat().none { it.id == codex.id })
    }

    @Test fun lookupIsCaseInsensitive() {
        assertEquals("claude-code", UniversalAgentRegistry.find("CLAUDE-CODE")?.id)
    }

    @Test fun switchingNeverPlansToRemoveSharedBuildTools() {
        val agy = UniversalAgentRegistry.find("antigravity")!!
        val claude = UniversalAgentRegistry.find("claude-code")!!
        val plan = DependencyPlanner.planSwitch(agy, claude, setOf("git", "gradle", "android-sdk", "agy-hub"))
        assertTrue("git" in plan.sharedToolsToKeep)
        assertTrue("gradle" in plan.sharedToolsToKeep)
        assertTrue("android-sdk" in plan.sharedToolsToKeep)
        assertTrue(!plan.canActivate) // required Claude CLI/runtime are not installed in this inventory
    }
}
