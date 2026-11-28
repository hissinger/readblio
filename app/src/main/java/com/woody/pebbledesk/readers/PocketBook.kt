package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * PocketBook reader(Pocketbook International, 내 파일·PocketBook Cloud·스토어 책을 여는 리더. 5.66.2 에뮬에서 확인).
 * 서재는 두 액티비티: 홈 탭(`HomeActivity`)의 'Last opened book' 칸과 Books 탭(`LibraryActivity`) 목록 보기 칸에
 * 제목 `tv_title`·저자 `tv_authors`·진행률 `tv_progress`(`20%`, 막 연 책은 빈 글자)·표지 `thumbnail`. 홈의 'Last added books' 줄과
 * Books 탭 격자 보기는 칸에 표지만 있어(제목 글자 없음) 읽는 화면 메뉴의 제목으로 안다.
 * 읽는 화면(`com.obreey.bookviewer.ReaderActivity`, 책 종류와 상관없이 하나)은 접근성 요소가 `Reading screen` 하나뿐이고,
 * 가운데를 눌러 띄우는 메뉴에 제목 `title_view_book`·진행률 `tv_progress`(`(5%)`)·쪽 `tv_pages`(`1 / 20`).
 * 책 DB(`books.db`)는 안드로이드 11 부터 앱 전용 폴더라 읽지 않고, 칸에도 파일 경로가 없어 표지는 화면에서 자르고 홈에서는 앱을 연다
 * (특정 책 열기 `LauncherBookOpenActivity` 는 PocketBook 안의 책 번호가 있어야 한다).
 */
object PocketBook : ReaderApp(
    pkg = "com.obreey.reader", classPrefix = "com.obreey",
    shelves = setOf("com.obreey.bookshelf.ui.home.HomeActivity", "com.obreey.bookshelf.ui.library.LibraryActivity"),
    viewers = setOf("com.obreey.bookviewer.ReaderActivity"),
    titles = listOf("tv_title"), thumbs = listOf("thumbnail"), authors = listOf("tv_authors"), progress = "tv_progress", due = null,
    viewerProgress = listOf("tv_progress", "tv_pages"), viewerTitle = "title_view_book",
    eagerCovers = true,
) {
    /** 홈을 잠깐만 보고 'Last opened book' 칸을 누르기도 해서 바로 자르고([eagerCovers]), 목록이 밀리는 중에 찍힌 칸은 자르지 않는다 */
    override val recheckCovers get() = true

    /**
     * 표지 자리는 `thumbnail` 안의 그림(`android:id/icon`): `thumbnail` 묶음은 둘레에 흰 여백이 있다.
     * 그림을 못 찾으면 `thumbnail` 자리 그대로.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        val c = super.readCell(cell) ?: return null
        return c.copy(thumb = c.thumb?.let { cell.coverImage()?.bounds() ?: it })
    }

    /** 칸 안의 표지 그림(`android:id/icon`) */
    private fun AccessibilityNodeInfo.coverImage(depth: Int = 0): AccessibilityNodeInfo? {
        if (viewIdResourceName == COVER_IMAGE) return this
        if (depth >= WALK_DEPTH) return null
        for (i in 0 until childCount) getChild(i)?.coverImage(depth + 1)?.let { return it }
        return null
    }

    private const val COVER_IMAGE = "android:id/icon"
}
