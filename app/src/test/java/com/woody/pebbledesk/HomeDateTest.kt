package com.woody.pebbledesk

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale

/** 홈 상단 날짜 형식(설정 › 화면 › 날짜)과 자주 쓰는 앱 격자 칸 수·앱 이름 설정 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeDateTest {
    /** 2026-11-16(월) */
    private val day = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 16, 9, 30) }.time
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        Storage.of(context).getSharedPreferences(HomePrefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `앱 언어 형식은 1_9_0 까지와 같다`() {
        assertEquals("Mon, Nov 16", HomeDate.format(DateStyle.APP, english = true, date = day, device = Locale.GERMANY))
        assertEquals("11월 16일 월요일", HomeDate.format(DateStyle.APP, english = false, date = day, device = Locale.GERMANY))
    }

    @Test fun `일_월_년은 요일만 앱 언어로`() {
        assertEquals("Mon, 16.11.2026", HomeDate.format(DateStyle.DMY, true, day, Locale.GERMANY))
        assertEquals("Monday, 16.11.2026", HomeDate.format(DateStyle.DMY_FULL, true, day, Locale.GERMANY))
        assertEquals("월, 16.11.2026", HomeDate.format(DateStyle.DMY, false, day, Locale.GERMANY))
        assertEquals("월요일, 16.11.2026", HomeDate.format(DateStyle.DMY_FULL, false, day, Locale.GERMANY))
    }

    @Test fun `기기 지역 형식은 안드로이드 언어·지역을 따른다`() {
        assertEquals("Mo., 16.11.2026", HomeDate.format(DateStyle.DEVICE, true, day, Locale.GERMANY))
        // 앱 언어와 상관없다
        assertEquals("Mo., 16.11.2026", HomeDate.format(DateStyle.DEVICE, false, day, Locale.GERMANY))
        assertTrue(HomeDate.format(DateStyle.DEVICE, true, day, Locale.KOREA).contains("2026"))
    }

    @Test fun `일 월은 영어만, 한국어 화면이면 앱 언어 형식`() {
        assertEquals("Mon, 16 Nov", HomeDate.format(DateStyle.DAY_MONTH, true, day, Locale.GERMANY))
        assertEquals("11월 16일 월요일", HomeDate.format(DateStyle.DAY_MONTH, false, day, Locale.GERMANY))
        assertTrue(DateStyle.DAY_MONTH in DateStyle.choices(english = true))
        assertFalse(DateStyle.DAY_MONTH in DateStyle.choices(english = false))
    }

    @Test fun `처음은 앱 언어 형식, 모르는 값도 앱 언어 형식`() {
        val prefs = HomePrefs(context)
        assertEquals(DateStyle.APP, prefs.dateStyle)
        prefs.dateStyle = DateStyle.DEVICE
        assertEquals(DateStyle.DEVICE, HomePrefs(context).dateStyle)
        assertEquals(DateStyle.APP, DateStyle.of("iso"))
    }

    @Test fun `격자는 처음 4칸·이름 켜짐, 고를 수 없는 칸 수는 4칸`() {
        val prefs = HomePrefs(context)
        assertEquals(4, prefs.favColumns)
        assertTrue(prefs.favNames)
        prefs.favColumns = 6
        prefs.favNames = false
        assertEquals(6, HomePrefs(context).favColumns)
        assertFalse(HomePrefs(context).favNames)
        prefs.favColumns = 9
        assertEquals(4, HomePrefs(context).favColumns)
    }
}
