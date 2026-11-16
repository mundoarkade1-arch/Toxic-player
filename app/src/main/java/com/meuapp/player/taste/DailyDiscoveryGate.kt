package com.meuapp.player.taste

/**
 * Decide se as "Descobertas do dia" devem ser acrescentadas agora. Lógica pura, sem Android.
 * Regras: no máximo uma vez por dia; nunca se o usuário apagou a playlist (até ele criar de novo).
 */
object DailyDiscoveryGate {
    const val NAME = "Descobertas do dia"

    /** [today] e [lastDay] são datas no formato aaaa-mm-dd (ou vazio se nunca rodou). */
    fun shouldRun(today: String, lastDay: String?, userDeleted: Boolean): Boolean {
        if (userDeleted) return false
        if (today.isBlank()) return false
        return today != lastDay
    }

    /** Mesmo nome, sem diferença de maiúscula, espaço ou acento. */
    fun isDiscoveryName(name: String): Boolean =
        UserTasteProfile.norm(name.trim()) == UserTasteProfile.norm(NAME)
}
