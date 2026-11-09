package com.meuapp.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class SafeReadTest {
    @Test fun leArquivoPequeno() {
        assertEquals("olá", SafeRead.readLimited(ByteArrayInputStream("olá".toByteArray()), 100))
    }

    @Test fun aceitaExatamenteOLimite() {
        assertEquals("a".repeat(10), SafeRead.readLimited(ByteArrayInputStream("a".repeat(10).toByteArray()), 10))
    }

    @Test fun rejeitaArquivoMaiorQueOLimite() {
        assertNull(SafeRead.readLimited(ByteArrayInputStream("a".repeat(11).toByteArray()), 10))
    }
}
