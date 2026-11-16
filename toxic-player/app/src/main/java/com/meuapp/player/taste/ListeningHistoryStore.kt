package com.meuapp.player.taste

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.meuapp.player.Track

/**
 * Liga o histórico ao aparelho. Guarda em SharedPreferences privadas ("toxic_history").
 * Chamar só na thread principal. Grava no máximo a cada 5 segundos. Nada sai do aparelho.
 */
class ListeningHistoryStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("toxic_history", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var history = ListeningHistory.parse(prefs.getString("json", null))
    private var scheduled = false
    private val flushTask = Runnable { scheduled = false; flush() }

    val total: Int get() = history.size
    fun topArtists(limit: Int) = history.topArtists(limit)

    /** Perfil de gosto recalculado do histórico. [genreOf] recebe a chave da música ("c:<idDoDrive>") e devolve o gênero. */
    fun profile(genreOf: (String) -> String, now: Long = System.currentTimeMillis()): UserTasteProfile =
        UserTasteProfile.build(history.entries(), genreOf, now)

    /** Música estável: Drive = "c:<id>", aparelho = "l:<id>". */
    private fun keyOf(t: Track): String = t.cloudId?.let { "c:$it" } ?: "l:${t.id}"

    /** A faixa [t] acabou de ser deixada. [listenedMs] = quanto ouviu. */
    fun onTrackLeft(t: Track, listenedMs: Long, endedNaturally: Boolean, repeated: Boolean) {
        val outcome = PlayClassifier.classify(listenedMs, t.durationMs, endedNaturally)
        history.record(keyOf(t), t.title, t.artist, outcome, listenedMs, System.currentTimeMillis(), repeated)
        schedule()
    }

    fun setFavorite(t: Track, fav: Boolean) {
        history.setFavorite(keyOf(t), t.title, t.artist, fav, System.currentTimeMillis())
        schedule()
    }

    /** Apaga todo o histórico (para uma futura opção de privacidade na tela). */
    fun clear() {
        history = ListeningHistory()
        prefs.edit().remove("json").apply()
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        handler.postDelayed(flushTask, 5000L)
    }

    fun flush() { prefs.edit().putString("json", history.toJson()).apply() }
}
