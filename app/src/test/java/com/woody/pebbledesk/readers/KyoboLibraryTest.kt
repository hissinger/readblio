package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 교보도서관(2.2.6) 목록 보기 서재 칸: 받는 중에는 표지 오른쪽 위에 진행 원 묶음이 보여 그 칸의 표지는 자르지 않는다.
 * 표지·제목 자리는 크레마 페블(1072×1456) 화면 덤프 그대로, 받기 묶음은 레이아웃(`kel_study_list_item`, 28dp 표지 오른쪽 위)대로 놓았다.
 * 서재 칸은 누른 칸(직접 훑는다)으로 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KyoboLibraryTest {
    private val pkg = KyoboLibrary.pkg

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, visible: Boolean = true, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            isVisibleToUser = visible
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 목록 보기 칸 하나: 표지 자리(표지·받기 묶음), 제목·저자. 받은 칸은 받기 묶음이 감춰져 있다. [shown] 이 아니면 화면 밖 옆 쪽의 칸 */
    private fun cell(title: String, downloading: Boolean, shown: Boolean = true) = node(
        "android.widget.RelativeLayout", 0, 340, 1072, 640, null, null, shown,
        node("android.widget.RelativeLayout", 40, 360, 228, 628, "flMiddleArea", null, true,
            node("android.widget.ImageView", 58, 377, 210, 595, "ivThumbnail"),
            node("android.widget.FrameLayout", 158, 377, 210, 429, "bookshelf_main_grid_item_download_layout", null, downloading),
        ),
        node("android.widget.TextView", 241, 411, 545, 447, "tvTitle", title),
        node("android.widget.TextView", 241, 448, 330, 480, "tvAuthor", "애덤 베커"),
    )

    private fun clicked(cell: AccessibilityNodeInfo): Cell? {
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1072, 1456, "content", null, true,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1072, 1300, "rvBookList", null, true, cell)))
        return KyoboLibrary.clickedCell(cell.getChild(0), emptyList(), AppWindows(root, listOf(root)), null)
    }

    @Test fun `받는 중인 칸은 표지를 자르지 않는다`() {
        val c = clicked(cell("기술이 인류를 구원한다는 착각", downloading = true))!!
        assertEquals("기술이 인류를 구원한다는 착각", c.title)
        assertNull(c.thumb)
    }

    @Test fun `받은 칸은 표지 자리를 잡는다`() =
        assertEquals(Rect(58, 377, 210, 595), clicked(cell("기술이 인류를 구원한다는 착각", downloading = false))!!.thumb)

    @Test fun `보이지 않는 옆 쪽의 칸은 읽지 않는다`() =
        assertNull(clicked(cell("기술이 인류를 구원한다는 착각", downloading = false, shown = false)))

    /** 표지 보기 칸: 누르는 칸 안에 표지·새 책 점·표지 위에 겹친 반납일(`15일`)·진행 막대. 제목이 없다(갤럭시 A23 화면 덤프 2026-11-20) */
    private fun gridCell(downloading: Boolean = false, shown: Boolean = true) = node(
        "android.widget.RelativeLayout", 31, 440, 370, 929, null, null, shown,
        node("android.widget.ImageView", 72, 479, 328, 876, "ivThumbnail"),
        node("android.widget.LinearLayout", 72, 820, 328, 876, "info", null, true,
            node("android.widget.TextView", 200, 820, 328, 876, "tvRemainDate", "15일")),
        node("android.widget.FrameLayout", 276, 479, 328, 531, "bookshelf_main_grid_item_download_layout", null, downloading),
    ).apply { isClickable = true }

    private fun gridOf(cell: AccessibilityNodeInfo): KyoboGridCell? {
        seal(node("android.widget.GridView", 0, 384, 1080, 2040, "rvBookList", null, true, cell))
        return KyoboLibrary.gridCell(cell.getChild(0))
    }

    @Test fun `표지 보기 칸은 칸 자리와 표지 자리(반납일이 겹친 채)와 반납일 글자`() {
        val g = gridOf(gridCell())!!
        assertEquals(Rect(31, 440, 370, 929), g.cell)
        assertEquals(Rect(72, 479, 328, 876), g.thumb)
        assertEquals("15일", g.due)
    }

    @Test fun `잘라 둔 표지 열쇠는 칸 자리와 반납일 글자로 누른 칸과 맞춘다`() {
        val g = gridOf(gridCell())!!
        assertEquals(KyoboGridCell.key(Rect(31, 440, 370, 929), "15일"), g.key)
        // 같은 자리라도 반납일이 다른 칸(쪽을 넘긴 직후의 다른 책)은 다른 열쇠
        assertNotEquals(KyoboGridCell.key(Rect(31, 440, 370, 929), "3일"), g.key)
    }

    @Test fun `받는 중인 표지 보기 칸은 자르지 않는다`() = assertNull(gridOf(gridCell(downloading = true)))

    @Test fun `보이지 않는 표지 보기 칸은 자르지 않는다`() = assertNull(gridOf(gridCell(shown = false)))

    @Test fun `제목이 있는 칸(목록 보기)은 표지 보기 칸이 아니다`() {
        val c = cell("기술이 인류를 구원한다는 착각", downloading = false).apply { isClickable = true }
        seal(node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1072, 1300, "rvBookList", null, true, c))
        assertNull(KyoboLibrary.gridCell(c.getChild(0).getChild(0)))
    }
}
