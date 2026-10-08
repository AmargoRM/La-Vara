package com.lavara.system.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseParserTest {

    private fun release(name: String, body: String, assets: String) = """
        {"tag_name":"ultima","name":"$name","body":"$body","draft":false,"assets":[$assets]}
    """.trimIndent()

    private val apkAsset = """{"name":"La-Vara.apk","url":"https://api.github.com/repos/AmargoRM/La-Vara/releases/assets/606417244","size":22919569}"""

    @Test
    fun leeLaMarcaDeVersionDeLasNotas() {
        val info = ReleaseParser.parse(release("La Vara 0.1.12", "Texto <!-- versionCode=12 -->", apkAsset))!!
        assertEquals(12, info.versionCode)
        assertEquals("0.1.12", info.versionName)
        assertEquals("https://api.github.com/repos/AmargoRM/La-Vara/releases/assets/606417244", info.apkApiUrl)
        assertEquals(22919569L, info.apkSizeBytes)
    }

    @Test
    fun sinMarcaUsaElTitulo() {
        // Así es el Release 0.1.8, publicado antes de que existiera la marca.
        val info = ReleaseParser.parse(release("La Vara 0.1.8", "Última versión de La Vara. Compilación 8.", apkAsset))!!
        assertEquals(8, info.versionCode)
    }

    @Test
    fun sinApkDevuelveNull() {
        val otro = """{"name":"otra-cosa.zip","url":"https://x","size":1}"""
        assertNull(ReleaseParser.parse(release("La Vara 0.1.8", "", otro)))
    }

    @Test
    fun apkFueraDeLaApiDeGithubDevuelveNull() {
        val ajeno = """{"name":"La-Vara.apk","url":"https://otro-servidor.com/La-Vara.apk","size":1}"""
        assertNull(ReleaseParser.parse(release("La Vara 0.1.40", "", ajeno)))
    }

    @Test
    fun sinNumeroDeVersionDevuelveNull() {
        assertNull(ReleaseParser.parse(release("Sin número", "nada", apkAsset)))
    }

    @Test
    fun jsonRotoDevuelveNull() {
        assertNull(ReleaseParser.parse("<html>error</html>"))
    }

    @Test
    fun soloEsNuevaSiElNumeroEsMayor() {
        val info = ReleaseInfo(9, "0.1.9", "u", 0)
        assertTrue(ReleaseParser.isNewer(info, 8))
        assertFalse(ReleaseParser.isNewer(info, 9))
        assertFalse(ReleaseParser.isNewer(info, 10))
    }
}
