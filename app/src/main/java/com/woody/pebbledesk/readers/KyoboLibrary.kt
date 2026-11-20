package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.Seen
import java.time.LocalDate

/**
 * 교보도서관: 서재(목록·표지 보기) 칸에 제목·저자·진행률·반납(`반납 4일 남음`). 표지 보기 칸에는 제목이 없다(`4일`, `47%` 만).
 * 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄(`bookIndicator` `2% (4/226p)`)과 메뉴의 제목만 이름으로 읽는다.
 * 받지 않은 칸은 누르면 받기만 하므로 읽는 화면이 열려야 편 것으로 본다([openOnViewer], 2026-10-24 페블에서 받기만 했는데 들어왔다).
 * 받는 중인 칸은 표지 오른쪽 위에 진행 원(가운데 멈춤 단추)을 겹쳐 그려 그런 칸의 표지는 자르지 않는다(2026-10-31 페블에서 원이 표지에 찍혔다).
 * 원은 칸 레이아웃 `kel_study_list_item` 의 받기 묶음 `view_download_progress`(2.2.6)에 들고, 같은 묶음의 받기 단추도 가린다(받지 않은 칸에 보이는지는 미확인).
 * 받은 칸은 묶음이 감춰진다(페블 화면 덤프). [COVER_OVERLAYS]
 * 표지 보기 칸에는 제목이 없어, 누른 칸의 반납일과 미리 잘라 둔 표지를 기억했다가 메뉴 제목으로 책이 정해지면 붙인다([KyoboLibraryReader]).
 */
object KyoboLibrary : ReaderApp(
    pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
    shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
    viewers = setOf(
        "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
        "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
    ),
    titles = listOf("tvTitle"), thumbs = listOf("ivThumbnail"), coverOverlays = COVER_OVERLAYS, authors = listOf("tvAuthor"), progress = "tvReadPercentageTxt", due = "tvRemainDate",
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle", openOnViewer = true,
) {
    /**
     * 읽는 화면은 책을 연 뒤 쪽수를 세는 동안(아래 줄 `- % ( - /326p)`) 본문 웹 화면과 창 맨 바깥 묶음(`FrameLayout`)이 쉬지 않고(0.2~0.3초마다)
     * 내용 바뀜을 보내 잠잠해지지 않았다: 그동안 띄운 메뉴의 제목을 읽지 못해 표지 보기로 연 책이 들어오지 않았다(2026-11-20 갤럭시 A23, 2.2.6).
     * 메뉴(`bottomNavLinear`)와 아래 진행률 줄(`bookIndicator`)은 다른 종류라 그대로 본다.
     */
    override fun ignoresViewerContent(cls: String?) = cls in BUSY_VIEWER_CLASSES

    /** 받기만 한 칸을 누른 뒤 탭을 눌러 다른 책을 열면 그 칸이 들어왔다(2026-10-24 페블) */
    override val forgetsClickOnOtherTap get() = true

    /**
     * 보이지 않는 칸은 읽지 않는다: 서재 쪽 넘기기(`vpBookShelf`)가 옆 쪽을 화면 밖에 두어 같은 책 칸이 하나 더(자리가 화면 밖, 폭이 음수) 먼저 읽히고,
     * 그 칸의 표지 자리로 찍으려다 표지를 자르지 못했다(2026-10-31 페블).
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? = if (!cell.isVisibleToUser) null else super.readCell(cell)

    override fun newAdapter(): ReaderAdapter = KyoboLibraryReader(this)

    /**
     * 표지 보기 칸들. 받는 중인 칸·보이지 않는 칸(옆 쪽)·쓸 수 없는 표지 자리는 뺀다.
     * 칸의 반납일 글자(`15일`)가 표지 아래쪽에 겹쳐 있어 표지에 같이 찍힌다(사용자 결정 2026-11-20: 그대로 자른다, 앱 안내에 적음).
     */
    fun gridCells(root: AccessibilityNodeInfo): List<KyoboGridCell> = thumbs.flatMap { root.byId(pkg, it) }.mapNotNull(::gridCell)

    /** 표지 [thumb] 이 든 표지 보기 칸. 제목이 있는 칸(목록 보기)·받는 중·보이지 않는 칸이면 null */
    fun gridCell(thumb: AccessibilityNodeInfo): KyoboGridCell? {
        var cell: AccessibilityNodeInfo? = thumb.parent
        for (i in 0 until CELL_UP) { if (cell == null || cell.isClickable) break; cell = cell.parent }
        val c = cell?.takeIf { it.isClickable && it.isVisibleToUser && !it.isList() } ?: return null
        var dueText = ""
        var covered = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            if (id == due) n.text?.let { dueText = plain(it) }
            if (id in coverOverlays && n.isVisibleToUser) covered = true
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(c, 0)
        if (covered || c.count(pkg, titles, stopAt = 0) > 0) return null
        return thumb.bounds().takeIf(::usableThumb)?.let { KyoboGridCell(c.bounds(), it, dueText) }
    }
}

/**
 * 교보도서관 표지 보기 칸 하나: 누르는 칸 자리 [cell], 표지 자리 [thumb], 반납일 글자 [due](`15일`, 없으면 "").
 * 잘라 둔 표지의 열쇠는 칸 자리와 반납일 글자([key]): 쪽을 넘긴 직후(서재가 멈춰 다시 찍기 전) 같은 자리의 다른 책을 눌러도 앞 쪽 책의 표지를 붙이지 않게.
 */
class KyoboGridCell(val cell: Rect, val thumb: Rect, val due: String) {
    val key get() = key(cell, due)

    companion object {
        fun key(cell: Rect, due: String) = "kyobo-grid:${cell.flattenToString()}:$due"
    }
}

/**
 * 교보도서관 어댑터: 표지 보기 서재. 칸에 제목이 없어 서재가 멈출 때 칸마다 표지를 칸 자리·반납일 글자 열쇠로 잘라 두고([KyoboGridCell.key]),
 * 칸을 누르면(누름 글자가 `15일`·`0%` 뿐) 그 반납일과 열쇠를 기억했다가 그 뒤 연 읽는 화면의 메뉴 제목으로 책이 정해지면 반납일·표지를 붙인다.
 * 읽는 화면이 제목 없이 닫히면(메뉴를 띄우지 않음) 버린다(서재로 돌아오면 칸 자리가 바뀌어 있을 수 있다).
 */
private class KyoboLibraryReader(private val kyobo: KyoboLibrary) : ScreenReader(kyobo) {
    /** 누른 표지 보기 칸: 반납일(모르면 -1), 잘라 둔 표지 열쇠, 누른 때, 누른 창 */
    private class GridClick(val due: Long, val key: String, val at: Long, val window: Int)
    private var gridClick: GridClick? = null

    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        dropUsedClick(ctx)
        super.onShelfLive(ctx, force)
    }

    override fun onShelf(ctx: AppContext) {
        dropUsedClick(ctx)
        super.onShelf(ctx)
        val root = ctx.root ?: return
        // 목록 보기에서는 칸이 없다(바탕이 제목 칸의 표지를 찍었으면 여기서는 찍지 않는다: retry 없이)
        val spots = kyobo.gridCells(root).map { CoverSpot("", it.thumb, key = it.key) }
        ctx.capture(spots, retry = false)
    }

    override fun onClick(ctx: AppContext, click: Click) {
        super.onClick(ctx, click)
        if (ctx.screen !in app.shelves) return
        val window = click.source?.windowId ?: -1
        val items = click.text.map(::plain).filter { it.isNotEmpty() }
        // 표지 보기 칸: 글자가 반납일·진행률뿐(제목이 없다). 진행률이 있어야 칸이다(탭·단추가 아니다)
        val grid = click.source != null && items.any { percent(it) >= 0 } && items.all { percent(it) >= 0 || STATUS_ONLY.matches(it) }
        if (!grid) {
            // 서재 화면 자체에서 다른 것을 눌렀으면 잊는다(위에 뜬 받기·안내 창의 단추는 그 칸을 열려는 것이라 둔다)
            if (gridClick?.window == window) gridClick = null
            return
        }
        val dueText = items.firstOrNull { STATUS_ONLY.matches(it) }.orEmpty()
        val due = dueDate(dueText)
        // 기간이 지난 책은 열리지 않는다
        gridClick = if (due in 0 until LocalDate.now().toEpochDay()) null
        else GridClick(due, KyoboGridCell.key(click.source!!.bounds(), dueText), ctx.now, window)
    }

    override fun onViewer(ctx: AppContext) {
        val grid = gridClick?.takeIf { g -> ctx.viewerOpenedAt?.let { it >= g.at && it - g.at < CLICK_OPEN_MS } == true }
            ?: return super.onViewer(ctx)
        val shown = readShown(ctx) ?: return
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        val title = shown.title ?: return recordShown(ctx, shown)
        gridClick = null
        ctx.opened(Seen(title, shown.author, shown.progress, grid.due), ctx.unknownOpenAt, coverKey = grid.key)
    }

    /**
     * 찍은 뒤 다시 본다: 표지 보기 칸(열쇠 자리)은 그 칸이 아직 같은 자리에 받는 중이 아닌 채로 있을 때만(바탕은 제목 칸만 보아 제목 없는 칸을 모두 버린다).
     * 제목 칸(목록 보기)은 바탕 그대로
     */
    override fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> {
        val (grid, titled) = spots.partition { it.key != null }
        if (grid.isEmpty()) return super.stillSpots(ctx, spots)
        val now = ctx.root?.let { root -> kyobo.gridCells(root).map { it.key to it.thumb } }.orEmpty().toSet()
        return super.stillSpots(ctx, titled) + grid.filter { (it.key to it.rect) in now }
    }

    /** 누른 칸 뒤에 읽는 화면이 열렸다가 제목 없이 서재로 돌아왔다: 그 칸은 버린다 */
    private fun dropUsedClick(ctx: AppContext) {
        val g = gridClick ?: return
        if (ctx.viewerOpenedAt?.let { it >= g.at } == true || ctx.now - g.at >= CLICK_OPEN_MS) gridClick = null
    }
}

/** 읽는 화면에서 쉬지 않고 내용 바뀜을 보내는 요소 종류(본문 웹 화면, 창 맨 바깥 묶음) */
private val BUSY_VIEWER_CLASSES = setOf("android.webkit.WebView", "android.widget.FrameLayout")

/** 표지 위에 겹치는 받기 묶음(받기 단추·진행 원·멈춤 단추) */
private val COVER_OVERLAYS = listOf("bookshelf_main_grid_item_download_layout")
