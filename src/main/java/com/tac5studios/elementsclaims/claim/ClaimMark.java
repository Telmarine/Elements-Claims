package com.tac5studios.elementsclaims.claim;

import java.util.UUID;

/**
 * A claim mark: a shared map mark for a claim (for example "Base"), shown on Codex: Cartography to the claim's
 * trusted players whose rank has View claim marks. Made by the owner or a rank with Manage land.
 */
public record ClaimMark(UUID id, String name, String emblem, int color, String dimension, int x, int y, int z) {}
