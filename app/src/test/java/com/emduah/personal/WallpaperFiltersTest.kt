package com.emduah.personal

import com.emduah.personal.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WallpaperFiltersTest {
    @Test fun minimumRatingIncludesEveryHigherRatingInTheApiRequest() {
        val request = Profile(Screen.HOME, filters = """{"rating":[3],"sort":"rating_desc"}""").validated().requestFilters()
        assertEquals(listOf(3, 4, 5), request.getJSONArray("rating").let { a -> (0 until a.length()).map { a.getInt(it) } })
        assertEquals("[5]", Profile(Screen.HOME, filters = """{"rating":[5]}""").requestFilters().getJSONArray("rating").toString())
        assertFalse(Profile(Screen.HOME, filters = """{"rating":[0]}""").requestFilters().has("rating"))
    }

    @Test fun allowsEitherDateBoundOrBothAndKeepsThePathExpression() {
        listOf("""{"date_from":"2026-01-01"}""", """{"date_to":"2026-11-06"}""", """{"date_from":"2026-01-01","date_to":"2026-11-06"}""").forEach { raw ->
            val original = JSONObject(raw).put("path", "forest -draft")
            val request = Profile(Screen.HOME, filters = original.toString()).validated().requestFilters()
            assertEquals(original.optString("date_from"), request.optString("date_from"))
            assertEquals(original.optString("date_to"), request.optString("date_to"))
            assertEquals("forest -draft", request.getString("path"))
        }
    }

    @Test fun rejectsReversedRangesAndInvalidCalendarDates() {
        listOf("""{"date_from":"2026-11-06","date_to":"2026-01-01"}""", """{"date_to":"2026-02-30"}""").forEach { raw ->
            assertThrows(IllegalArgumentException::class.java) { Profile(Screen.HOME, filters = raw).validated() }
        }
    }

    @Test fun additionalJsonPreservesBasicFiltersAndStructuredAdvancedChoices() {
        val original = JSONObject("""{"bucket":35,"sort":"random","type":"video","rating":[4,5],"date_to":"2026-11-06","path":"forest -draft","favorite":true,"seed":"chosen","model":"old"}""")
        val combined = WallpaperFilters.mergeExtraFilters(original, """{"model":"new","metadata":"sunset"}""")
        assertEquals(35, combined.getInt("bucket"))
        assertEquals("video", combined.getString("type"))
        assertEquals("[4,5]", combined.getJSONArray("rating").toString())
        assertEquals("2026-11-06", combined.getString("date_to"))
        assertEquals("forest -draft", combined.getString("path"))
        assertTrue(combined.getBoolean("favorite"))
        assertEquals("chosen", combined.getString("seed"))
        assertEquals("new", combined.getString("model"))
        val additional = WallpaperFilters.extraFilters(combined)
        assertFalse(additional.has("bucket"))
        assertFalse(additional.has("seed"))
        assertEquals("sunset", additional.getString("metadata"))
    }

    @Test fun additionalJsonCannotOverrideBasicControlsOrRequestState() {
        listOf("""{"rating":[1]}""", """{"bucket":2}""", """{"after_id":100}""", """{"max_bytes":9999999}""", "{").forEach { extra ->
            assertThrows(IllegalArgumentException::class.java) { WallpaperFilters.mergeExtraFilters(JSONObject(), extra) }
        }
    }
}
