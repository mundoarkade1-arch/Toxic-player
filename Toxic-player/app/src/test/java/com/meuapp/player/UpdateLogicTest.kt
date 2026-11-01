package com.meuapp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {
    private fun release(vararg names: String, draft: Boolean = false, pre: Boolean = false): String {
        val arr = names.joinToString(",") {
            """{"name":"$it","browser_download_url":"https://x/$it","size":1000,"state":"uploaded"}"""
        }
        return """{"tag_name":"v9","draft":$draft,"prerelease":$pre,"body":"notas","assets":[$arr]}"""
    }

    @Test fun lerApkValido() {
        val i = UpdateLogic.parse(release("ToxicPlayer-v1.2.5-c105.apk"))
        assertNotNull(i)
        assertEquals("1.2.5", i!!.versionName)
        assertEquals(105L, i.versionCode)
        assertEquals(1000L, i.size)
    }

    @Test fun ignoraRascunhoEPreLancamento() {
        assertNull(UpdateLogic.parse(release("ToxicPlayer-v1.2.5-c105.apk", draft = true)))
        assertNull(UpdateLogic.parse(release("ToxicPlayer-v1.2.5-c105.apk", pre = true)))
    }

    @Test fun ignoraNomesQueNaoSaoDoApp() {
        assertNull(UpdateLogic.parse(release("app-release.apk", "Source.zip")))
    }

    @Test fun escolheMaiorVersionCode() {
        val i = UpdateLogic.parse(release("ToxicPlayer-v1.2.5-c105.apk", "ToxicPlayer-v1.2.9-c109.apk"))
        assertEquals(109L, i!!.versionCode)
    }

    @Test fun jsonInvalidoDevolveNulo() {
        assertNull(UpdateLogic.parse("isso nao e json"))
    }

    @Test fun releaseSemAssets() {
        assertNull(UpdateLogic.parse("""{"tag_name":"v1","draft":false,"prerelease":false,"assets":[]}"""))
    }

    @Test fun comparacaoDeVersionCode() {
        assertTrue(UpdateLogic.isNewer(110L, 105L))
        assertFalse(UpdateLogic.isNewer(105L, 105L))
        assertFalse(UpdateLogic.isNewer(100L, 105L))
        // 1.10 vs 1.9: o código numérico resolve o que a comparação de texto erraria
        assertTrue(UpdateLogic.isNewer(110L, 19L))
    }

    @Test fun leHashQuandoExiste() {
        val h = "a".repeat(64)
        val json = """{"draft":false,"prerelease":false,"assets":[{"name":"ToxicPlayer-v1.0.1-c101.apk","browser_download_url":"https://x/a","size":5,"state":"uploaded","digest":"sha256:$h"}]}"""
        assertEquals(h, UpdateLogic.parse(json)!!.sha256)
    }
}
