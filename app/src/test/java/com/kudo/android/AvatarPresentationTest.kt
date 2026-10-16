package com.kudo.android

import androidx.compose.ui.graphics.Color
import com.kudo.android.data.*
import com.kudo.android.ui.components.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AvatarPresentationTest {
    private val now = Instant.parse("2026-10-08T08:00:00Z")
    private val status = UserStatus("no_entry_sign", "当前状态", now.plusSeconds(30))
    private val flair = AvatarFlair("资质", "https://cdn.example/flair.gif")
    private val user = Member(218, "current", "当前用户", "", status = status, flair = flair)

    @Test fun accountEntryShowsStatusButTheSameUsersPublicContentShowsFlair() {
        assertEquals(AvatarDecoration.Status(status), avatarDecoration(user, AvatarPresentation.ACCOUNT, now))
        assertEquals(AvatarDecoration.Flair(flair), avatarDecoration(user, AvatarPresentation.PUBLIC, now))
        assertNull(avatarDecoration(user.copy(status = null), AvatarPresentation.ACCOUNT, now))
    }

    @Test fun publicStatusIsOnlyUsedWithoutFlairAndExpiredStatusDisappears() {
        assertEquals(AvatarDecoration.Status(status), avatarDecoration(user.copy(flair = null), AvatarPresentation.PUBLIC, now))
        assertNull(avatarDecoration(user.copy(flair = null), AvatarPresentation.PUBLIC, now.plusSeconds(30)))
        assertNull(avatarDecoration(user, AvatarPresentation.ACCOUNT, now.plusSeconds(30)))
        assertEquals(AvatarDecoration.Flair(flair), avatarDecoration(user, AvatarPresentation.PUBLIC, now.plusSeconds(30)))
        assertNull(avatarDecoration(null, AvatarPresentation.PUBLIC, now))
    }

    @Test fun nicknameKeepsOnlyDecorationsNotAlreadyShownOnThePublicAvatar() {
        assertEquals(listOf(AvatarDecoration.Status(status)), memberNameDecorations(user, now))
        assertTrue(memberNameDecorations(user.copy(status = null), now).isEmpty())
        assertTrue(memberNameDecorations(user.copy(flair = null), now).isEmpty())
        assertTrue(memberNameDecorations(user.copy(flair = null, status = null), now).isEmpty())
    }

    @Test fun expiredStatusIsNotKeptAfterTheNicknameWhileFlairStaysOnTheAvatar() {
        val expired = now.plusSeconds(30)
        assertEquals(AvatarDecoration.Flair(flair), avatarDecoration(user, AvatarPresentation.PUBLIC, expired))
        assertTrue(memberNameDecorations(user, expired).isEmpty())
        assertTrue(memberNameDecorations(user.copy(flair = null), expired).isEmpty())
    }

    @Test fun websiteColorsSupportWhiteShorthandAndCssAlphaOrder() {
        assertEquals(Color.White, avatarColor("fff"))
        assertEquals(Color(0xff00aeff), avatarColor("#00aeff"))
        assertEquals(Color(0x8844aa11), avatarColor("4a18"))
        assertEquals(Color(0x8044aa11), avatarColor("44aa1180"))
        assertNull(avatarColor("not-a-color"))
        assertNull(avatarColor(""))
    }
}
