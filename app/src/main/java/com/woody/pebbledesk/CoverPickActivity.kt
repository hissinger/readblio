package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 책 정보 고치기 › `기기에서 표지 고르기`: 기기의 사진으로 책의 **표지만** 바꾼다(제목·저자·자리·앱·진행률은 그대로, 인터넷 없이. 검색에 없는 책·직접 넣은 책·만화·직접 찍은 표지, 이슈 #26).
 * 그리는 것 없는 투명 화면: 열리면 바로 사진을 줄 수 있는 앱 목록([PickImage.chooser])을 띄우고, 고른 사진을 뒤에서 읽어 긴 변 [COVER_PX] 까지 줄인 JPEG 으로
 * [BookShelf.replaceCover] 에 넣고 닫는다. 홈·올해 읽은 책은 돌아오면 바뀐 표지로 다시 그린다. 자르지 않는다: 표지는 어디서나 칸에 맞춰 가운데를 잘라 그리므로 어떤 비율이든 된다.
 * 사용자가 닫은 것(취소)만 조용히 끝내고, 주소 없이 돌아오거나 읽지 못하면 `사진을 열지 못했습니다` 창에 진단 한 줄(사진으로 받아 원인을 가린다, 배경 사진과 같다). 창을 닫으면 끝.
 * 이 화면이 따로 있는 까닭: 메뉴는 홈·올해 읽은 책 어디서나 뜨므로, 고르기 창의 결과를 받을 곳을 하나로 둔다.
 */
class CoverPickActivity : EinkActivity() {
    private val bookId by lazy { intent.getStringExtra(EXTRA_BOOK).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 고르기 창을 연 채 이 화면이 다시 만들어졌으면(메모리 정리. 회전은 매니페스트 configChanges 로 다시 만들지 않는다) 결과만 기다린다
        if (savedInstanceState != null) return
        if (runCatching { startActivityForResult(PickImage.chooser(getString(R.string.cover_device_pick)), PICK) }.isFailure) failed("no picker")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK) return
        val uri = PickImage.uri(data)
        if (uri == null) {
            PickImage.noUri(resultCode, data)?.let { failed(it) } ?: finish()
            return
        }
        val app = applicationContext
        Bg.run({ coverJpeg(app, uri) }) { got ->
            if (isFinishing || isDestroyed) return@run
            // 저장은 화면 스레드에서(표지 받아 바꾸기 [BookSearchActivity] 와 같다)
            got.mapCatching { BookShelf.replaceCover(this, bookId, it) }.onSuccess { finish() }.onFailure { failed(PickImage.failed(uri, it)) }
        }
    }

    /** 제목 아래 회색 진단 한 줄. 창을 닫으면 이 화면도 닫는다. */
    private fun failed(detail: String) {
        Sheet(this).header(getString(R.string.picture_failed), detail).onDismiss { finish() }.show()
    }

    companion object {
        private const val PICK = 1
        private const val EXTRA_BOOK = "book"
        /** 기기에서 고른 표지의 긴 변(px, 키우지 않는다). 검색 표지(Apple Books 1000×1000)와 비슷하게 */
        private const val COVER_PX = 1000
        private const val COVER_QUALITY = 90

        fun intent(context: Context, bookId: String) = Intent(context, CoverPickActivity::class.java).putExtra(EXTRA_BOOK, bookId)

        /** [uri] 의 사진을 긴 변 [COVER_PX] 까지 줄여(회전·투명은 [PickImage.decode]) JPEG 으로. 뒤에서 부른다. */
        private fun coverJpeg(context: Context, uri: Uri): ByteArray {
            val bmp = PickImage.decode(context, uri) { w, h -> COVER_PX.toFloat() / max(w, h) }
            try {
                return ByteArrayOutputStream().also { if (!bmp.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it)) error("compress") }.toByteArray()
            } finally {
                bmp.recycle()
            }
        }
    }
}
