package com.meuapp.player.cloud

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Autorização OAuth 2.0 via Google Play Services (AuthorizationClient).
 *
 * Segurança: o app NUNCA vê senha nem refresh token (ficam com o Google Play Services) e NÃO grava o access token
 * em disco: ele vive só na memória, com validade curta. Por isso não há segredo para proteger no Keystore.
 * Só persistimos o fato "conectado" e o e-mail da conta (dado não secreto) para a interface.
 */
object CloudAuth {
    sealed class Outcome {
        data class Granted(val token: String) : Outcome()
        /** O usuário precisa ver uma tela do Google (consentimento ou escolha de conta). */
        data class NeedsUi(val pendingIntent: PendingIntent) : Outcome()
    }

    private lateinit var appCtx: Context
    @Volatile private var token: String? = null
    @Volatile private var tokenAt = 0L
    private val lock = Any()

    fun init(ctx: Context) { if (!::appCtx.isInitialized) appCtx = ctx.applicationContext }

    private val prefs get() = appCtx.getSharedPreferences("toxic_cloud", Context.MODE_PRIVATE)

    var connected: Boolean
        get() = prefs.getBoolean("connected", false)
        private set(v) { prefs.edit().putBoolean("connected", v).apply() }

    private fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(CloudConfig.DRIVE_SCOPE))).build()

    /** Token em memória, se ainda válido pelo nosso relógio. */
    fun cachedToken(): String? {
        val t = token
        return if (t != null && System.currentTimeMillis() - tokenAt < CloudConfig.TOKEN_TTL_MS) t else null
    }

    private fun store(t: String) { synchronized(lock) { token = t; tokenAt = System.currentTimeMillis() } }
    fun invalidateToken() { synchronized(lock) { token = null; tokenAt = 0L } }

    /** Inicia/renova a autorização. Chamar fora da thread principal. Pode pedir interface ao usuário. */
    fun authorizeBlocking(): Outcome {
        try {
            val r = Tasks.await(Identity.getAuthorizationClient(appCtx).authorize(request()), 30, TimeUnit.SECONDS)
            return outcome(r)
        } catch (e: ExecutionException) {
            throw mapAuthFailure(e.cause ?: e)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw CloudException(CloudErrorKind.NO_INTERNET, cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CloudException(CloudErrorKind.UNKNOWN, cause = e)
        }
    }

    /** Resultado da tela de consentimento do Google. */
    fun handleResult(data: Intent?): Outcome {
        try {
            return outcome(Identity.getAuthorizationClient(appCtx).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            throw mapAuthFailure(e)
        }
    }

    private fun outcome(r: AuthorizationResult): Outcome {
        if (r.hasResolution()) {
            val pi = r.pendingIntent ?: throw CloudException(CloudErrorKind.UNKNOWN)
            return Outcome.NeedsUi(pi)
        }
        val t = r.accessToken ?: throw CloudException(CloudErrorKind.TOKEN_EXPIRED)
        store(t)
        connected = true
        return Outcome.Granted(t)
    }

    private fun mapAuthFailure(t: Throwable): CloudException {
        if (t is ApiException) {
            return when (t.statusCode) {
                10, 12500, 12501 -> if (t.statusCode == 12501) CloudException(CloudErrorKind.CANCELLED, cause = t)
                                    else CloudException(CloudErrorKind.NOT_CONFIGURED, cause = t)   // 10 = DEVELOPER_ERROR
                7 -> CloudException(CloudErrorKind.NO_INTERNET, cause = t)                           // NETWORK_ERROR
                16 -> CloudException(CloudErrorKind.CANCELLED, cause = t)
                else -> CloudException(CloudErrorKind.ACCESS_REVOKED, cause = t)
            }
        }
        return CloudException(CloudErrorKind.UNKNOWN, cause = t)
    }

    /**
     * Token para chamadas ao Drive (inclusive do player em segundo plano). Usa o cache; senão tenta renovar em silêncio.
     * Se o Google exigir interação, falha com TOKEN_EXPIRED: o serviço não pode abrir telas.
     */
    fun tokenBlocking(): String {
        if (!connected) throw CloudException(CloudErrorKind.NOT_CONNECTED)
        cachedToken()?.let { return it }
        synchronized(lock) {
            cachedToken()?.let { return it }
            return when (val o = authorizeBlocking()) {
                is Outcome.Granted -> o.token
                is Outcome.NeedsUi -> throw CloudException(CloudErrorKind.TOKEN_EXPIRED)
            }
        }
    }

    /** Descarta o token atual e pede outro (usado depois de um 401). */
    fun refreshBlocking(): String { invalidateToken(); return tokenBlocking() }

    /**
     * Desconecta: revoga o acesso no Google (endpoint oficial de revogação) e esquece o estado local.
     * Falha de rede na revogação não impede a desconexão local; o resultado diz se a revogação remota funcionou.
     */
    fun disconnectBlocking(): Boolean {
        // Sem token em memória, tenta obter um em silêncio só para poder revogá-lo (sem abrir telas).
        val t = token ?: try { (authorizeBlocking() as? Outcome.Granted)?.token } catch (e: Exception) { null }
        var revoked = false
        if (t != null) {
            try {
                val c = URL(CloudConfig.REVOKE_URL).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.connectTimeout = CloudConfig.CONNECT_TIMEOUT_MS
                    c.readTimeout = CloudConfig.READ_TIMEOUT_MS
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    c.outputStream.use { it.write("token=${URLEncoder.encode(t, "UTF-8")}".toByteArray()) }
                    revoked = c.responseCode == 200
                } finally { c.disconnect() }
            } catch (e: Exception) { revoked = false }
        }
        invalidateToken()
        connected = false
        return revoked
    }

    /** Apenas esquece o estado local (quando o Google já revogou o acesso por fora). */
    fun markDisconnected() { invalidateToken(); connected = false }
}
