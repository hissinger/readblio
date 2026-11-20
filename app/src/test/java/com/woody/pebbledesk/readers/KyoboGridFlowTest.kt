package com.woody.pebbledesk.readers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.BookRecorder
import com.woody.pebbledesk.OpenSteps
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.Target
import com.woody.pebbledesk.WatchLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 교보도서관 표지 보기 서재의 흐름(`KyoboLibraryReader`, 가짜 앱 문맥): 서재가 멈추면 칸마다 표지를 칸 자리·반납일 글자 열쇠로 자르고,
 * 칸을 누른 뒤 열린 읽는 화면의 메뉴 제목으로 책이 정해지면 누른 칸의 반납일·열쇠로 편다. 칸·메뉴 자리는 갤럭시 A23 화면 덤프(2026-11-20).
 * Robolectric 에는 요소 이름으로 찾기가 없어 [Yes24ViewerTest.Nodes] 로 나무를 훑는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [Yes24ViewerTest.Nodes::class])
class KyoboGridFlowTest {
    private val pkg = KyoboLibrary.pkg
    private val shelf = KyoboLibrary.shelves.first()
    private val viewer = "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity"
    private val cellRect = Rect(31, 440, 370, 929)
    private val key = KyoboGridCell.key(cellRect, "15일")
    private val today get() = LocalDate.now().toEpochDay()

    private fun node(cls: String, r: Rect, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(r)
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            isVisibleToUser = true
            kids.forEach { shadowOf(this).addChild(it) }
        }

    /** 창 [window] 의 요소로 굳힌다(아래 요소까지) */
    private fun seal(n: AccessibilityNodeInfo, window: Int): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i), window)
        AccessibilityNodeInfo::class.java.getDeclaredField("mWindowId").apply { isAccessible = true }.setInt(n, window)
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 표지 보기 칸(누르는 칸): 표지·표지에 겹친 반납일 글자 */
    private fun gridCell(due: String) = node("android.widget.RelativeLayout", cellRect, null, null,
        node("android.widget.ImageView", Rect(72, 479, 328, 876), "ivThumbnail"),
        node("android.widget.LinearLayout", Rect(72, 820, 328, 876), "info", null,
            node("android.widget.TextView", Rect(200, 820, 328, 876), "tvRemainDate", due)),
    ).apply { isClickable = true }

    /** 서재 창: 표지 보기 칸 하나와 아래 탭 */
    private fun shelfWindow(due: String = "15일") = seal(node("android.widget.FrameLayout", Rect(0, 0, 1080, 2408), "content", null,
        node("android.widget.GridView", Rect(0, 384, 1080, 2040), "rvBookList", null, gridCell(due)),
        node("android.widget.LinearLayout", Rect(39, 2079, 221, 2228), "rl_bottom_study", null).apply { isClickable = true },
    ), SHELF_WINDOW)

    /** 읽는 화면: 메뉴를 띄우면 위 막대의 제목([title]), 늘 아래 진행률 줄 */
    private fun viewerWindow(title: String?) = seal(node("android.widget.FrameLayout", Rect(0, 0, 1080, 2408), "content", null,
        *listOfNotNull(
            title?.let { node("android.widget.RelativeLayout", Rect(0, 66, 1080, 207), "topNavLinear", null,
                node("android.widget.TextView", Rect(90, 103, 842, 169), "viewer_top_booktitle", it)) },
            node("android.widget.TextView", Rect(0, 2343, 1080, 2408), "bookIndicator", "0% (1/713p)"),
        ).toTypedArray(),
    ), VIEWER_WINDOW)

    /** 가짜 앱 문맥: 편 책(제목·반납일·표지 열쇠)과 찍어 달라는 표지 자리를 적는다 */
    private inner class Ctx(var t: Long = 1_000_000L) : AppContext {
        val opened = mutableListOf<Triple<String, Long, String?>>()
        val captured = mutableListOf<List<CoverSpot>>()
        var windowsNow = AppWindows(null, emptyList())
        override val context: Context = RuntimeEnvironment.getApplication()
        override val app: ReaderApp get() = KyoboLibrary
        override val recorder: BookRecorder = FakeRecorder()
        lateinit var adapter: ReaderAdapter
        override fun opened(seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook {
            adapter.beforeOpened(this)
            opened += Triple(seen.title, seen.due, coverKey)
            return ShelfBook("id-${seen.title}", seen.title, "", null, 0)
        }
        override fun unknown(at: Long) {}
        override var unknownOpenAt: Long? = null
        override val now: Long get() = t
        override var screen: String? = shelf
        override var viewerOpenedAt: Long? = null
        override val lastTransition: Long = 0
        override val lastOpenedAt: Long? = null
        override val root: AccessibilityNodeInfo? get() = windowsNow.active
        override fun windows() = windowsNow
        override val prefs: SharedPreferences get() = context.getSharedPreferences("kyobo_grid_flow_test", Context.MODE_PRIVATE)
        override fun log(kind: WatchLog.Kind, arg: String, detail: String) {}
        override fun noWindow() {}
        override val canShoot = true
        override fun capture(spots: List<CoverSpot>, retry: Boolean) { if (spots.isNotEmpty()) captured += spots }
        override fun viewerAgain(ms: Long) {}
        override fun viewerRetry() = false
        override fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) = done(runCatching(work))
    }

    private fun start(): Ctx = Ctx().also { it.adapter = KyoboLibrary.newAdapter() }

    /** 서재가 떠서 멈췄다 */
    private fun showShelf(ctx: Ctx, due: String = "15일") {
        val w = shelfWindow(due)
        ctx.windowsNow = AppWindows(w, listOf(w))
        ctx.screen = shelf
        ctx.adapter.onScreen(ctx, shelf, null, again = false, byWindow = true)
        ctx.adapter.onShelf(ctx)
    }

    /** 서재의 표지 보기 칸을 누른다(누름 글자는 반납일·진행률뿐) */
    private fun clickCell(ctx: Ctx, due: String = "15일") {
        val cell = ctx.windowsNow.active!!.getChild(0).getChild(0)
        ctx.adapter.onClick(ctx, Click(cell, listOf(due, "0%")))
    }

    /** 읽는 화면이 열리고 잠잠해졌다(메뉴가 떠 있으면 [title]) */
    private fun openViewer(ctx: Ctx, title: String?) {
        ctx.t += 1000
        ctx.screen = viewer
        ctx.viewerOpenedAt = ctx.t
        ctx.adapter.onScreen(ctx, viewer, shelf, again = false, byWindow = true)
        showMenu(ctx, title)
    }

    private fun showMenu(ctx: Ctx, title: String?) {
        ctx.t += 1000
        val w = viewerWindow(title)
        ctx.windowsNow = AppWindows(w, listOf(w))
        ctx.adapter.onViewer(ctx)
    }

    @Test fun `서재가 멈추면 표지 보기 칸의 표지를 칸 자리·반납일 글자 열쇠로 찍어 달라고 한다`() {
        val ctx = start()
        showShelf(ctx)
        assertEquals(listOf(key to Rect(72, 479, 328, 876)), ctx.captured.flatten().map { it.key to it.rect })
    }

    @Test fun `누른 칸 뒤 메뉴 제목으로 정한 책에 그 칸의 반납일과 표지 열쇠를 준다`() {
        val ctx = start()
        showShelf(ctx)
        clickCell(ctx)
        // 메뉴를 띄우기 전에는 제목을 몰라 펴지 않는다
        openViewer(ctx, title = null)
        assertTrue(ctx.opened.isEmpty())
        showMenu(ctx, "영혼의 왈츠 2")
        assertEquals(listOf(Triple("영혼의 왈츠 2", today + 15, key)), ctx.opened)
        // 한 번 쓴 칸은 다시 쓰지 않는다(메뉴를 다시 띄우면 바탕 규칙대로 반납일·열쇠 없이)
        showMenu(ctx, "영혼의 왈츠 2")
        assertEquals(Triple("영혼의 왈츠 2", -1L, null), ctx.opened.last())
    }

    @Test fun `메뉴 없이 서재로 돌아오면 누른 칸을 버린다`() {
        val ctx = start()
        showShelf(ctx)
        clickCell(ctx)
        openViewer(ctx, title = null)
        showShelf(ctx)
        // 그 뒤 다른 길(이어 읽기)로 연 책에는 그 칸의 반납일·표지를 붙이지 않는다
        openViewer(ctx, "다른 책")
        assertEquals(listOf(Triple("다른 책", -1L, null)), ctx.opened)
    }

    @Test fun `서재 화면의 다른 것(탭)을 누르면 누른 칸을 잊는다`() {
        val ctx = start()
        showShelf(ctx)
        clickCell(ctx)
        val tab = ctx.windowsNow.active!!.getChild(1)
        ctx.adapter.onClick(ctx, Click(tab, listOf("책장")))
        openViewer(ctx, "다른 책")
        assertEquals(listOf(Triple("다른 책", -1L, null)), ctx.opened)
    }

    @Test fun `반납일이 지난 칸은 기억하지 않는다`() {
        val ctx = start()
        showShelf(ctx, due = "만료")
        clickCell(ctx, due = "만료")
        openViewer(ctx, "만료된 책")
        assertEquals(listOf(Triple("만료된 책", -1L, null)), ctx.opened)
    }

    private companion object {
        const val SHELF_WINDOW = 7
        const val VIEWER_WINDOW = 8
    }
}
