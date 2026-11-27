package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup

/**
 * 설정 › 정보 › 후원하기: 후원 수단 셋(카카오페이·Ko-fi·Buy Me a Coffee)의 QR 을 아래 메뉴에 한 줄로 보인다.
 * 전자잉크 기기에서는 결제가 어려워 링크를 열지 않고 휴대폰 카메라로 찍게 한다(인터넷 없이 보인다).
 * 언어와 상관없이 셋 다 같은 순서. 토스는 넣지 않는다: 토스아이디(toss.me)가 2024-08-01 에 끝나 토스 QR 은 계좌번호를 담는다(공개하면 통장 협박에 쓰일 수 있다).
 * QR 그림은 `tools/support_qr.py` 가 만든 모듈 1px 그림이고 [QrView] 가 정수배로 키워 흐리지 않게 그린다.
 */
object Support {
    private class Way(val qr: Int, val name: String, val note: String)

    fun show(c: Context) {
        val ways = listOf(
            Way(R.drawable.support_kakaopay, c.getString(R.string.support_kakaopay), c.getString(R.string.support_kakaopay_note)),
            Way(R.drawable.support_kofi, "Ko-fi", c.getString(R.string.support_kofi_note)),
            Way(R.drawable.support_bmc, "Buy Me a Coffee", c.getString(R.string.support_bmc_note)),
        )
        Sheet(c).header(c.getString(R.string.support_title), c.getString(R.string.support_desc))
            .note(row(c, ways))
            .show()
    }

    /** 칸 폭을 같게 나눈 한 줄(이름 길이와 상관없이 QR 사이가 같다) */
    private fun row(c: Context, ways: List<Way>): View = c.hbox().apply {
        // 이름이 두 줄인 칸이 있어도 QR 들이 위에 나란히(hbox 의 세로 가운데 맞춤·글자 기준선 맞춤이면 다른 칸이 아래로 밀린다)
        gravity = Gravity.TOP
        isBaselineAligned = false
        ways.forEach { w ->
            addView(c.vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(QrView(c, BitmapFactory.decodeResource(c.resources, w.qr)), lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                // 글자를 키우면 한 칸(세로 약 164dp)에 `Buy Me a Coffee` 가 다 들지 않아 두 줄까지
                addView(c.text(w.name, 18f, Ui.bold, lines = 2).apply { gravity = Gravity.CENTER_HORIZONTAL },
                    lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(6) })
                addView(c.text(w.note, 15f, color = Ui.GRAY).apply { gravity = Gravity.CENTER_HORIZONTAL },
                    lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = c.dp(2) })
            }, lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** QR 하나: 칸 폭과 [QR_DP] 중 작은 정사각형에, 모듈이 고르게 정수배로(필터 없이) 키워 가운데 그린다. */
    private class QrView(c: Context, private val qr: Bitmap) : View(c) {
        // 안드로이드 9 부터 Paint 는 기본으로 그림을 부드럽게 키운다(모듈 경계가 회색으로 번진다)
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private val dst = Rect()
        private val max = c.dp(QR_DP)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val size = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) max else minOf(w, max)
            setMeasuredDimension(if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) w else size, size)
        }

        override fun onDraw(canvas: Canvas) {
            val box = minOf(width, height, max)
            val n = qr.width
            val k = (box / n).coerceAtLeast(1)
            val l = (width - n * k) / 2
            val t = (height - n * k) / 2
            dst.set(l, t, l + n * k, t + n * k)
            canvas.drawBitmap(qr, null, dst, paint)
        }
    }

    private const val QR_DP = 160
}
