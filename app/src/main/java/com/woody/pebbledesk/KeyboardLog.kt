package com.woody.pebbledesk

import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets

/**
 * 입력 메뉴(제목 고치기·감상 등)를 띄운 동안 키보드와 메뉴의 자리를 자동 추가 기록(`기록 보기`)에 남긴다([WatchLog.Kind.KEYBOARD]).
 * 키보드가 메뉴를 가리는 기기(2026-11-26 Viwoods AiPaper Reader: 화면이 어두워지고 키보드만 보였다)에서
 * 메뉴가 키보드 위에 오는지, 안 오면 키보드가 창에 알렸는지(키보드 앱)를 기록 사진으로 가르려고 둔다.
 * 창이 초점을 받고 [FIRST_MS] 뒤 한 번, 그 뒤로는 창 배치가 바뀔 때마다([SETTLE_MS] 기다려) 바뀐 것만, 메뉴 하나에 [MAX] 줄까지.
 * 줄: 키보드 앱(패키지) · 키보드 보임 여부 · 키보드가 메뉴를 가린 높이 · 메뉴 아래가 화면 아래에서 올라온 만큼(dp).
 * 정상이면 `보임 0 408`(가린 높이 0, 키보드 높이만큼 올라감) → 키보드를 내리면 `안 보임 0 31`(내비게이션 바). 가려지면 가린 높이가 0 보다 크고,
 * 키보드가 보이는데 `안 보임` 이면 키보드가 창에 알리지 않은 것이다. 기록은 기기 밖으로 보내지 않는다.
 */
object KeyboardLog {
    fun watch(root: View) {
        var last: String? = null
        var count = 0
        val record = Runnable {
            if (!root.isAttachedToWindow || count >= MAX) return@Runnable
            val state = state(root) ?: return@Runnable
            if (state == last) return@Runnable
            last = state
            count++
            val (visible, detail) = state.split(' ', limit = 2)
            WatchLog.add(root.context, WatchLog.Kind.KEYBOARD, pkg = imePackage(root), arg = visible, detail = detail)
        }
        root.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (!hasFocus) return
                root.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                root.postDelayed(record, FIRST_MS)
                // 키보드가 뜨고 내려갈 때 창 배치가 바뀐다(창이 올라가지 않아도 키보드 높이가 오면 다시 배치한다)
                root.viewTreeObserver.addOnGlobalLayoutListener {
                    root.removeCallbacks(record)
                    root.postDelayed(record, SETTLE_MS)
                }
            }
        })
    }

    /**
     * `1 0 408`: 키보드 보임(1·0) · 키보드가 메뉴를 가린 dp · 메뉴 아래가 화면 아래에서 올라온 dp. 창이 없으면 null.
     * 메뉴([root])의 아래 끝과 키보드 위 끝을 화면 자리로 견준다(창은 [Sheet] 가 아래를 비운 만큼 메뉴보다 클 수 있다).
     */
    @Suppress("DEPRECATION")
    private fun state(root: View): String? {
        val decor = root.rootView
        val insets = decor.rootWindowInsets ?: return null
        val screen = DisplayMetrics().also { decor.display?.getRealMetrics(it) }.heightPixels.takeIf { it > 0 } ?: return null
        fun bottomOf(v: View) = IntArray(2).also { v.getLocationOnScreen(it) }[1] + v.height
        val windowBottom = bottomOf(decor)
        val sheetBottom = bottomOf(root)
        // 키보드 위 끝(화면 자리). 키보드 높이(IME inset)는 창과 겹친 만큼이라 창 아래 끝에서 잰다
        val (visible, keyboardTop) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.isVisible(WindowInsets.Type.ime()) to windowBottom - insets.getInsets(WindowInsets.Type.ime()).bottom
        } else {
            // 안드로이드 10 까지는 키보드를 따로 묻지 못하고, 창이 키보드에 안 올라가면 아래 시스템 영역(systemWindowInset)에도 키보드가 오지 않아
            // (Poke3_A10 에뮬: 0) 창에서 보이는 영역의 아래 끝을 본다(그 아래가 내비게이션 바(stable)보다 크면 키보드)
            val frame = android.graphics.Rect().also { decor.getWindowVisibleDisplayFrame(it) }
            val top = if (frame.bottom > 0) frame.bottom else windowBottom
            (windowBottom - top > insets.stableInsetBottom) to top
        }
        val covered = if (visible) (sheetBottom - keyboardTop).coerceAtLeast(0) else 0
        val density = root.resources.displayMetrics.density
        fun dp(px: Int) = Math.round(px / density)
        return "${if (visible) 1 else 0} ${dp(covered)} ${dp(screen - sheetBottom)}"
    }

    private fun imePackage(view: View): String =
        Settings.Secure.getString(view.context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }.orEmpty()

    private const val FIRST_MS = 1500L
    private const val SETTLE_MS = 500L
    private const val MAX = 8
}
