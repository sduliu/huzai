package com.kudo.android

import com.kudo.android.data.DiscourseParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TopicResumeDataTest {
    private fun topic() = JSONObject("""{"id":5,"title":"续读","highest_post_number":500,"post_stream":{"stream":[1],"posts":[]}}""")

    @Test fun retainsServerProgressAndResumesAtTheNextUnreadFloor() {
        val detail = DiscourseParser.detail(topic().put("last_read_post_number", 7))
        assertEquals(7, detail.lastReadPostNumber)
        assertEquals(8, detail.resumePostNumber)
    }

    @Test fun absentOrInvalidProgressDoesNotCreateAResumeTarget() {
        listOf(null, JSONObject.NULL, 0, -1).forEach { progress ->
            val detail = DiscourseParser.detail(topic().put("last_read_post_number", progress))
            assertNull(detail.lastReadPostNumber)
            assertNull(detail.resumePostNumber)
        }
    }

    @Test fun completedAndOutOfRangeProgressCapsAtTheHighestFloorWithoutOverflow() {
        listOf(500, 600, Int.MAX_VALUE).forEach { progress ->
            assertEquals(500, DiscourseParser.detail(topic().put("last_read_post_number", progress)).resumePostNumber)
        }
        assertEquals(Int.MAX_VALUE, DiscourseParser.detail(topic()
            .put("highest_post_number", Int.MAX_VALUE).put("last_read_post_number", Int.MAX_VALUE)).resumePostNumber)
    }
}
