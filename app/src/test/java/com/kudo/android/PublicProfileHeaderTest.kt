package com.kudo.android

import com.kudo.android.ui.account.profileToolbarIdentityAlpha
import org.junit.Assert.assertEquals
import org.junit.Test

class PublicProfileHeaderTest {
    @Test fun toolbarIdentityAppearsAfterExpandedHeaderFadesAway() {
        assertEquals(0f, profileToolbarIdentityAlpha(0f), 0f)
        assertEquals(0f, profileToolbarIdentityAlpha(.5f), 0f)
        assertEquals(.5f, profileToolbarIdentityAlpha(.75f), .001f)
        assertEquals(1f, profileToolbarIdentityAlpha(1f), 0f)
    }
}
