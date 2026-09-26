package com.cognex.realplay.party

/**
 * The two session shapes (Architecture §25). SOLO is today's one-player/turn-based flow; PARTY
 * sequences the SAME verifiable skills into a crowd-engagement rotation. Adds no verifier and no
 * new way to win (§20 invariant 24).
 */
enum class SessionMode { SOLO, PARTY }

/** The four PARTY formats (§25), all built from the shipped skills — no dedicated rule engine. */
enum class PartyFormat { RELAY, HEAD_TO_HEAD, TEAM_VS_TEAM, CO_OP_STREAK }
