package com.lavara.conditions

import com.lavara.core.TimeText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lo que tiene que ser cierto para que una automatización siga. Cada tipo tiene un @SerialName fijo
 * que nunca se cambia (ver docs/FORMATO_JSON.md).
 */
@Serializable
sealed interface Condition {

    /** Compara el nivel de batería (0 a 100) con [value]. */
    @Serializable
    @SerialName("battery_level")
    data class BatteryLevel(
        val comparison: Comparison,
        val value: Int,
    ) : Condition {
        init {
            require(value in 0..100) { "value debe estar entre 0 y 100, no $value" }
        }
    }

    /**
     * La hora actual está entre [start] (incluida) y [end] (excluida).
     * Si [end] es menor que [start], el rango cruza la medianoche (ej.: 22:00 a 06:00).
     */
    @Serializable
    @SerialName("time_between")
    data class TimeBetween(
        val start: String,
        val end: String,
    ) : Condition {
        init {
            TimeText.requireValid(start, "start")
            TimeText.requireValid(end, "end")
        }
    }

    /** Todas las condiciones de la lista son ciertas. Lista vacía = cierto. */
    @Serializable
    @SerialName("and")
    data class And(val conditions: List<Condition>) : Condition

    /** Al menos una condición de la lista es cierta. Lista vacía = falso. */
    @Serializable
    @SerialName("or")
    data class Or(val conditions: List<Condition>) : Condition

    /** La condición es falsa. */
    @Serializable
    @SerialName("not")
    data class Not(val condition: Condition) : Condition
}

@Serializable
enum class Comparison(val symbol: String) {
    @SerialName("greater_than") GREATER_THAN(">"),
    @SerialName("greater_or_equal") GREATER_OR_EQUAL(">="),
    @SerialName("less_than") LESS_THAN("<"),
    @SerialName("less_or_equal") LESS_OR_EQUAL("<="),
    @SerialName("equal") EQUAL("="),
    ;

    fun test(actual: Int, expected: Int): Boolean = when (this) {
        GREATER_THAN -> actual > expected
        GREATER_OR_EQUAL -> actual >= expected
        LESS_THAN -> actual < expected
        LESS_OR_EQUAL -> actual <= expected
        EQUAL -> actual == expected
    }
}
