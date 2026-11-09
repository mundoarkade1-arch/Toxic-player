package com.meuapp.player

import com.meuapp.player.audio.AudioFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFxImportTest {
    @Test
    fun importacaoRestauraEstadoMestreEEqualizador() {
        AudioFx.enabled = false
        val json = """{"toxic":"preset","enabled":true,"eq":[3,2,1,0,0,0,0,0,0,0],"eqOn":true}"""

        assertEquals("Preset carregado com sucesso.", AudioFx.importJson(json))
        assertTrue(AudioFx.enabled)
        assertEquals(3f, AudioFx.bands[0], 0.001f)
        assertEquals(2f, AudioFx.bands[1], 0.001f)
    }

    @Test
    fun importacaoRejeitaJsonSemAjustesDeAudio() {
        val result = AudioFx.importJson("""{"toxic":"preset","title":"arquivo qualquer"}""")
        assertTrue(result.contains("não encontrei ajustes de áudio"))
    }

    @Test
    fun jsonVazioNaoAplicaPreset() {
        val before = AudioFx.bands.toList()
        assertEquals("O arquivo está vazio.", AudioFx.importJson("   "))
        assertEquals(before, AudioFx.bands.toList())
    }
}
