package com.woody.pebbledesk

import android.content.res.Resources
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 홈 상단 날짜 형식(설정 › 화면 › 날짜, 이슈 #15 의 요청). 요일과 달 이름은 앱 언어로 쓰고, [DEVICE] 만 안드로이드의 언어·지역을 따른다.
 * [DAY_MONTH] 는 영어만(한국어로는 어색해 고르기에 넣지 않고, 한국어 화면이면 [APP] 처럼 그린다).
 */
enum class DateStyle(val key: String) {
    /** 앱 언어의 형식(1.9.0 까지): `Mon, Nov 16` · `11월 16일 월요일` */
    APP("app"),
    /** `Mon, 16.11.2026` · `월, 16.11.2026` */
    DMY("dmy"),
    /** `Monday, 16.11.2026` · `월요일, 16.11.2026` */
    DMY_FULL("dmy_full"),
    /** 기기 지역 형식: 독일어 기기면 `Mo., 16.11.2026`, 한국어 기기면 `2026. 11. 16. (월)` */
    DEVICE("device"),
    /** `Mon, 16 Nov`(영어만) */
    DAY_MONTH("day_month");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: APP

        /** 고르기 메뉴에 보이는 형식(앱 언어가 영어면 [DAY_MONTH] 까지) */
        fun choices(english: Boolean) = if (english) entries else entries - DAY_MONTH
    }
}

object HomeDate {
    /** 앱 언어의 요일·달 이름 */
    private fun appLocale(english: Boolean): Locale = if (english) Locale.US else Locale.KOREA

    /**
     * [style] 로 [date] 를 쓴다. [english] 는 앱 언어, [device] 는 안드로이드의 언어·지역(앱 언어로 덮어쓰지 않은 것, 기본은 시스템 설정).
     * 영어만 있는 형식을 한국어 화면에서 쓰면 [DateStyle.APP] 으로.
     */
    fun format(style: DateStyle, english: Boolean, date: Date, device: Locale = deviceLocale()): String = formatter(style, english, device).format(date)

    /** [format] 의 형식기(같은 형식이면 다시 만들지 않도록 부르는 쪽이 들고 있는다) */
    fun formatter(style: DateStyle, english: Boolean, device: Locale = deviceLocale()): SimpleDateFormat {
        val app = appLocale(english)
        return when (if (style == DateStyle.DAY_MONTH && !english) DateStyle.APP else style) {
            DateStyle.APP -> if (english) SimpleDateFormat("EEE, MMM d", app) else SimpleDateFormat("M월 d일 EEEE", app)
            DateStyle.DMY -> SimpleDateFormat("EEE, dd.MM.yyyy", app)
            DateStyle.DMY_FULL -> SimpleDateFormat("EEEE, dd.MM.yyyy", app)
            DateStyle.DEVICE -> SimpleDateFormat(DateFormat.getBestDateTimePattern(device, "EEEddMMyyyy"), device)
            DateStyle.DAY_MONTH -> SimpleDateFormat("EEE, d MMM", app)
        }
    }

    /** 안드로이드에 정한 언어·지역(앱이 자기 화면의 언어를 바꿔도 그대로인 시스템 설정) */
    fun deviceLocale(): Locale = Resources.getSystem().configuration.locales[0]
}
