package com.meuapp.player.cloud

/** Caminhos de pasta do Drive (lógica pura, testável). */
object CloudFolders {
    const val ROOT = "Meu Drive"
    const val OTHER = "Outros"

    /** "Música/Rock" para a pasta [parentId]. Pasta raiz ou desconhecida = "Meu Drive"; sem pasta = "Outros". */
    fun pathOf(parentId: String, folders: Map<String, DriveFolder>): String {
        if (parentId.isBlank()) return OTHER
        val parts = ArrayList<String>()
        var cur: String? = parentId
        var guard = 0
        while (cur != null && guard++ < 30) {
            val f = folders[cur] ?: break               // não está na lista = raiz do Drive
            parts += f.name.ifBlank { "?" }
            cur = f.parentId.takeIf { it.isNotBlank() }
        }
        return if (parts.isEmpty()) ROOT else parts.reversed().joinToString("/")
    }
}
