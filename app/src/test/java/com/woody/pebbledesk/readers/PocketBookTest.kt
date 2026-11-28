package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

/**
 * PocketBook reader(5.66.2). 자리는 CremaPebble_A11(1072×1456) 화면 덤프 그대로:
 * Books 탭 목록 보기 칸 `root_layout` (8,694)-(1064,982), 그 안 `thumbnail` (8,694)-(200,982) 속 표지 그림 `android:id/icon` (25,707)-(191,969).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [PocketBookTest.Nodes::class])
class PocketBookTest {
    /** Robolectric 에는 요소 이름으로 찾기가 없어 안드로이드처럼 나무를 훑어 찾는다(읽는 화면 메뉴의 제목·진행률) */
    @Implements(AccessibilityNodeInfo::class)
    class Nodes : ShadowAccessibilityNodeInfo() {
        @RealObject lateinit var node: AccessibilityNodeInfo
        @Implementation
        protected fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> = ArrayList<AccessibilityNodeInfo>().apply {
            fun walk(n: AccessibilityNodeInfo) {
                if (n.viewIdResourceName == id) add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
            }
            walk(node)
        }
    }

    private val pkg = PocketBook.pkg

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = if (":id/" in it) it else "$pkg:id/$it" }
            text?.let { this.text = it }
            isVisibleToUser = true
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    private fun thumbnail(l: Int, t: Int, r: Int, b: Int) = node("android.widget.FrameLayout", l, t, r, b, "thumbnail", null,
        node("android.widget.ImageView", l + 17, t + 13, r - 9, b - 13, "android:id/icon"),
        node("android.view.View", l + 17, t + 13, l + 45, b - 13, "glareView"),
    )

    /** Books 탭 목록 보기 칸 하나 */
    private fun row(title: String, author: String?, progress: String) = node("android.widget.LinearLayout", 8, 694, 1064, 982, "root_layout", null,
        thumbnail(8, 694, 200, 982),
        node("android.widget.FrameLayout", 200, 694, 1064, 982, "swipe_view", null,
            node("android.widget.LinearLayout", 200, 694, 1064, 982, "book_item", null,
                node("android.widget.TextView", 215, 709, 1049, 764, "tv_title", title),
                *listOfNotNull(author?.let { node("android.widget.TextView", 215, 764, 302, 811, "tv_authors", it) }).toTypedArray(),
                node("android.widget.ImageView", 215, 922, 260, 967, "iv_location"),
                node("android.widget.TextView", 260, 926, 335, 962, "tv_format", "EPUB"),
                node("android.widget.TextView", 974, 926, 1049, 962, "tv_progress", progress),
            ),
        ),
    )

    private fun clicked(cell: AccessibilityNodeInfo, source: AccessibilityNodeInfo = cell): Cell? {
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1072, 1456, "android:id/content", null,
            node("androidx.recyclerview.widget.RecyclerView", 8, 404, 1064, 1456, "books", null, cell)))
        return PocketBook.clickedCell(source, emptyList(), AppWindows(root, listOf(root)), null)
    }

    @Test fun `목록 보기 칸은 제목·저자·진행률, 표지는 여백 없는 그림 자리`() {
        val c = clicked(row("시험 책", "홍길동", "20%"))!!
        assertEquals("시험 책", c.title)
        assertEquals("홍길동", c.author)
        assertEquals(20, c.progress)
        assertEquals(Rect(25, 707, 191, 969), c.thumb)
    }

    @Test fun `막 연 책은 진행률이 빈 글자, 저자 없는 책도`() {
        val c = clicked(row("은빛 마법사의 각성 (1화 ~ 2화)", null, ""))!!
        assertEquals("은빛 마법사의 각성 (1화 ~ 2화)", c.title)
        assertEquals("", c.author)
        assertEquals(-1, c.progress)
    }

    @Test fun `격자 보기 칸은 표지뿐이라 책이 아니다`() {
        val grid = thumbnail(395, 404, 677, 827)
        assertNull(clicked(node("android.widget.FrameLayout", 395, 404, 677, 827, null, null, grid), grid))
    }

    /** 읽는 화면 메뉴(위 막대의 제목, 아래 쪽·진행률, 맨 아래 설정 단추들도 `tv_title`) */
    private fun viewerMenu(title: String?) = seal(node("android.widget.FrameLayout", 0, 45, 1072, 1456, "android:id/content", null,
        node("android.widget.LinearLayout", 0, 45, 1072, 227, "ll_top", null,
            *listOfNotNull(title?.let { node("android.widget.TextView", 0, 152, 1072, 225, "title_view_book", it) }).toTypedArray()),
        node("android.widget.LinearLayout", 0, 1109, 1072, 1456, "layout_seekbar", null,
            node("android.widget.TextView", 495, 1192, 578, 1235, "tv_pages", "1 / 20"),
            node("android.widget.TextView", 589, 1198, 636, 1231, "tv_progress", "(5%)"),
            node("android.widget.TextView", 34, 1376, 70, 1426, "tv_title", "Text"),
            node("android.widget.TextView", 137, 1376, 178, 1426, "tv_title", "View"),
        ),
    ))

    @Test fun `읽는 화면 메뉴의 제목과 괄호 안 진행률`() {
        val root = viewerMenu("은빛 마법사의 각성 (1화 ~ 2화)")
        val v = PocketBook.readViewer(AppWindows(root, listOf(root)))
        assertEquals("은빛 마법사의 각성 (1화 ~ 2화)", v.title)
        assertEquals(5, v.progress)
    }

    @Test fun `메뉴가 없으면 책 모름`() {
        val root = seal(node("android.widget.FrameLayout", 0, 45, 1072, 1456, "the_frame", null))
        val v = PocketBook.readViewer(AppWindows(root, listOf(root)))
        assertNull(v.title)
        assertEquals(-1, v.progress)
    }
}
