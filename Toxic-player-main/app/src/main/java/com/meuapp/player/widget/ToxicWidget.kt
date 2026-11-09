package com.meuapp.player.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.media3.common.Player
import com.meuapp.player.MainActivity
import com.meuapp.player.R

/** Estado mostrado no widget (preenchido pelo serviço de áudio). */
object WidgetState {
    var title = "Toxic Player"
    var artist = "Toque para abrir"
    var playing = false
}

/** Ponte entre o widget e o player (definida pelo serviço enquanto ele está vivo). */
object WidgetBridge {
    var player: Player? = null
}

const val ACTION_W_PLAY = "com.meuapp.player.W_PLAY"
const val ACTION_W_NEXT = "com.meuapp.player.W_NEXT"
const val ACTION_W_PREV = "com.meuapp.player.W_PREV"

class ToxicWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, build(context))
    }

    companion object {
        private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, code, Intent(ctx, WidgetActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_player)
            v.setTextViewText(R.id.w_title, WidgetState.title)
            v.setTextViewText(R.id.w_artist, WidgetState.artist)
            v.setImageViewResource(R.id.w_play, if (WidgetState.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            v.setOnClickPendingIntent(R.id.w_play, pi(ctx, ACTION_W_PLAY, 1))
            v.setOnClickPendingIntent(R.id.w_next, pi(ctx, ACTION_W_NEXT, 2))
            v.setOnClickPendingIntent(R.id.w_prev, pi(ctx, ACTION_W_PREV, 3))
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.w_info, open)
            return v
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ToxicWidget::class.java))
            for (id in ids) mgr.updateAppWidget(id, build(ctx))
        }
    }
}

/** Recebe os toques dos botões do widget. Se o player não estiver ativo, abre o app. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = WidgetBridge.player
        if (p == null) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        when (intent.action) {
            ACTION_W_PLAY -> if (p.isPlaying) p.pause() else p.play()
            ACTION_W_NEXT -> p.seekToNextMediaItem()
            ACTION_W_PREV -> p.seekToPreviousMediaItem()
        }
    }
}
