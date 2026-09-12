package de.pilzscout.core.model

import kotlinx.serialization.Serializable

/**
 * Reference edibility of a species (never of a photographed mushroom). Ordered from most to least
 * dangerous so the worst value among several candidates can be picked with minBy(ordinal).
 */
@Serializable
enum class Edibility {
    DEADLY, POISONOUS, PSYCHOACTIVE, CAUTION, INEDIBLE, UNKNOWN, EDIBLE, CHOICE;

    val dangerous: Boolean get() = this == DEADLY || this == POISONOUS || this == PSYCHOACTIVE

    companion object {
        fun parse(raw: String?): Edibility? = raw?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
