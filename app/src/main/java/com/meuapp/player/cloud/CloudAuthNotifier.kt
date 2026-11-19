package com.meuapp.player.cloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meuapp.player.MainActivity

/**
 * Aviso (notificação) de que a conta do Google caiu ou o acesso foi revogado.
 * Ao tocar nele, o app abre e reconecta. Não repete o aviso enquanto o anterior não for resolvido.
 * A notificação nunca leva token, link ou dado da conta.
 */
object CloudAuthNotifier {
    const val EXTRA_RECONNECT = "toxic_reconnect"
    private const val CHANNEL_ID = "toxic_account"
    private const val NOTIF_ID = 4101
    private const val PREFS = "toxic_cloud"
    private const val KEY_SHOWN = "reauth_notif_shown"
    private const val KEY_ASKED = "notif_perm_asked"

    /** O Android permite notificar? (No Android 12 ou menor é sempre sim, salvo se o usuário desligou nas configurações.) */
    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Precisamos pedir a permissão? Só no Android 13+, e só uma vez (para não incomodar). */
    fun shouldAskPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    /** Mostra o aviso, se puder e se ainda não estiver na tela. Devolve true se mostrou. */
    fun show(ctx: Context): Boolean {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SHOWN, false)) return false
        if (!canNotify(app)) return false
        try {
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Aviso da conta Google", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_RECONNECT, true)
            }
            val pi = PendingIntent.getActivity(app, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Reconecte sua conta do Google")
                .setContentText("Toque aqui para voltar a ouvir suas músicas do Drive.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(NOTIF_ID, n)
            prefs.edit().putBoolean(KEY_SHOWN, true).apply()
            return true
        } catch (e: SecurityException) {
            return false
        } catch (e: Exception) {
            return false
        }
    }

    /** A conta voltou (ou foi desconectada de propósito): apaga o aviso e libera um novo para o futuro. */
    fun clear(ctx: Context) {
        val app = ctx.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOWN, false).apply()
        try { NotificationManagerCompat.from(app).cancel(NOTIF_ID) } catch (e: Exception) { /* sem problema */ }
    }
}
