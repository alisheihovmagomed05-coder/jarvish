package com.rasul.jarvis

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.KeyEvent

class MusicController(private val ctx: Context) {

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        const val YANDEX_PACKAGE = "ru.yandex.music"
        // Название приложения, по которому ищем Lane среди установленных
        const val LANE_LABEL = "lane"
    }

    // ---------- громкость и управление плеером ----------

    fun volumeUp() = audio.adjustStreamVolume(
        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
    )

    fun volumeDown() = audio.adjustStreamVolume(
        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
    )

    fun pause() = mediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    fun resume() = mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    fun next() = mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    private fun mediaKey(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    // ---------- запуск музыки ----------

    /** Возвращает короткую фразу, которую Джарвис произнесёт в ответ. */
    fun play(target: Target, query: String?): String {
        val pkg = packageFor(target)
            ?: return if (target == Target.LANE) "Не нашёл приложение Лейн" else "Не нашёл Яндекс Музыку"

        if (query.isNullOrBlank()) {
            return if (launch(pkg)) "Открываю" else "Не получилось открыть"
        }

        // Стандартный Android-запрос «включи по поиску», его понимают многие плееры
        val search = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(pkg)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStart(search)) return "Включаю $query"

        // Запасной вариант для Яндекс Музыки: ссылка на поиск
        if (target == Target.YANDEX) {
            val deep = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("yandexmusic://search/?text=" + Uri.encode(query))
            ).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(deep)) return "Ищу $query"
        }

        // Если плеер не умеет принимать поиск — хотя бы откроем его
        launch(pkg)
        return "Открыл плеер, но поиск трека он не принимает"
    }

    private fun packageFor(target: Target): String? = when (target) {
        Target.YANDEX -> YANDEX_PACKAGE.takeIf { isInstalled(it) }
        Target.LANE -> findByLabel(LANE_LABEL)
    }

    private fun isInstalled(pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun findByLabel(label: String): String? {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .firstOrNull { it.loadLabel(pm).toString().trim().equals(label, ignoreCase = true) }
            ?.activityInfo?.packageName
    }

    private fun launch(pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(intent)
    }

    private fun tryStart(intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
