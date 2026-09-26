package com.cognex.realplay.verify

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The registry enforces the CLOSED rule set: every rule has exactly one verifier (Architecture §4.1). */
class VerifierRegistryTest {

    @Test
    fun everyRuleHasAVerifier() {
        val registry = VerifierRegistry.default()
        for (rule in RuleId.entries) {
            val verifier = registry.verifierFor(rule)
            assertNotNull("missing verifier for $rule", verifier)
            assertSame("verifier for $rule reports the wrong rule", rule, verifier.rule)
        }
    }

    @Test
    fun missingVerifier_throwsAtConstruction() {
        // Registering only one verifier must fail — the rest of the rule set is unimplemented.
        val ex = assertThrows(IllegalStateException::class.java) {
            VerifierRegistry.of(
                com.cognex.realplay.verify.verifiers.DistanceLessThanVerifier()
            )
        }
        assertTrue(ex.message!!.contains("No verifier registered"))
    }

    @Test
    fun duplicateVerifier_throwsAtConstruction() {
        val ex = assertThrows(IllegalStateException::class.java) {
            VerifierRegistry.of(
                com.cognex.realplay.verify.verifiers.DistanceLessThanVerifier(),
                com.cognex.realplay.verify.verifiers.DistanceLessThanVerifier()
            )
        }
        assertTrue(ex.message!!.contains("Duplicate verifier"))
    }
}
