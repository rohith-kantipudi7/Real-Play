package com.cognex.realplay.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for the composer's hard boundary (Architecture §4.1, §6.5, §20 invariants 18–20). */
class SchemaValidatorTest {

    private val feasible = setOf("G0", "G1", "G2")

    @Test fun accepts_valid_proposal_with_reword() {
        val raw = """{"generatorId":"G1","instruction":"Push them together!","hints":["Slide it closer"]}"""
        val r = SchemaValidator.validate(raw, feasible)
        assertTrue(r is SchemaValidator.Result.Accepted)
        val p = (r as SchemaValidator.Result.Accepted).proposal
        assertEquals("G1", p.generatorId)
        assertEquals("Push them together!", p.instruction)
        assertEquals(listOf("Slide it closer"), p.hints)
    }

    @Test fun extracts_json_from_surrounding_prose() {
        val raw = "Sure! Here you go:\n{\"generatorId\":\"G2\"}\nHope that helps."
        val r = SchemaValidator.validate(raw, feasible)
        assertTrue(r is SchemaValidator.Result.Accepted)
        assertEquals("G2", (r as SchemaValidator.Result.Accepted).proposal.generatorId)
    }

    @Test fun rejects_unknown_or_infeasible_skill() {
        val r = SchemaValidator.validate("""{"generatorId":"G9"}""", feasible)
        assertTrue(r is SchemaValidator.Result.Rejected)
    }

    @Test fun rejects_empty_and_non_json() {
        assertTrue(SchemaValidator.validate(null, feasible) is SchemaValidator.Result.Rejected)
        assertTrue(SchemaValidator.validate("", feasible) is SchemaValidator.Result.Rejected)
        assertTrue(SchemaValidator.validate("no json here", feasible) is SchemaValidator.Result.Rejected)
    }

    @Test fun rejects_missing_generator_id() {
        val r = SchemaValidator.validate("""{"instruction":"do it"}""", feasible)
        assertTrue(r is SchemaValidator.Result.Rejected)
    }

    @Test fun rejects_unsafe_instruction_verbs() {
        val r = SchemaValidator.validate("""{"generatorId":"G1","instruction":"Throw the knife!"}""", feasible)
        assertTrue(r is SchemaValidator.Result.Rejected)
    }

    @Test fun rejects_overlong_instruction() {
        val long = "x".repeat(300)
        val r = SchemaValidator.validate("""{"generatorId":"G1","instruction":"$long"}""", feasible)
        assertTrue(r is SchemaValidator.Result.Rejected)
    }

    @Test fun accepts_id_only_proposal_without_reword() {
        val r = SchemaValidator.validate("""{"generatorId":"G0"}""", feasible)
        assertTrue(r is SchemaValidator.Result.Accepted)
        val p = (r as SchemaValidator.Result.Accepted).proposal
        assertEquals("G0", p.generatorId)
        assertEquals(null, p.instruction)
        assertEquals(null, p.hints)
    }
}
