package com.woody.pebbledesk

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 기기가 앱의 밀도를 덮어썼을 때 홈이 화면이 작아졌다고 보는가([Ui.screenShrunk], 이슈 #29).
 * 더 높은 밀도(BOOX 앱별 DPI)만 작아진 것이고, 더 낮은 밀도(iReader 가 앱 밀도를 무시)·같은 밀도는 아니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w572dp-h777dp-xhdpi")
class ScreenShrunkTest {
    private val app: Context = RuntimeEnvironment.getApplication()

    /** 앱이 맞춘 밀도(짧은 변 572dp → 320dpi)를 정한 뒤, [dpi] 로 그려지는 화면 */
    private fun drawnAt(dpi: Int): Context {
        assertEquals(320, Ui.designDensityDpi(app))
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply { densityDpi = dpi })
    }

    @Test fun `맞춘 밀도 그대로면 작아지지 않았다`() = assertFalse(Ui.screenShrunk(drawnAt(320)))

    @Test fun `더 높은 밀도로 덮어쓰면(BOOX) 작아졌다`() = assertTrue(Ui.screenShrunk(drawnAt(373)))

    @Test fun `더 낮은 밀도로 덮어쓰면(iReader) 작아지지 않았다`() = assertFalse(Ui.screenShrunk(drawnAt(288)))
}
