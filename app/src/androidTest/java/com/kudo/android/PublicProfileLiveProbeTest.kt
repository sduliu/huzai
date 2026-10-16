package com.kudo.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.kudo.android.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File

/** Opt-in read-only contract probe; never sends community mutations. */
class PublicProfileLiveProbeTest {
    @Test fun inspectPublicProfileContractWithoutChangingCommunityData() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("profileReadOnlyProbe") == "true")
        val app = (instrumentation.targetContext.applicationContext as KudoApplication).container
        val username = InstrumentationRegistry.getArguments().getString("profileUsername") ?: "mqq"
        val record = JSONObject().put("username", username)
        val user = app.repository.publicProfile(username)
        record.put("basic", JSONObject().put("id", user.member.id).put("followers", user.followers).put("following", user.following).put("canMessage", user.canMessage))
        record.put("avatar", user.member.avatar).put("animatedAvatar", user.member.animatedAvatar)
        val card = app.repository.profileCardBackground(username)
        record.put("hasCardBackground", card.isNotBlank())
        val raw = app.client.get("/u/$username/summary.json")
        val summary = parseProfileSummary(raw)
        record.put("summaryKeys", raw.optJSONObject("user_summary")?.keys()?.asSequence()?.toList())
        record.put("rawTopicRefs", raw.optJSONObject("user_summary")?.optJSONArray("topic_ids"))
        record.put("popularTopics", summary.topics.size).put("popularReplies", summary.replies.size).put("counts", JSONObject(summary.counts))
        record.put("replyEntries", summary.replies.map { "${it.topicId}/${it.number}" })
        val page = app.repository.profilePage(username, ProfileTab.REPLIES)
        record.put("replyPageSize", page.entries.size).put("replyNext", page.next)
        File(instrumentation.targetContext.getExternalFilesDir(null), "profile-live-contract.json").writeText(record.toString(2))
        println(record.toString(2))
        fun screenshot(name: String) {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        fun contains(node: AccessibilityNodeInfo?, text: String): Boolean {
            if (node == null) return false
            if (node.text?.toString()?.contains(text) == true) return true
            return (0 until node.childCount).any { contains(node.getChild(it), text) }
        }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://linux.do/u/$username"))).use {
            val deadline = SystemClock.uptimeMillis() + 12000
            while (!contains(instrumentation.uiAutomation.rootInActiveWindow, "@${username}") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(250)
            org.junit.Assert.assertTrue(contains(instrumentation.uiAutomation.rootInActiveWindow, "@${username}"))
            SystemClock.sleep(3500)
            screenshot("public-profile-live.png")
        }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://linux.do/404"))).use {
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!contains(instrumentation.uiAutomation.rootInActiveWindow, "内容不存在或暂无权限") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
            org.junit.Assert.assertTrue(contains(instrumentation.uiAutomation.rootInActiveWindow, "内容不存在或暂无权限"))
            screenshot("public-profile-live-404.png")
        }
    }
}
