package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.YearMonth

/**
 * ⑬ 올해 읽은 책(독서 기록 하단 오른쪽 `올해 읽은 책 ›`). 하단 햄버거 › `년도` 로 지난해를 고르면 `2025년 읽은 책`.
 * 그해 합쳐 5분 이상 읽었거나 그해 완독한 책을 모두 보인다(직접 넣은 책은 시간이 붙지 않아 회차가 걸친 해, [BookRecord.readYears]):
 * 읽고 있는 책·완독한 책·뺀 책·목록에 넣지 못한 책(읽은 시간에만 있는 제목). 최근에 읽은(다 읽은) 순.
 * 목록은 줄마다 표지·제목·저자와 별점(완독한 책은 오른쪽 끝에 도장, 읽는 중 표시는 없다), 격자는 한 줄에 표지 4권과 그 아래 책 이름·별점.
 * 한 번이라도 완독한 책은 표지가 어디에 놓이든(격자·목록·상세) 오른쪽 위 모서리를 가로지르는 `완독` 띠. 완독한 날은 모두 남고
 * 상세에 회차마다 기간이 나온다. 다시 읽어도 완독 기록은 그대로다.
 * 하단 왼쪽 `목록 · 격자`, 가운데 보기 거르기 `전체 ▾`(보기 메뉴: 읽은 책 `전체`·`완독만` + 카테고리, 기억한다), 오른쪽 햄버거
 * (`월 독서 결산 ›`(지난해를 보고 있으면 그해 12월, ⑮ [RecapActivity]) · `년도  2026 ›` · `편집`). 책을 누르면 홈과 같은 책 상세([showBookDetail]).
 * 여러 쪽이면 쪽 표시는 제목 줄 오른쪽 끝.
 * 편집(햄버거 › `편집`, 또는 책을 길게 누름): 책마다 체크 칸, 누르면 그 칸만 바꾼다. 제목 `3권 고름`(0권이면 `편집`) · 오른쪽 `19권 모두 고르기`,
 * 하단 `취소` · `카테고리 ›`(여러 권 카테고리, [BulkCategories]) · `지우기`(여러 권 `Readblio에서 지우기`). 쪽을 넘겨도 고른 것은 그대로.
 */
class YearBooksActivity : EinkActivity() {
    /** 목록·격자(줄 높이가 달라 따로 두고 하나만 보인다) */
    private lateinit var listRows: PagedRows
    private lateinit var gridRows: PagedRows
    private val rows get() = if (prefs.yearGrid) gridRows else listRows
    private lateinit var pageText: TextView
    private lateinit var viewToggle: TextView
    /** 하단 가운데 보기 거르기 `전체 ▾` · `소설 · 완독 ▾` */
    private lateinit var filterButton: TextView
    private lateinit var empty: TextView
    /**
     * 격자 칸 폭·한 줄 권수·칸 사이([coverGrid]). 세로 화면은 [COLUMNS]권. 가로 화면은 표지를 세로 화면과 같은 크기로 두고 들어가는 만큼 늘리며,
     * 남는 폭은 칸 사이에 나눈다(페블 5권, 바형 8권).
     */
    private lateinit var fit: CoverGrid
    /** 목록 한 줄의 책 수: 가로로 넓어 한 칸이 [LIST_COLUMN_DP] 넘게 남으면 두 권씩 나란히(바형), 아니면 한 권 */
    private var listColumns = 1
    /** 격자 표지 크기: 칸 높이는 그대로 두고 아래 별 줄만큼 줄인다(2:3) */
    private var gridCoverW = 0
    private var gridCoverH = 0
    /** 보고 있는 해(처음에는 올해). 햄버거 › `년도` 로 고른다. */
    private var year = LocalDate.now().year
    private lateinit var titleText: TextView
    /** 제목 옆 권수(모든 앱의 앱 수와 같은 꼴). 지금 거른 목록의 수 */
    private lateinit var titleCount: TextView
    /** 편집: 제목 줄 오른쪽 `19권 모두 고르기`(다 고르면 `모두 풀기`) */
    private lateinit var selectAll: TextView
    /** 제목 줄(쪽 표시가 다 들지 않으면 글자를 줄인다) */
    private lateinit var titleBar: android.widget.LinearLayout
    /** 하단 줄: 평소(목록·격자 · 보기 거르기 · 햄버거)와 편집(취소 · 카테고리 · 지우기) */
    private lateinit var normalFooter: View
    private lateinit var editFooter: View
    private lateinit var editCategory: TextView
    private lateinit var editDelete: TextView
    /** 고를 수 있는 해(최근 순)와 지금 목록(거른 것) */
    private var years: List<Int> = emptyList()
    private var list: List<Row> = emptyList()
    /** 편집 중인가, 고른 책(줄의 책 id, 목록에 없던 책은 제목으로 만든 임시 id) */
    private var editing = false
    private val picked = LinkedHashSet<String>()
    /** 지금 쪽에 그려 둔 체크 칸(누른 칸만 다시 그린다) */
    private val marks = HashMap<String, CheckMark>()

    /** 한 줄: 책의 기록, 그해 마지막으로 읽은 날과 완독한 날 중 늦은 날(목록 순서), 그해 완독했는가(`완독` 거르기) */
    internal class Row(val record: BookRecord, val sortDay: LocalDate, val doneInYear: Boolean) {
        val book get() = record.book
        val finished get() = record.finished
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        // `‹` 는 뒤로 가기와 같다: 편집 중이면 편집만 끝낸다(제목이 `‹ 3권 고름`)
        root.addView(titleRow("", Palette.Area.LIBRARY) { if (editing) endEdit() else finish() }.apply {
            // 제목 줄: `‹` · 제목 · 권수 · (빈 자리) · (편집이면 `19권 모두 고르기`) · 쪽 `‹ 1 / 2 ›`(여러 쪽일 때, 오른쪽 끝)
            titleText = getChildAt(1) as TextView
            titleCount = text("", 21f, color = Ui.GRAY).apply { setPadding(dp(14), dp(20), dp(8), dp(20)) }
            addView(titleCount, lp(WRAP, WRAP).apply { topMargin = dp(6) })
            addView(View(context), lp(0, 0, 1f))
            selectAll = text("", 17f).apply {
                setPadding(dp(16), dp(20), 0, dp(20))
                visibility = View.GONE
                setOnClickListener { toggleAll() }
            }
            addView(selectAll, lp(WRAP, WRAP))
            pageText = text("", 21f).apply {
                setPadding(dp(16), dp(20), 0, dp(20))
                pagesClick { rows }
            }
            addView(pageText, lp(WRAP, WRAP))
            titleBar = this
        })
        root.addView(hline(2))
        empty = text(getString(R.string.year_books_empty), 18f, color = Ui.GRAY, lines = 3).apply {
            setPadding(dp(Ui.MARGIN), dp(40), dp(Ui.MARGIN), 0)
        }
        root.addView(empty)
        // 표지 크기는 세로 화면(짧은 변) 기준, 가로면 권수를 늘린다
        fit = coverGrid(COLUMNS, GAP_DP)
        val inner = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN)
        listColumns = if (inner >= 2 * dp(LIST_COLUMN_DP) + 2 * dp(Ui.MARGIN)) 2 else 1
        // 글자를 키우면 줄·격자 글자 칸도 그만큼 높인다(줄이면 고정 크기 표지가 잘리므로 100% 아래로는 그대로).
        listRows = PagedRows(this, rowDp(ROW_DP * Ui.textGrow))
        gridRows = PagedRows(this, fit.cellW * 3 / 2 + dp(GRID_TEXT_DP * Ui.textGrow), dividers = false)
        gridCoverH = fit.cellW * 3 / 2 - dp(GRID_STARS_DP)
        gridCoverW = gridCoverH * 2 / 3
        val pages = FrameLayout(this)
        pages.addView(listRows, FrameLayout.LayoutParams(MATCH, MATCH))
        pages.addView(gridRows, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(pages, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footers = FrameLayout(this)
        val footer = FrameLayout(this)
        viewToggle = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { prefs.yearGrid = !prefs.yearGrid; render() }
        }
        footer.addView(viewToggle, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        // 가운데 보기 거르기 `전체 ▾`: 읽은 책(전체·완독만)과 카테고리(기억한다)
        filterButton = filterButton()
        footer.addView(filterButton, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER_HORIZONTAL))
        // 오른쪽 햄버거: 월 독서 결산 · 년도 · 편집
        val menuButton = MenuIcon(this).apply {
            contentDescription = getString(R.string.menu)
            setOnClickListener { showYearMenu() }
        }
        footer.addView(menuButton, FrameLayout.LayoutParams(dp(MenuIcon.TOUCH_DP + Ui.MARGIN), MATCH, Gravity.END))
        normalFooter = footer
        footers.addView(footer, FrameLayout.LayoutParams(MATCH, MATCH))
        // 편집: `취소` · `카테고리 ›` · `지우기`(아무것도 고르지 않았으면 흐리게)
        editFooter = FrameLayout(this).apply {
            visibility = View.GONE
            addView(text(getString(R.string.cancel), 21f).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
                setOnClickListener { endEdit() }
            }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
            editCategory = text(getString(R.string.edit_category), 21f, Ui.bold).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(24), 0)
                setOnClickListener { if (picked.isNotEmpty()) bulkCategories() }
            }
            addView(editCategory, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER_HORIZONTAL))
            editDelete = text(getString(R.string.edit_delete), 21f, Ui.bold).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
                setOnClickListener { if (picked.isNotEmpty()) bulkForget() }
            }
            addView(editDelete, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        }
        footers.addView(editFooter, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(footers, lp(MATCH, dp(Ui.FOOTER_DP)))
        for (r in listOf(listRows, gridRows)) r.onPageChanged = { if (r === rows) showPage() }
        setContentView(root)
    }

    /** 뒤로 가기: 편집 중이면 편집만 끝낸다 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (editing) endEdit() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    override fun onResume() {
        super.onResume()
        recordState().let { if (it != shown) render(it) }
    }

    /**
     * 지금 그려 둔 기록의 표(오늘·DB 순번·오늘 책별 시간). 돌아왔을 때 같으면(화면만 껐다 켬, 책 상세에서 아무것도 바꾸지 않음) 기록을 다시 모아
     * 목록을 다시 만들지 않는다(같은 화면을 다시 그려 깜빡이지 않게). 보기·거르기·해 바꾸기는 [render] 를 바로 부른다.
     */
    private var shown: List<Any?>? = null

    private fun recordState(): List<Any?> {
        val today = LocalDate.now()
        // 세기가 날이 바뀐 지난날을 저장하므로 DB 순번은 센 뒤에 본다
        val todayMs = ReadingLog.days(this, ReadingLog.readerPackages(this))?.get(today)
        return listOf(today, Db.changes(), todayMs)
    }

    private fun showPage() {
        pageText.text = pageLabel(rows)
        // 한 쪽이면 자리(왼쪽 여백)도 비운다
        pageText.visibility = if (pageText.text.isEmpty()) View.GONE else View.VISIBLE
        // 제목 줄에 다 들지 않으면(좁은 화면·키운 글자·영어 제목, 편집의 `모두 고르기`) 쪽 표시를 줄인다. 쪽·해가 바뀌면 다시 재므로 처음 크기부터.
        pageText.setSp(21f)
        fitTitleLink(titleBar, pageText)
    }

    /** [state] 는 이미 센 [recordState](돌아왔을 때) */
    private fun render(state: List<Any?> = recordState()) {
        shown = state
        val today = LocalDate.now()
        // 기록은 한 번만 모은다(읽은 시간 계산이 사용 기록을 읽는다. 위 [recordState] 가 센 뒤라 여기서는 그 뒤의 것만 이어 센다).
        val records = BookRecord.all(this).filter { !it.hidden }
        years = years(records, today)
        // 지난해의 마지막 책을 기록에서 지워 그해가 비면 올해로 돌아온다(빈 지난해에 `올해 읽은 책이 없습니다`가 뜨지 않게).
        if (year !in years) year = today.year
        val yearAll = yearRows(records, year)
        // 보기 거르기: 고른 카테고리의 책만(`전체`면 통계에서 뺀 카테고리의 책만 빼고). 보기 메뉴의 권수도 `완독만`을 따른다.
        val filter = StatsFilter.of(this, prefs.statsPicked(HomePrefs.StatsScreen.YEAR))
        val all = yearAll.filter { filter.keepsBook(it.book.id) }
        val finishedOnly = prefs.yearFinishedOnly
        list = if (finishedOnly) all.filter { it.doneInYear } else all
        // 편집 중 목록에서 사라진 책(다른 화면에서 지움)은 고른 것에서 뺀다
        if (editing) picked.retainAll(list.map { it.book.id }.toSet())
        titleText.text = if (year == today.year) getString(R.string.year_books) else getString(R.string.year_books_of, year)
        titleCount.text = if (list.isEmpty()) "" else getString(R.string.year_books_count, list.size)
        // 하단 가운데 보기 거르기: `전체 ▾` · `완독 ▾` · `소설 ▾` · `소설 · 완독 ▾`
        filterButton.text = listOfNotNull(
            filterName(filter).takeIf { !filter.all || !finishedOnly },
            getString(R.string.finish_book).takeIf { finishedOnly },
        ).joinToString(getString(R.string.year_filter_join)) + " ▾"
        filterButton.setOnClickListener {
            // 권수: 고른 카테고리·`완독만` 으로 그해 책을 센다(메뉴 안에서 고르는 대로 다시 센다)
            showStatsFilter(filter, getString(R.string.filter_year_sub, year), { f, done ->
                getString(R.string.year_books_count, yearAll.count { f.keepsBook(it.book.id) && (!done || it.doneInYear) })
            }, finishedOnly) { cats, done ->
                prefs.setStatsPicked(HomePrefs.StatsScreen.YEAR, cats)
                prefs.yearFinishedOnly = done
                render(); rows.showPage(0)
            }
        }
        empty.text = getString(when {
            yearAll.isEmpty() -> R.string.year_books_empty
            all.isEmpty() -> R.string.year_category_empty
            else -> R.string.year_finished_empty
        })
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val grid = prefs.yearGrid
        viewToggle.text = toggleText(R.string.fav_view_list, R.string.fav_view_grid, !grid)
        // 그해 책이 하나도 없으면 보기·거르기를 감춘다(거른 결과가 비면 되돌릴 수 있게 둔다).
        viewToggle.visibility = if (yearAll.isEmpty()) View.GONE else View.VISIBLE
        filterButton.visibility = viewToggle.visibility
        // 편집 중이면 하단은 취소·카테고리·지우기, 제목은 고른 권수
        if (editing && list.isEmpty()) editing = false
        normalFooter.visibility = if (editing) View.GONE else View.VISIBLE
        editFooter.visibility = if (editing) View.VISIBLE else View.GONE
        titleCount.visibility = if (editing) View.GONE else View.VISIBLE
        selectAll.visibility = if (editing) View.VISIBLE else View.GONE
        if (editing) showPicked()
        marks.clear()
        listRows.visibility = if (grid) View.GONE else View.VISIBLE
        gridRows.visibility = if (grid) View.VISIBLE else View.GONE
        if (grid) gridRows.setRows(list.chunked(fit.columns).map { line -> { gridRow(line) } })
        else if (listColumns > 1) listRows.setRows(list.chunked(listColumns).map { line ->
            {
                // 두 권씩 나란히(왼쪽에서 오른쪽, 위에서 아래로). 덜 찬 줄도 칸 폭이 같게 빈 칸을 채운다.
                hbox().apply {
                    line.forEach { addView(bookRow(it), lp(0, MATCH, 1f)) }
                    repeat(listColumns - line.size) { addView(View(context), lp(0, MATCH, 1f)) }
                }
            }
        })
        else listRows.setRows(list.map { r -> { bookRow(r) } })
        showPage()
    }

    /** 두 갈래 고르기 글자 `목록 · 격자`: 지금 고른 쪽은 굵게, 다른 쪽은 흐리게 */
    private fun toggleText(first: Int, second: Int, firstOn: Boolean) = SpannableStringBuilder().apply {
        listOf(first to firstOn, second to !firstOn).forEachIndexed { i, (res, on) ->
            if (i > 0) append("  ·  ", ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
            append(getString(res), if (on) StyleSpan(Typeface.BOLD) else ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
        }
    }

    /**
     * 표지 한 줄([CoverGrid.columns]권). 표지 아래에 책 이름 한 줄(완독한 책은 표지의 띠로 안다), 별점이 있으면 그 아래 작은 별(없으면 빈 자리).
     * 표지를 가리지 않게 별 줄만큼 표지를 줄였다(칸 높이는 그대로).
     */
    private fun gridRow(line: List<Row>): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), 0)
        line.forEachIndexed { i, r ->
            addView(vbox().apply {
                val cover = coverImage(BookShelf.cover(context, r.book, gridCoverW))
                if (editing) {
                    // 편집: 표지 왼쪽 위에 흰 테두리 두른 체크 칸
                    addView(FrameLayout(context).apply {
                        addView(cover, FrameLayout.LayoutParams(MATCH, MATCH))
                        addView(mark(r), FrameLayout.LayoutParams(dp(GRID_CHECK_DP), dp(GRID_CHECK_DP)).apply { leftMargin = dp(3); topMargin = dp(3) })
                    }, lp(gridCoverW, gridCoverH).apply { gravity = Gravity.CENTER_HORIZONTAL })
                } else addView(cover, lp(gridCoverW, gridCoverH).apply { gravity = Gravity.CENTER_HORIZONTAL })
                addView(text(r.book.title, 13f, Ui.medium).apply { gravity = Gravity.CENTER_HORIZONTAL }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
                stars(r, 12)?.let { addView(it, lp(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(5) }) }
                onBook(r)
            }, lp(fit.cellW, MATCH).apply { if (i > 0) marginStart = fit.gap })
        }
    }

    /**
     * 한 줄: 표지·제목·저자와 별점. 완독한 적이 있으면 오른쪽 끝에 줄보다 큰 완독 도장(위아래·오른쪽이 조금 잘린다).
     * 표지는 줄 높이를 거의 다 채운다(위아래 [COVER_GAP_DP] 만 남기고, 폭은 2:3).
     */
    private fun bookRow(r: Row): View = (if (r.finished) StampedRow(this, STAMP_DP, STAMP_END_DP) else hbox()).apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        // 편집: 줄 왼쪽에 체크 칸
        if (editing) addView(mark(r), lp(dp(CHECK_DP), dp(CHECK_DP)).apply { marginEnd = dp(16) })
        addView(coverImage(BookShelf.cover(context, r.book, coverW)), lp(coverW + dp(2), coverW * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(r.book.title, 22f, Ui.bold, lines = 2))
            // 목록은 짧게: 저자(알면)와 별점(있으면). 완독은 표지의 띠와 도장이, 읽는 중인지·완독 횟수·기간·읽은 시간은 누르면 나오는 상세가 말한다.
            val author = r.book.author.takeIf { it.isNotBlank() }
            val stars = stars(r, 13)
            if (author != null || stars != null) addView(hbox().apply {
                author?.let {
                    // 저자가 길어도 별이 밀려나지 않게 별 자리를 뺀 만큼만 쓴다.
                    addView(text(it, 16f, color = Ui.GRAY).apply {
                        if (stars != null) maxWidth = r.textWidth() - stars.apply { measure(0, 0) }.measuredWidth - dp(DOT_W_DP)
                    })
                }
                stars?.let {
                    if (author != null) addView(text("·", 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(DOT_GAP_DP); marginEnd = dp(DOT_GAP_DP) })
                    addView(it)
                }
            }, lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }, lp(0, WRAP, 1f).apply {
            marginStart = dp(16)
            // 글자가 도장에 덮이지 않게 도장 왼쪽 끝까지 비운다.
            if (r.finished) marginEnd = stampSpace
        })
        onBook(r)
    }

    /** 줄(칸)을 누르면: 편집이면 고르기, 아니면 책 상세. 길게 누르면 그 책을 고른 채로 편집을 시작한다(편집 중이면 누른 것과 같다). */
    private fun View.onBook(r: Row) {
        setOnClickListener { if (editing) toggle(r.book.id) else showMenu(r) }
        setOnLongClickListener {
            if (editing) toggle(r.book.id) else startEdit(r.book.id)
            true
        }
    }

    /** 이 책의 체크 칸(지금 쪽에 그린 것을 기억한다) */
    private fun mark(r: Row): CheckMark = CheckMark(this).apply {
        on = r.book.id in picked
        if (prefs.yearGrid) ring = dp(3)
    }.also { marks[r.book.id] = it }

    private val stampSpace get() = dp(STAMP_END_DP + STAMP_DP / 2 - Ui.MARGIN)

    /** 목록 줄 표지 그림 폭(테두리 빼고): 줄 높이에서 위아래 [COVER_GAP_DP] 와 테두리를 뺀 높이의 2/3 */
    private val coverW get() = (rowDp(ROW_DP * Ui.textGrow) - dp(2 * COVER_GAP_DP + 2)) * 2 / 3

    /** 목록 줄 글자 칸 폭(한 권 칸 폭 - 여백 - 표지 - 도장 자리) */
    private fun Row.textWidth() = resources.displayMetrics.widthPixels / listColumns - 2 * dp(Ui.MARGIN) - coverW - dp(2) - dp(16) -
        (if (finished) stampSpace else 0) - (if (editing) dp(CHECK_DP + 16) else 0)

    /** 마지막 회차에 별점이 있으면 작은 별 다섯(회색, 반 개), 없으면 null */
    private fun stars(r: Row, sizeDp: Int): View? {
        val halves = r.record.lastRound.review?.halves ?: 0
        if (halves == 0) return null
        return StarBar(this, sizeDp, 2, filled = Ui.GRAY).apply { this.halves = halves }
    }

    /** 홈과 같은 책 상세(완독은 늘 오늘, 완독한 날은 전체 기록에서 고친다) */
    private fun showMenu(r: Row) = showBookDetail(r.record) { render() }

    /** 햄버거: `월 독서 결산 ›`(올해면 이번 달, 지난해면 그해 12월) · `년도  2026 ›` · `편집`, 맨 아래 `닫기` */
    private fun showYearMenu() {
        val today = LocalDate.now()
        val recapMonth = if (year == today.year) YearMonth.from(today) else YearMonth.of(year, 12)
        val sheet = Sheet(this)
            .item(getString(R.string.year_menu_recap)) { startActivity(RecapActivity.intent(this, recapMonth)) }
            .item(getString(R.string.year_menu_year), right = "$year  ›") {
                choose(getString(R.string.year_menu_year), years.map { it.toString() }, years.indexOf(year)) {
                    year = years[it]
                    render()
                    rows.showPage(0)
                }
            }
        if (list.isNotEmpty()) sheet.item(getString(R.string.year_menu_edit)) { startEdit(null) }
        sheet.show()
    }

    /** 편집을 시작한다([first] 를 고른 채로, 길게 누른 책). 고른 칸이 보이게 목록을 한 번 다시 그린다(쪽은 그대로). */
    private fun startEdit(first: String?) {
        editing = true
        picked.clear()
        first?.let { picked += it }
        redraw()
    }

    /** 편집을 끝낸다(고른 것을 버리고 평소 목록으로, 보던 쪽은 그대로. 카테고리·지우기를 마친 뒤도) */
    private fun endEdit() {
        if (!editing) return
        editing = false
        picked.clear()
        redraw()
    }

    /** 기록은 그대로 두고 지금 쪽을 다시 그린다(편집 시작·끝) */
    private fun redraw() {
        val page = rows.page
        render()
        rows.showPage(page)
    }

    /** 한 권 고르기·풀기: 그 칸과 제목 글자만 바꾼다(0권 ↔ 1권이면 하단 글자 색도) */
    private fun toggle(id: String) {
        if (!picked.remove(id)) picked += id
        marks[id]?.on = id in picked
        showPicked()
    }

    /** `모두 고르기`(거른 목록 전체) · `모두 풀기`: 지금 쪽의 칸을 바꾼다 */
    private fun toggleAll() {
        val ids = list.map { it.book.id }
        if (picked.containsAll(ids)) picked.clear() else picked.addAll(ids)
        marks.forEach { (id, m) -> m.on = id in picked }
        showPicked()
    }

    /** 편집 제목 `3권 고름`(0권이면 `편집`), 오른쪽 `19권 모두 고르기`, 하단 카테고리·지우기(0권이면 흐리게) */
    private fun showPicked() {
        val n = picked.size
        titleText.text = if (n == 0) getString(R.string.year_menu_edit) else getString(R.string.edit_count, n)
        val all = n > 0 && n == list.size
        selectAll.text = SpannableStringBuilder().apply {
            append(getString(R.string.year_books_count, list.size), ForegroundColorSpan(Ui.GRAY), 0)
            append("  ")
            append(getString(if (all) R.string.select_none else R.string.select_all), StyleSpan(Typeface.BOLD), 0)
        }
        val color = if (n == 0) Ui.DIVIDER else Ui.BLACK
        if (editCategory.currentTextColor != color) { editCategory.setTextColor(color); editDelete.setTextColor(color) }
        selectAll.setSp(17f)
        pageText.setSp(21f)
        fitTitleLink(titleBar, pageText)
    }

    /** 고른 줄(목록 순서) */
    private fun pickedRows(): List<Row> = list.filter { it.book.id in picked }

    /**
     * 여러 권 카테고리(Gmail 라벨처럼, [BulkCategories]): 머리 `카테고리` / `고른 3권`, 카테고리마다 체크 줄(모두 ✓ · 일부 − · 없음, 오른쪽 회색
     * `2권`·`3권 모두`·`제거`, 통계에서 뺀 것은 뒤에 `통계에서 뺌`) · `+ 새 카테고리`(만들면 켜진 채로 다시 띄운다), 맨 아래 `취소` | `확인`.
     * `확인` 하면 바꾼 줄만 고른 책 모두에 붙이거나 떼고(한 트랜잭션) 편집을 끝낸다. 목록에 없던 책은 붙이는 순간 나간 책으로 적어 둔다.
     */
    private fun bulkCategories(state: BulkCategories? = null) {
        val chosen = pickedRows()
        if (chosen.isEmpty()) return
        val bulk = state ?: BulkCategories.of(this, chosen.map { resolvedId(it) })
        val sheet = Sheet(this).header(getString(R.string.category), getString(R.string.bulk_category_sub, chosen.size))
        Categories.list(this).forEach { c ->
            sheet.check(c.name, tag = if (c.excluded) getString(R.string.category_excluded_tag) else null,
                on = { bulk.mark(c.id) == BulkCategories.Mark.ALL }, mixed = { bulk.mark(c.id) == BulkCategories.Mark.SOME },
                rightNow = {
                    when (bulk.mark(c.id)) {
                        BulkCategories.Mark.ALL -> getString(R.string.category_all_of, bulk.total)
                        BulkCategories.Mark.SOME -> getString(R.string.category_books, bulk.count(c.id))
                        BulkCategories.Mark.NONE -> if (bulk.initial(c.id) != BulkCategories.Mark.NONE) getString(R.string.category_remove) else ""
                    }
                }) { bulk.toggle(c.id) }
        }
        sheet.item(getString(R.string.category_new)) {
            askNew { bulk.set(it.id, BulkCategories.Mark.ALL); if (!isFinishing) bulkCategories(bulk) }
        }
            .footer(getString(R.string.cancel)) {}
            .done(getString(R.string.filter_done)) {
                val add = bulk.add
                val remove = bulk.remove
                if (add.isNotEmpty() || remove.isNotEmpty()) BookShelf.together(this) {
                    Categories.change(this, pickedRows().map { claimedId(it) }, add, remove)
                }
                endEdit()
            }
            .show()
    }

    /**
     * 여러 권 `Readblio에서 지우기` 확인: `3권을 Readblio에서 지울까요?` / 제목 한 줄(넘치면 `… 외 2권`) / `모든 해의 읽은 책 목록·결산에서 빠집니다.`
     * (읽고 있는 책이 있으면 `그중 1권은 홈에서도 빠집니다.`), 고른 책을 합쳐 1분 이상 읽었으면 체크 줄 `읽은 시간 1시간 14분도 지우기`(기본 켜짐,
     * 한 권 지우기와 같다. 아래 작게 `모든 해의 시간 · …`), `3권 지우기`(굵게) / `취소`. 한 트랜잭션으로 지우고 편집을 끝낸다.
     */
    private fun bulkForget() {
        val chosen = pickedRows()
        if (chosen.isEmpty()) return
        val ms = chosen.sumOf { it.record.dayMs.values.sum() }
        var dropTime = ms >= 60_000
        val home = chosen.count { it.record.state == BookRecord.State.READING }
        val names = namesLine(chosen.map { it.book.title })
        val notes = listOfNotNull(names, getString(R.string.bulk_forget_note), home.takeIf { it > 0 }?.let { getString(R.string.bulk_forget_home, it) })
        val sheet = Sheet(this).header(getString(R.string.bulk_forget_confirm, chosen.size), notes.joinToString("\n"))
        if (dropTime) sheet.check(getString(R.string.forget_time, duration(ms)), sub = getString(R.string.bulk_forget_time_sub), on = { dropTime }) { dropTime = !dropTime }
        sheet.item(getString(R.string.bulk_forget_action, chosen.size), bold = true) {
            BookShelf.together(this) {
                pickedRows().forEach { r ->
                    BookShelf.forget(this, claimedId(r), dropTime, fromShelf = r.record.state == BookRecord.State.READING)
                }
            }
            endEdit()
        }.item(getString(R.string.cancel)) {}.noClose().show()
    }

    /** 제목들을 한 줄에(`A · B · C`), 화면 폭에 다 들지 않으면 드는 만큼과 `외 N권`(한 권이면 그 제목을 `…` 로 줄여) */
    private fun namesLine(titles: List<String>): String {
        val paint = text("", 18f).paint
        val width = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN)
        if (titles.size == 1) return android.text.TextUtils.ellipsize(titles[0], paint, width.toFloat(), android.text.TextUtils.TruncateAt.END).toString()
        val sep = " · "
        for (k in titles.size downTo 1) {
            val head = titles.take(k).joinToString(sep)
            val s = if (k == titles.size) head else getString(R.string.bulk_forget_more, head, titles.size - k)
            if (paint.measureText(s) <= width) return s
        }
        return getString(R.string.bulk_forget_more, android.text.TextUtils.ellipsize(titles[0], paint, width * 0.6f, android.text.TextUtils.TruncateAt.END), titles.size - 1)
    }

    /** 줄의 책 id: 목록에 없던 책은 그 제목의 책이 그사이 생겼으면 그 책(카테고리를 볼 때, 적어 두지는 않는다) */
    private fun resolvedId(r: Row): String =
        if (r.record.state == BookRecord.State.TITLE_ONLY)
            (BookShelf.findByTitle(this, r.book.title) ?: BookShelf.findLivePast(this, r.book.title)?.book)?.id ?: r.book.id
        else r.book.id

    /** 줄의 책 id(누르는 순간): 목록에 없던 책은 나간 책으로 적어 둔다([BookShelf.claim], 부르는 쪽 트랜잭션 안에서) */
    private fun claimedId(r: Row): String =
        if (r.record.state == BookRecord.State.TITLE_ONLY) BookShelf.claim(this, r.book.title, r.book.app).id else r.book.id

    companion object {
        /** 편집 체크 칸: 목록 줄 왼쪽(보기 메뉴와 같은 크기), 격자 표지 왼쪽 위(흰 테두리 포함) */
        private const val CHECK_DP = 24
        private const val GRID_CHECK_DP = 30
        /** 목록 줄 표지 위아래 여백(dp). 표지는 나머지 높이를 채운다. */
        private const val COVER_GAP_DP = 6
        /** 읽고 있는 책 목록과 같은 줄 높이 */
        private const val ROW_DP = 98
        /** 목록 줄의 완독 도장: 지름(줄보다 커서 위아래가 잘린다), 가운데가 줄 오른쪽 끝에서 안쪽으로 */
        private const val STAMP_DP = 120
        private const val STAMP_END_DP = 62
        /** 격자: 한 줄 권수, 표지 사이, 표지 밖 높이(위 여백·아래 글자) */
        private const val COLUMNS = 4
        private const val GAP_DP = 14
        private const val GRID_TEXT_DP = 34
        /** 가로 화면 목록: 한 권 칸(여백 안)이 이만큼 넓게 남으면 두 권씩 나란히 */
        private const val LIST_COLUMN_DP = 440
        /** 격자 별 줄(별 12dp + 위 5dp): 표지를 이만큼 줄인다 */
        private const val GRID_STARS_DP = 17
        /** 목록 저자와 별 사이 `·` 양옆 틈과 `·` 자리(점 폭 넉넉히) */
        private const val DOT_GAP_DP = 8
        private const val DOT_W_DP = 2 * DOT_GAP_DP + 8

        fun intent(context: Context) = Intent(context, YearBooksActivity::class.java)

        /** 고를 수 있는 해: 책이 보이는 해([BookRecord.readYears])나 완독한 해와 올해(최근 순) */
        internal fun years(records: List<BookRecord>, today: LocalDate): List<Int> {
            return (records.flatMap { r -> r.readYears + r.finishes.map { it.year } } + today.year).distinct().sortedDescending()
        }

        /**
         * [year] 에 합쳐 5분 이상 읽었거나 그해 완독한 책(직접 넣은 책은 회차가 걸친 해, [BookRecord.readYears]). 그해 마지막으로 읽은(완독한) 날 순
         * (직접 넣은 책은 읽은 날 대신 그해 회차를 시작한 날), 같으면 그해 오래 읽은 순.
         * 기간·읽은 시간은 남은 기록 전체로 센다(해를 넘긴 책도 맞게).
         */
        internal fun yearRows(records: List<BookRecord>, year: Int): List<Row> =
            records
                .filter { year in it.readYears || it.finishes.any { d -> d.year == year } }
                .map { r ->
                    // 그해 조금이라도 읽은 마지막 날(목록에는 그해 합쳐 5분이면 들어오니, 하루 5분이 안 된 날도 정렬에 센다)
                    val lastRead = r.dayMs.filter { (d, ms) -> d.year == year && ms > 0 }.keys.maxOrNull()
                        // 직접 넣은 책은 읽은 시간이 없어 그해 회차를 시작한 날로
                        ?: r.rounds.takeIf { r.book.manual }?.mapNotNull { it.from }?.filter { it.year == year }?.maxOrNull() ?: LocalDate.MIN
                    val lastDone = r.finishes.lastOrNull { it.year == year } ?: LocalDate.MIN
                    Row(r, maxOf(lastRead, lastDone), doneInYear = lastDone != LocalDate.MIN)
                }
                .sortedWith(compareByDescending<Row> { it.sortDay }.thenByDescending { it.record.yearMs(year) })
    }
}
