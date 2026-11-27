package com.woody.pebbledesk

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 카테고리 저장([Categories])과 통계 거르기([StatsFilter]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoriesTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        Categories.clearCache()
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    @Test fun `만들기·이름 바꾸기·순서·지우기와 책에 붙이기`() {
        val novel = Categories.add(app, " 소설 ")!!
        val essay = Categories.add(app, "에세이")!!
        assertEquals("소설", novel.name)
        // 같은 이름은 새로 만들지 않는다, 빈 이름은 만들지 않는다
        assertEquals(novel.id, Categories.add(app, "소설")?.id)
        assertNull(Categories.add(app, "  "))
        assertEquals(listOf("소설", "에세이"), Categories.list(app).map { it.name })

        Categories.move(app, essay.id, -1)
        assertEquals(listOf("에세이", "소설"), Categories.list(app).map { it.name })
        assertFalse(Categories.rename(app, essay.id, "소설"))
        assertTrue(Categories.rename(app, essay.id, "산문"))
        // 같은 이름은 저장할 때처럼 다듬어 본다(30자로 자른 뒤), 자기 이름은 빼고
        val long = Categories.add(app, "가".repeat(30))!!
        assertTrue(Categories.sameName(app, " " + "가".repeat(35)))
        assertFalse(Categories.sameName(app, "가".repeat(30), except = long.id))
        Categories.delete(app, long.id)

        Categories.assign(app, "b-1", setOf(novel.id))
        Categories.assign(app, "b-2", setOf(essay.id))
        assertEquals(listOf("소설"), Categories.of(app, "b-1").map { it.name })
        Categories.assign(app, "b-1", setOf(essay.id))
        assertEquals(listOf("산문"), Categories.of(app, "b-1").map { it.name })
        // 여러 개는 카테고리 순서로(산문이 앞), 없는 카테고리는 붙이지 않는다
        Categories.assign(app, "b-1", setOf(novel.id, essay.id, 99L))
        Categories.clearCache()
        assertEquals(listOf("산문", "소설"), Categories.of(app, "b-1").map { it.name })
        Categories.assign(app, "b-1", emptySet())
        assertTrue(Categories.of(app, "b-1").isEmpty())
        assertFalse("b-1" in Categories.books(app))

        // 지우면 그 책들에서 빠지고 다른 카테고리가 없던 책은 분류 없음, DB 에서 읽어도 같다
        Categories.assign(app, "b-3", setOf(novel.id, essay.id))
        Categories.delete(app, essay.id)
        Categories.clearCache()
        assertTrue(Categories.of(app, "b-2").isEmpty())
        assertEquals(listOf("소설"), Categories.of(app, "b-3").map { it.name })
        assertEquals(listOf("소설"), Categories.list(app).map { it.name })
    }

    private val day = LocalDate.of(2026, 11, 4)
    private val days: ReadingDays = mapOf(day to mapOf(
        ReadKey("p", "소설책", "b-1") to 10L,
        ReadKey("p", "만화책", "b-2") to 20L,
        ReadKey("p", "없는 책", "b-3") to 40L,
        ReadKey("p", "제목만", "") to 80L,
        ReadKey("p") to 160L,
    ))
    private val cats = listOf(Category(1, "소설", false), Category(2, "만화", true))
    private val books = mapOf("b-1" to setOf(1L), "b-2" to setOf(2L))

    @Test fun `전체는 통계에서 뺀 카테고리의 책만 빼고 모르는 시간까지 센다`() {
        val f = StatsFilter(emptySet(), cats, books)
        assertEquals(10L + 40 + 80 + 160, f.days(days).total(day))
        assertTrue(f.keepsBook("b-1"))
        assertFalse(f.keepsBook("b-2"))
        assertTrue(f.keepsBook("t-제목만"))
    }

    @Test fun `거를 것이 없으면 기록을 그대로 쓴다`() {
        assertSame(days, StatsFilter(emptySet(), listOf(Category(1, "소설", false)), books).days(days))
        assertSame(days, StatsFilter(emptySet(), emptyList(), emptyMap()).days(days))
    }

    @Test fun `고른 카테고리만, 분류 없음은 카테고리 없는 책과 제목만 아는 시간`() {
        assertEquals(10L, StatsFilter(setOf(1L), cats, books).days(days).total(day))
        // 통계에서 뺀 카테고리도 직접 고르면 보인다
        assertEquals(20L, StatsFilter(setOf(2L), cats, books).days(days).total(day))
        assertEquals(40L + 80, StatsFilter(setOf(Categories.NONE), cats, books).days(days).total(day))
        // 통계에서 빼지 않은 것을 모두 고르면 전체(모르는 시간까지)
        assertEquals(10L + 40 + 80 + 160, StatsFilter(setOf(1L, Categories.NONE), cats, books).days(days).total(day))
        // 날은 그대로 둔다(기록이 있는 날)
        assertEquals(setOf(day), StatsFilter(setOf(1L), cats, books).days(days).keys)
    }

    @Test fun `없어진 카테고리를 고른 것은 무시하고 남는 것이 없으면 전체`() {
        val f = StatsFilter(setOf(9L), cats, books)
        assertTrue(f.all)
        assertEquals(setOf(1L), StatsFilter(setOf(1L, 9L), cats, books).picked)
    }

    @Test fun `전체를 한 번 더 누르면 켜는 것은 통계에서 빼지 않은 카테고리와 분류 없음, 꼭 그만큼 고르면 전체`() {
        val three = cats + Category(3, "에세이", false)
        val f = StatsFilter(emptySet(), three, books)
        assertEquals(setOf(1L, 3L, Categories.NONE), f.selectable)
        assertTrue(f.with(setOf(1L, 3L, Categories.NONE)).all)
        // 하나 빼면 그대로(모르는 시간은 빠진다)
        assertEquals(setOf(1L, Categories.NONE), f.with(setOf(1L, Categories.NONE)).picked)
        assertEquals(10L + 40 + 80, f.with(setOf(1L, Categories.NONE)).days(days).total(day))
        // 통계에서 뺀 카테고리까지 켜면 전체와 다른 보기
        assertEquals(setOf(1L, 2L, 3L, Categories.NONE), f.with(setOf(1L, 2L, 3L, Categories.NONE)).picked)
        // 없어진 카테고리를 빼고 남은 것이 꼭 그만큼이면 전체
        assertTrue(f.with(setOf(1L, 3L, 9L, Categories.NONE)).all)
    }

    @Test fun `여러 카테고리의 책은 하나라도 고르면 한 번 들고, 전체에서는 하나라도 통계에서 뺐으면 빠진다`() {
        val three = cats + Category(3, "에세이", false)
        // b-1 소설·에세이, b-2 소설·만화(뺌), b-3 분류 없음
        val multi = mapOf("b-1" to setOf(1L, 3L), "b-2" to setOf(1L, 2L))
        val all = StatsFilter(emptySet(), three, multi)
        assertTrue(all.keepsBook("b-1"))
        assertFalse(all.keepsBook("b-2"))
        assertEquals(10L + 40 + 80 + 160, all.days(days).total(day))
        // 둘 다 골라도 한 번
        assertEquals(10L + 20, StatsFilter(setOf(1L, 3L), three, multi).days(days).total(day))
        assertEquals(10L, StatsFilter(setOf(3L), three, multi).days(days).total(day))
        // 통계에서 뺀 카테고리가 붙은 책도 다른 카테고리로 고르면 보인다
        assertEquals(10L + 20, StatsFilter(setOf(1L), three, multi).days(days).total(day))
        assertEquals(40L + 80, StatsFilter(setOf(Categories.NONE), three, multi).days(days).total(day))
    }

    @Test fun `여러 카테고리에서 홈 하단·독서 기록의 오늘 시간과 연속일, 올해 읽은 책 권수`() {
        val today = LocalDate.of(2026, 11, 27)
        val cats = listOf(Category(1, "소설", false), Category(2, "에세이", false), Category(3, "만화", true))
        val multi = mapOf("demian" to setOf(1L, 2L), "essay" to setOf(2L), "onepiece" to setOf(1L, 3L))
        fun key(id: String) = ReadKey("p", id, id)
        // 오늘은 셋, 어제는 만화가 붙은 원피스만, 그제는 데미안
        val days: ReadingDays = mapOf(
            today to mapOf(key("demian") to 30 * 60_000L, key("essay") to 20 * 60_000L, key("onepiece") to 40 * 60_000L),
            today.minusDays(1) to mapOf(key("onepiece") to 40 * 60_000L),
            today.minusDays(2) to mapOf(key("demian") to 30 * 60_000L),
        )
        // 홈 하단(전체): 원피스가 빠져 오늘 50분, 어제는 읽지 않은 날이라 연속일은 오늘 하루
        val all = StatsFilter(emptySet(), cats, multi).days(days)
        assertEquals(50 * 60_000L, all.total(today))
        assertEquals(1, ReadingLog.streak(all, today))
        // 소설로 거르면 원피스가 들어 어제도 읽은 날: 사흘
        val novel = StatsFilter(setOf(1L), cats, multi).days(days)
        assertEquals(70 * 60_000L, novel.total(today))
        assertEquals(3, ReadingLog.streak(novel, today))
        // 소설·에세이: 데미안 한 번
        assertEquals(90 * 60_000L, StatsFilter(setOf(1L, 2L), cats, multi).days(days).total(today))
        // 올해 읽은 책 권수(책마다 [StatsFilter.keepsBook])
        val year = listOf("demian", "essay", "onepiece", "plain")
        fun count(picked: Set<Long>) = StatsFilter(picked, cats, multi).let { f -> year.count(f::keepsBook) }
        assertEquals(3, count(emptySet()))
        assertEquals(2, count(setOf(1L)))
        assertEquals(2, count(setOf(2L)))
        assertEquals(3, count(setOf(1L, 2L)))
        assertEquals(1, count(setOf(3L)))
        assertEquals(1, count(setOf(Categories.NONE)))
    }

    @Test fun `관리 화면 권수는 카테고리마다 세고, 그 카테고리만 붙은 책과 지우면 통계에 나오는 책을 따로 센다`() {
        val novel = Categories.add(app, "소설")!!
        val essay = Categories.add(app, "에세이")!!
        val comic = Categories.add(app, "만화")!!
        val webtoon = Categories.add(app, "웹툰")!!
        Categories.setExcluded(app, comic.id, true)
        Categories.setExcluded(app, webtoon.id, true)
        listOf("데미안", "수레바퀴 아래서", "싯다르타", "유리알 유희").forEach { BookShelf.add(app, it, "헤세") }
        val (a, b, c, d) = BookShelf.list(app).map { it.id }
        Categories.assign(app, a, setOf(novel.id, essay.id))
        Categories.assign(app, b, setOf(novel.id))
        // 만화·소설: 만화를 지우면 통계에 나온다. 만화·웹툰: 웹툰으로 계속 빠진다
        Categories.assign(app, c, setOf(comic.id, novel.id))
        Categories.assign(app, d, setOf(comic.id, webtoon.id))
        // 목록에 없는 책은 세지 않는다
        Categories.assign(app, "b-gone", setOf(essay.id))
        val counts = Categories.bookCounts(app)
        assertEquals(Categories.BookCount(3, 1, 0), counts[novel.id])
        assertEquals(Categories.BookCount(1, 0, 0), counts[essay.id])
        assertEquals(Categories.BookCount(2, 0, 1), counts[comic.id])
        assertEquals(Categories.BookCount(1, 0, 0), counts[webtoon.id])
    }

    @Test fun `상세 카테고리 글자는 칸에 드는 만큼, 넘치면 앞에서부터와 나머지 수`() {
        // 글자 하나를 10px 로 재는 그리기
        val paint = object : android.text.TextPaint() {
            override fun measureText(text: String): Float = text.length * 10f
        }
        val more = "%1${'$'}s 외 %2${'$'}d"
        val names = listOf("소설", "에세이", "인문", "과학")
        // `소설·에세이·인문·과학  ›` 15자
        assertEquals("소설·에세이·인문·과학  ›", categoryLabel(names, more, paint, 150))
        // `소설·에세이·인문 외 1` 은 다 보이는 것보다 길어 건너뛰고 `소설·에세이 외 2  ›` 13자
        assertEquals("소설·에세이 외 2  ›", categoryLabel(names, more, paint, 149))
        assertEquals("소설 외 3  ›", categoryLabel(names, more, paint, 129))
        assertEquals("없음  ›", categoryLabel(listOf("없음"), more, paint, 50))
    }
}
