package com.emduah.personal.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object WallpaperFilters {
    val basicKeys = setOf("bucket", "sort", "type", "date_from", "date_to", "rating", "path")
    private val advancedControls = setOf("directory", "favorite", "status", "tag", "category", "seed", "path_mode")
    private val managedKeys = setOf("trash", "max_bytes", "after_id", "position", "limit", "cursor")
    private val excludedFromExtra = basicKeys + advancedControls + managedKeys

    fun minimumRating(filters: JSONObject): Int? {
        val ratings = filters.optJSONArray("rating") ?: return null
        if (ratings.length() == 0) return null
        val minimum = (0 until ratings.length()).minOf { ratings.getInt(it) }
        require(minimum in 0..5) { "Minimum rating must be between 0 and 5 stars." }
        return minimum.takeIf { it > 0 }
    }

    fun setMinimumRating(filters: JSONObject, minimum: Int?) {
        require(minimum == null || minimum in 0..5) { "Minimum rating must be between 0 and 5 stars." }
        if (minimum == null || minimum == 0) filters.remove("rating")
        else filters.put("rating", JSONArray((minimum..5).toList()))
    }

    fun validateDates(filters: JSONObject) {
        fun date(key: String, label: String): LocalDate? {
            val value = filters.optString(key)
            if (value.isBlank()) return null
            return runCatching { LocalDate.parse(value) }.getOrElse { throw IllegalArgumentException("$label must be a valid date (YYYY-MM-DD).") }
        }
        val minimum = date("date_from", "Minimum date")
        val maximum = date("date_to", "Maximum date")
        require(minimum == null || maximum == null || !maximum.isBefore(minimum)) { "Maximum date must be on or after the minimum date." }
    }

    fun extraFilters(filters: JSONObject): JSONObject = JSONObject().apply {
        filters.keys().forEach { key -> if (key !in excludedFromExtra) put(key, filters.get(key)) }
    }

    fun mergeExtraFilters(filters: JSONObject, extraText: String): JSONObject {
        val extras = runCatching { JSONObject(extraText) }.getOrElse { throw IllegalArgumentException("Additional filters must be a valid JSON object.") }
        extras.keys().forEach { key -> require(key !in excludedFromExtra) { "Set $key using its configuration control instead of additional JSON." } }
        return JSONObject().apply {
            filters.keys().forEach { key -> if (key in basicKeys || key in advancedControls) put(key, filters.get(key)) }
            extras.keys().forEach { key -> put(key, extras.get(key)) }
        }
    }
}
