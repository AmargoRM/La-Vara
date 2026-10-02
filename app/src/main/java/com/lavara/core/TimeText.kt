package com.lavara.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Horas escritas como texto "HH:mm" (por ejemplo "08:00"), el formato del JSON. */
object TimeText {
    private val format = DateTimeFormatter.ofPattern("HH:mm")

    fun parseOrNull(text: String): LocalTime? =
        try {
            LocalTime.parse(text, format)
        } catch (_: DateTimeParseException) {
            null
        }

    fun requireValid(text: String, field: String) {
        require(parseOrNull(text) != null) { "$field debe ser una hora \"HH:mm\" entre 00:00 y 23:59, no \"$text\"" }
    }
}
