package com.woody.pebbledesk.readers

import com.woody.pebbledesk.BookLookup
import com.woody.pebbledesk.BookRecorder
import com.woody.pebbledesk.BookShelf
import com.woody.pebbledesk.OpenSteps
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.Target

/**
 * 어댑터 시험(가짜 앱 문맥)의 가짜 기록기: 아무것도 적지 않고 묻는 말에만 정한 대로 답한다.
 * 편 책은 시험의 [AppContext.opened] 로만 받으므로 여기로 오면 실패한다.
 * [pendingTitles]: 제목 자리의 잘라 둔 표지가 있다고 답한다(서재 칸의 표지를 찍지 않게), [covers]: 목록의 책에 표지가 있다고 답한다,
 * [openedTitle]: 편 책 기록의 제목(비었거나 null 이면 책 모름), [byTitle]: 제목으로 찾은 책.
 * 더 적어야 하는 시험은 상속해 그 메서드만 덮는다.
 */
internal open class FakeRecorder(
    private val pendingTitles: Boolean = false,
    private val covers: Boolean = false,
    private val openedTitle: () -> String? = { null },
    private val byTitle: (String) -> ShelfBook? = { null },
) : BookRecorder {
    override fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook = error("ctx 로만")
    override fun unknown(pkg: String, at: Long) {}
    override fun status(bookId: String, progress: Int, due: Long, author: String, chapter: String) {}
    override fun statuses(list: List<BookShelf.Status>) {}
    override fun setFile(bookId: String, path: String) {}
    override fun setTitle(bookId: String, title: String) {}
    override fun setAuthor(bookId: String, author: String) {}
    override fun cover(bookId: String, jpeg: ByteArray) {}
    override fun pendingCover(key: String, jpeg: ByteArray) {}
    override fun hasPendingCover(key: String) = false
    override fun hasPendingTitle(title: String) = pendingTitles
    override fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean) {}
    override fun openedId(pkg: String): String? = null
    override fun recordedBook(pkg: String): ShelfBook? =
        openedTitle()?.takeIf { it.isNotEmpty() }?.let { ShelfBook("id-$it", it, "", null, 0) }
    override val lookup: BookLookup = object : BookLookup {
        override fun byTitle(title: String): ShelfBook? = this@FakeRecorder.byTitle(title)
        override fun get(id: String): ShelfBook? = null
        override fun bookOf(id: String): ShelfBook? = null
        override fun byFile(path: String): String? = null
        override fun byPath(path: String): String? = null
        override fun withoutFile(title: String): String? = null
        override fun knownTitle(path: String, title: String) = title
        override fun idsByFileName(name: String) = emptySet<String>()
        override fun filePath(id: String): String? = null
        override fun hasCover(id: String) = covers
    }
}
