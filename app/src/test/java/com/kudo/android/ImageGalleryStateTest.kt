package com.kudo.android

import androidx.compose.runtime.saveable.SaverScope
import com.kudo.android.ui.topic.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryStateTest {
    private fun picture(number: Int) = ArticleBlock.Image("https://images.test/$number.png", "图片 $number", 2f)

    @Test fun returningFromTheThirdOccurrenceTargetsThatOccurrenceEvenWhenUrlsRepeat() = runTest {
        val state = ImageGalleryState()
        val repeated = picture(1)
        val pictures = listOf(repeated, picture(2), repeated, picture(4), picture(5))
        var returnedIndex = -1
        var returnedImage: ArticleBlock.Image? = null
        state.origins["post-10"] = GalleryOrigin { image, index -> returnedImage = image; returnedIndex = index; null }
        state.open(pictures, 0, "post-10")
        state.session!!.select(2)
        state.dismiss()
        assertEquals(2, returnedIndex)
        assertEquals(repeated, returnedImage)
        assertNull(state.session)
    }

    @Test fun loadingAnAlbumKeepsTheDisplayedUploadAndPreservesDuplicateOccurrences() = runTest {
        val original = "https://linux.do/uploads/default/original/4X/a/b/c/abcdefghijklmnopqrstuvwxyz.png"
        val thumbnail = "https://linux.do/uploads/default/optimized/4X/a/b/c/abcdefghijklmnopqrstuvwxyz_2_690x138.png"
        val current = ArticleBlock.Image(thumbnail, "预览")
        val full = ArticleBlock.Image(original, "原图")
        val album = listOf(picture(1), full, picture(3), full)
        val state = ImageGalleryState()
        state.open(listOf(current), 0, "card", topicId = 23)
        state.loadAlbum(state.session!!) { id -> assertEquals(23, id); album }
        assertEquals(album, state.session!!.images)
        assertEquals(1, state.session!!.index)
        assertEquals(4, state.session!!.images.size)
    }

    @Test fun aFailedAlbumKeepsThePreviewAndCanBeRetriedWithoutReopening() = runTest {
        val state = ImageGalleryState()
        state.open(listOf(picture(1)), 0, "card", topicId = 7)
        val session = state.session!!
        state.loadAlbum(session) { error("offline") }
        assertEquals(listOf(picture(1)), session.images)
        assertTrue(session.error)
        assertFalse(session.loading)
        session.retryAlbum()
        state.loadAlbum(session) { listOf(picture(1), picture(2)) }
        assertFalse(session.error)
        assertEquals(2, session.images.size)
    }

    @Test fun aLateAlbumCannotReplaceTheNewlyOpenedCommentGallery() = runTest {
        val gate = CompletableDeferred<List<ArticleBlock.Image>>()
        val state = ImageGalleryState()
        state.open(listOf(picture(1)), 0, "card", topicId = 7)
        val old = state.session!!
        val loading = launch { state.loadAlbum(old) { gate.await() } }
        runCurrent()
        state.dismiss()
        state.open(listOf(picture(8), picture(9)), 1, "comment")
        gate.complete(listOf(picture(1), picture(2), picture(3)))
        loading.join()
        assertEquals("comment", state.session!!.origin)
        assertEquals(picture(9), state.session!!.current)
        assertEquals(listOf(picture(1)), old.images)
    }

    @Test fun repeatedCloseAndPageEventsCannotChangeTheChosenReturnTarget() = runTest {
        val gate = CompletableDeferred<Unit>()
        val state = ImageGalleryState()
        var reveals = 0
        state.origins["post"] = GalleryOrigin { image, index ->
            assertEquals(picture(3), image)
            assertEquals(2, index)
            reveals++
            gate.await()
            null
        }
        state.open((1..5).map(::picture), 0, "post")
        val closing = launch { state.dismiss(2) }
        runCurrent()
        state.session!!.select(0)
        state.dismiss(0)
        assertEquals(1, reveals)
        assertEquals(2, state.session!!.index)
        gate.complete(Unit)
        closing.join()
        assertNull(state.session)
    }

    @Test fun savedStateRetainsTheCurrentPageAndMetadataWithoutKeepingLayoutObjects() {
        val state = ImageGalleryState()
        val image = picture(3).copy(originalUrl = "https://images.test/original.png", filename = "screen.png", dimensions = "1688×339", sizeLabel = "22.8 KB")
        state.open(listOf(picture(1), picture(2), image), 0, "post")
        state.session!!.select(2)
        val saved = with(ImageGalleryState.Saver) { object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }.save(state) }
        val restored = ImageGalleryState.Saver.restore(saved!!)!!
        assertEquals(2, restored.session!!.index)
        assertEquals(image, restored.session!!.current)
        assertTrue(restored.session!!.restored)
        assertTrue(restored.origins.isEmpty())
        assertNull(restored.openingAnchor)
    }
}
