package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 기기에서 사진 고르기(배경 사진 [Wallpaper]·표지 [CoverPickActivity]): 고르기 창, 돌려받은 주소, 읽기와 실패 진단 한 줄.
 */
object PickImage {
    /**
     * 사진을 줄 수 있는 앱 목록(안드로이드 고르기 창 `createChooser`): 파일 앱(`GET_CONTENT`)·갤러리(`ACTION_PICK`, 전자잉크 런처 inkOS 가 쓰는 길)·
     * 시스템 파일 고르기(`OPEN_DOCUMENT`). 한 길만 열면 그 앱이 사진을 돌려주지 않는 기기(iReader 계열, 2026-11-14 보고: 고른 뒤 아무 일 없이 끝남)에서
     * 막히므로 다른 앱으로 고를 수 있게 한다. `startActivityForResult` 로 연다.
     */
    fun chooser(title: String): Intent {
        val files = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        // 갤러리: 주소 없이 종류만(inkOS 와 같다. setType 은 앞서 넣은 주소를 지우므로 주소와 함께 쓰지 않는다)
        val gallery = Intent(Intent.ACTION_PICK).setType("image/*")
        val documents = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        return Intent.createChooser(files, title).putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(gallery, documents))
    }

    /** 고른 사진의 주소. 주소를 `data` 대신 `clipData` 로만 주는 고르기 창이 있어 둘 다 본다. */
    fun uri(data: Intent?): Uri? = data?.data ?: data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

    /**
     * 주소 없이 돌아왔을 때의 진단 한 줄(사진으로 받아 원인을 가린다). 사용자가 닫은 것(취소)이면 null.
     * 고르기 창이 넣은 extras 를 풀다 그 앱만 아는 객체면 예외가 나니 감싼다(진단하다 앱이 죽지 않게).
     */
    fun noUri(resultCode: Int, data: Intent?): String? {
        if (resultCode == android.app.Activity.RESULT_CANCELED) return null
        val extras = runCatching { data?.extras?.keySet()?.sorted() }.getOrElse { "?" }
        return "result $resultCode, no uri, extras $extras"
    }

    /** 사진을 읽지 못했을 때의 진단 한 줄 */
    fun failed(uri: Uri, e: Throwable) = "${uri.scheme}://${uri.authority} · ${e.javaClass.simpleName}: ${e.message.orEmpty().take(80)}"

    /**
     * [uri] 의 사진을 읽는다(뒤에서 부른다). 사진 크기 w×h 를 [scale] 배로 줄여 읽고(1 이상이면 그대로, 키우지 않는다), 회전 정보는 [ImageDecoder] 가 맞춘다.
     * JPEG 으로 저장하면 투명한 곳이 검게 남으므로(투명 배경 PNG) 흰 바탕에 얹는다. 읽지 못하면 예외.
     */
    fun decode(context: Context, uri: Uri, scale: (w: Int, h: Int) -> Float): Bitmap {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = info.size.width to info.size.height
            val s = scale(w, h)
            if (s < 1f) decoder.setTargetSize(max(1, (w * s).roundToInt()), max(1, (h * s).roundToInt()))
        }
        if (!decoded.hasAlpha()) return decoded
        return Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
            decoded.recycle()
        }
    }
}
