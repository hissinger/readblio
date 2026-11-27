package com.woody.pebbledesk

import android.content.Context
import com.woody.pebbledesk.Db.rows
import com.woody.pebbledesk.Db.tx
import java.util.UUID

/** 사용자가 만든 카테고리 하나. [excluded] 면 통계(홈 하단·독서 기록·올해 읽은 책·결산의 `전체`)에서 뺀다. */
data class Category(val id: Long, val name: String, val excluded: Boolean)

/**
 * 카테고리(DB `category`)와 책마다 붙인 카테고리(`book_category`, 책 하나에 없거나 여럿). 붙이지 않은 책은 `분류 없음`.
 * 책은 책 상세의 `카테고리` 에서 붙이고, 카테고리는 설정 › 독서 › 카테고리([CategoriesActivity])에서 만들고 고친다.
 * 통계에서 거르는 것은 [StatsFilter].
 */
object Categories {
    /** 보기 거르기에서 `분류 없음`(카테고리를 붙이지 않은 책) */
    const val NONE = -1L

    private class Snapshot(val db: android.database.sqlite.SQLiteDatabase, val revision: Long, val list: List<Category>, val books: Map<String, Set<Long>>)

    @Volatile private var cache: Snapshot? = null

    /** 지금 카테고리와 책마다 붙인 것(기록이 바뀌지 않았으면 읽어 둔 것) */
    private fun load(context: Context): Snapshot {
        val revision = Db.changes()
        val db = Db.get(context)
        cache?.takeIf { it.revision == revision && it.db === db }?.let { return it }
        val list = db.rows("SELECT id, name, excluded FROM category ORDER BY pos, id") { Category(it.getLong(0), it.getString(1), it.getInt(2) != 0) }
        val ids = list.map { it.id }.toSet()
        // 지운 카테고리를 가리키는 줄은 없지만(지울 때 함께 지운다) 있어도 빼고 본다(남는 것이 없으면 `분류 없음`)
        val books = HashMap<String, MutableSet<Long>>()
        db.rows("SELECT book_id, category_id FROM book_category") { it.getString(0) to it.getLong(1) }
            .forEach { (book, c) -> if (c in ids) books.getOrPut(book) { HashSet() } += c }
        return Snapshot(db, revision, list, books).also { cache = it }
    }

    /** 테스트: 읽어 둔 것을 버린다(DB 를 새로 열 때) */
    internal fun clearCache() { cache = null }

    /** 카테고리(정한 순서) */
    fun list(context: Context): List<Category> = load(context).list

    /** 책 id → 붙인 카테고리 id 들(하나도 없는 책은 없다) */
    fun books(context: Context): Map<String, Set<Long>> = load(context).books

    /** [book] 에 붙인 카테고리(카테고리 순서, 없으면 빈 목록) */
    fun of(context: Context, book: String): List<Category> = load(context).let { s -> s.books[book]?.let { ids -> s.list.filter { it.id in ids } }.orEmpty() }

    /** 이름을 다듬는다(앞뒤 빈칸, 너무 길면 자른다) */
    private fun clean(name: String) = name.trim().take(MAX_NAME)

    /** 같은 이름(대소문자 무시)의 카테고리. [name] 은 다듬은 이름. */
    private fun named(context: Context, name: String) = list(context).firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** [name] 을 저장할 때처럼 다듬으면 다른 카테고리([except] 빼고)와 이름이 같은가(입력 칸에서 저장 전에 알린다) */
    fun sameName(context: Context, name: String, except: Long? = null): Boolean = named(context, clean(name))?.let { it.id != except } == true

    /** 새 카테고리(맨 뒤). 같은 이름이 있으면 그것을 돌려준다. 빈 이름이면 null. */
    fun add(context: Context, name: String): Category? {
        val n = clean(name)
        if (n.isEmpty()) return null
        named(context, n)?.let { return it }
        val id = Db.get(context).tx {
            val pos = rows("SELECT COALESCE(MAX(pos), -1) + 1 FROM category") { it.getLong(0) }.first()
            insertOrThrow("category", null, Db.values(
                "uid" to UUID.randomUUID().toString().replace("-", ""), "name" to n, "pos" to pos, "excluded" to false,
            ))
        }
        cache = null
        return list(context).firstOrNull { it.id == id }
    }

    /** 이름 바꾸기. 빈 이름이거나 다른 카테고리와 같은 이름이면 바꾸지 않는다(false). */
    fun rename(context: Context, id: Long, name: String): Boolean {
        val n = clean(name)
        if (n.isEmpty() || sameName(context, n, except = id)) return false
        update(context, id, "name" to n)
        return true
    }

    /** 통계에서 빼기([excluded])·다시 넣기 */
    fun setExcluded(context: Context, id: Long, excluded: Boolean) = update(context, id, "excluded" to excluded)

    private fun update(context: Context, id: Long, value: Pair<String, Any>) {
        Db.get(context).tx { update("category", Db.values(value), "id = ?", arrayOf(id.toString())) }
        cache = null
    }

    /** 순서를 [delta] 칸 옮긴다(목록 끝에서는 그대로) */
    fun move(context: Context, id: Long, delta: Int) {
        val order = list(context).map { it.id }.toMutableList()
        val i = order.indexOf(id)
        val j = i + delta
        if (i < 0 || j !in order.indices) return
        order.add(j, order.removeAt(i))
        Db.get(context).tx {
            order.forEachIndexed { pos, c -> update("category", Db.values("pos" to pos), "id = ?", arrayOf(c.toString())) }
        }
        cache = null
    }

    /** 카테고리를 지운다. 그 책들에서 빠지고, 다른 카테고리가 없던 책은 `분류 없음`이 된다(읽은 기록은 그대로). */
    fun delete(context: Context, id: Long) {
        Db.get(context).tx {
            delete("book_category", "category_id = ?", arrayOf(id.toString()))
            delete("category", "id = ?", arrayOf(id.toString()))
        }
        cache = null
    }

    /** [book] 의 카테고리를 꼭 [categories] 로(비면 모두 뗀다. 없는 카테고리는 붙이지 않는다) */
    fun assign(context: Context, book: String, categories: Set<Long>) {
        val ids = list(context).map { it.id }.toSet()
        Db.get(context).tx {
            delete("book_category", "book_id = ?", arrayOf(book))
            categories.filter { it in ids }.forEach { insertOrThrow("book_category", null, Db.values("book_id" to book, "category_id" to it)) }
        }
        cache = null
    }

    /**
     * 여러 책([books])에 [add] 를 붙이고 [remove] 를 뗀다(올해 읽은 책의 편집, 한 트랜잭션). 다른 카테고리는 그대로, 없는 카테고리는 붙이지 않는다.
     * 바뀐 책 수.
     */
    fun change(context: Context, books: Collection<String>, add: Set<Long>, remove: Set<Long>): Int {
        val ids = list(context).map { it.id }.toSet()
        val now = books(context)
        var changed = 0
        Db.get(context).tx {
            for (book in books.distinct()) {
                val before = now[book].orEmpty()
                val after = (before + add.filter { it in ids }) - remove
                if (after == before) continue
                (before - after).forEach { delete("book_category", "book_id = ? AND category_id = ?", arrayOf(book, it.toString())) }
                (after - before).forEach { insertOrThrow("book_category", null, Db.values("book_id" to book, "category_id" to it)) }
                changed++
            }
        }
        cache = null
        return changed
    }

    /**
     * 카테고리마다 붙은 책 수(읽고 있는 책과 기록에서 지우지 않은 나간 책만. 여러 카테고리의 책은 그 카테고리마다 센다)와
     * 그중 그 카테고리 하나만 붙은 책 수(지우면 `분류 없음`이 되는 책), 통계에서 뺀 카테고리면 그것 말고는 통계에서 뺀 카테고리가 없는 책 수
     * (지우면 읽은 시간이 통계에 나오는 책)
     */
    fun bookCounts(context: Context): Map<Long, BookCount> {
        val alive = BookShelf.list(context).map { it.id }.toSet() + BookShelf.history(context).filter { !it.hidden }.map { it.book.id }
        val excluded = list(context).filter { it.excluded }.map { it.id }.toSet()
        val counts = HashMap<Long, BookCount>()
        books(context).filterKeys { it in alive }.values.forEach { cs ->
            val out = cs.count { it in excluded }
            cs.forEach { c ->
                val n = counts[c] ?: BookCount(0, 0, 0)
                counts[c] = BookCount(n.books + 1, n.only + if (cs.size == 1) 1 else 0, n.counted + if (c in excluded && out == 1) 1 else 0)
            }
        }
        return counts
    }

    /** [bookCounts] 의 한 카테고리: 붙은 책 [books], 그중 이 카테고리만 붙은 책 [only], 지우면 통계에 나오는 책 [counted] */
    data class BookCount(val books: Int, val only: Int, val counted: Int)

    /** 카테고리 이름 길이 한도 */
    private const val MAX_NAME = 30
}

/**
 * 여러 권 카테고리 메뉴(올해 읽은 책의 편집, Gmail 라벨처럼)의 줄 상태. 처음에는 고른 [total] 권 중 붙은 수([counts])로
 * 모두([Mark.ALL]) · 일부([Mark.SOME]) · 없음([Mark.NONE]). 누르면 일부 → 모두 → 없음 → 일부(처음에 일부였던 줄만 일부로 돌아온다),
 * 처음부터 모두·없음이던 줄은 둘 사이만. `확인` 하면 바꾼 줄만: 모두 = 고른 책 모두에 붙이고([add]), 없음 = 모두에서 떼고([remove]), 일부 = 그대로.
 */
class BulkCategories(val total: Int, private val counts: Map<Long, Int>) {
    enum class Mark { ALL, SOME, NONE }

    private val now = HashMap<Long, Mark>()

    /** 고른 책 중 [id] 가 붙은 수(처음) */
    fun count(id: Long): Int = counts[id] ?: 0

    /** 처음 상태 */
    fun initial(id: Long): Mark = count(id).let { n -> if (n <= 0) Mark.NONE else if (n >= total) Mark.ALL else Mark.SOME }

    /** 지금 상태 */
    fun mark(id: Long): Mark = now[id] ?: initial(id)

    fun toggle(id: Long) {
        now[id] = when (mark(id)) {
            Mark.SOME -> Mark.ALL
            Mark.ALL -> Mark.NONE
            Mark.NONE -> if (initial(id) == Mark.SOME) Mark.SOME else Mark.ALL
        }
    }

    /** 새로 만든 카테고리는 켜진 채로(모두에 붙임) */
    fun set(id: Long, mark: Mark) { now[id] = mark }

    /** 모두에 붙일 것(처음에 모두가 아니던 줄을 모두로) */
    val add: Set<Long> get() = now.filter { (id, m) -> m == Mark.ALL && initial(id) != Mark.ALL }.keys

    /** 모두에서 뗄 것(처음에 없음이 아니던 줄을 없음으로) */
    val remove: Set<Long> get() = now.filter { (id, m) -> m == Mark.NONE && initial(id) != Mark.NONE }.keys

    companion object {
        /** 책들([books])에 붙은 카테고리로 */
        fun of(context: Context, books: Collection<String>): BulkCategories {
            val all = Categories.books(context)
            val counts = HashMap<Long, Int>()
            books.distinct().forEach { b -> all[b].orEmpty().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
            return BulkCategories(books.distinct().size, counts)
        }
    }
}

/**
 * 통계 거르기. [picked] 가 비면 `전체`: 통계에서 뺀 카테고리의 책만 빼고 모두(어떤 책인지 모르는 시간도 든다).
 * 아니면 고른 카테고리([Categories.NONE] 은 `분류 없음`)의 책만(어떤 책인지 모르는 시간은 뺀다. 제목만 아는 책은 `분류 없음`).
 * 거르기는 책마다다: 카테고리가 여럿인 책은 그중 하나라도 고르면 들고(둘 다 골라도 한 번), `전체` 에서는 하나라도 통계에서 뺐으면 빠진다.
 * 없어진 카테고리를 고른 것은 무시하고, 남는 것이 없거나 [selectable] 을 꼭 그만큼 고른 것이면 `전체`.
 */
class StatsFilter(picked: Set<Long>, val categories: List<Category>, private val books: Map<String, Set<Long>>) {
    private val excluded = categories.filter { it.excluded }.map { it.id }.toSet()

    /** 보기 메뉴에서 `전체`를 한 번 더 누르면 켜는 것: 통계에서 빼지 않은 카테고리와 `분류 없음`(꼭 이것만 고르면 `전체`와 같게 본다) */
    val selectable: Set<Long> = categories.map { it.id }.filter { it !in excluded }.toSet() + Categories.NONE

    val picked: Set<Long> = picked.filter { it == Categories.NONE || categories.any { c -> c.id == it } }.toSet()
        .takeIf { it != selectable }.orEmpty()

    /** `전체` */
    val all get() = picked.isEmpty()

    /** 거를 것이 없다(`전체`이고 통계에서 뺀 카테고리에 붙은 책이 없다): 기록을 그대로 쓴다 */
    private val passThrough = all && books.values.none { cs -> cs.any { it in excluded } }

    /** 책 [id] 가 드는가(목록에 없던 책·제목만 아는 책은 `분류 없음`) */
    fun keepsBook(id: String): Boolean {
        val cs = books[id].orEmpty()
        return when {
            all -> cs.none { it in excluded }
            cs.isEmpty() -> Categories.NONE in picked
            else -> cs.any { it in picked }
        }
    }

    /** 읽은 시간 한 칸이 드는가: 책을 알면 그 책으로, 제목만 알면 `분류 없음`, 아무것도 모르면 `전체`에만 */
    fun keeps(k: ReadKey): Boolean = when {
        k.book.isNotEmpty() -> keepsBook(k.book)
        all -> true
        else -> k.title.isNotEmpty() && Categories.NONE in picked
    }

    /** 거른 날마다 기록(날은 그대로 두고 칸만 거른다: 기록이 있는 날이 바뀌지 않게) */
    fun days(days: ReadingDays): ReadingDays = if (passThrough) days else days.mapValues { (_, m) -> m.filterKeys(::keeps) }

    /** 같은 카테고리·책으로 다른 것을 고른 거르기 */
    fun with(picked: Set<Long>) = StatsFilter(picked, categories, books)

    companion object {
        /** 지금 카테고리로 [picked] 를 고른 거르기(비면 `전체`. 홈 하단은 늘 `전체`) */
        fun of(context: Context, picked: Set<Long> = emptySet()) = StatsFilter(picked, Categories.list(context), Categories.books(context))
    }
}
