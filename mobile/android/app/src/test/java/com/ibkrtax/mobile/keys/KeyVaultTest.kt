package com.ibkrtax.mobile.keys

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyVaultTest {
    // Small Argon2id settings keep the JVM tests fast; production uses Argon2idParams.DEFAULT.
    private val vault = KeyVault(Argon2idParams(memoryKiB = 1024, iterations = 1, parallelism = 1))
    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun passphrasePolicyRejectsShortAndTrivialPassphrases() {
        assertEquals(PassphraseProblem.TOO_SHORT, PassphrasePolicy.check("short".toCharArray()))
        assertEquals(PassphraseProblem.TOO_SIMPLE, PassphrasePolicy.check("aaaaaaaaaaaaaa".toCharArray()))
        assertEquals(PassphraseProblem.TOO_SIMPLE, PassphrasePolicy.check("123456789012".toCharArray()))
        assertEquals(PassphraseProblem.TOO_SIMPLE, PassphrasePolicy.check("210987654321".toCharArray()))
        assertEquals(PassphraseProblem.TOO_SIMPLE, PassphrasePolicy.check("zyxwvutsrqpo".toCharArray()))
        assertNull(PassphrasePolicy.check(passphrase))
    }

    @Test
    fun recoveryCodeIsWellFormedAndForgivingOnInput() {
        val code = RecoveryCode.generate()
        assertEquals(26, code.length)
        val shown = RecoveryCode.format(code)
        assertEquals(listOf(4, 4, 4, 4, 5, 5), shown.split("-").map { it.length })

        assertEquals(code, RecoveryCode.normalize(shown.lowercase()))
        assertEquals(code, RecoveryCode.normalize(shown.replace("-", " ")))
        assertEquals("0" + "1".repeat(25), RecoveryCode.normalize("O" + "IL".repeat(12) + "I"))
        assertNull(RecoveryCode.normalize("too short"))
        assertNull(RecoveryCode.normalize("U".repeat(26)))
    }

    @Test
    fun passphraseAndRecoveryCodeBothUnlockTheSameDataKey() {
        val setup = vault.setUp(passphrase)

        assertEquals(32, setup.dataKey.size)
        assertArrayEquals(setup.dataKey, vault.unlockWithPassphrase(setup.manifest, passphrase))
        assertArrayEquals(setup.dataKey, vault.unlockWithRecoveryCode(setup.manifest, RecoveryCode.format(setup.recoveryCode).lowercase()))
        assertTrue(vault.matches(setup.manifest, setup.dataKey))
    }

    @Test
    fun wrongSecretsAreRejectedWithoutPartialResults() {
        val setup = vault.setUp(passphrase)

        assertThrows(WrongSecretException::class.java) { vault.unlockWithPassphrase(setup.manifest, "wrong passphrase!".toCharArray()) }
        assertThrows(WrongSecretException::class.java) { vault.unlockWithRecoveryCode(setup.manifest, RecoveryCode.generate()) }
        assertThrows(WrongSecretException::class.java) { vault.unlockWithRecoveryCode(setup.manifest, "not a code") }
        assertFalse(vault.matches(setup.manifest, ByteArray(32)))
    }

    @Test
    fun changingThePassphraseRewrapsOnlyThePassphraseKey() {
        val setup = vault.setUp(passphrase)
        val newPassphrase = "a different passphrase".toCharArray()

        val changed = vault.changePassphrase(setup.manifest, setup.dataKey, newPassphrase)

        assertArrayEquals(setup.dataKey, vault.unlockWithPassphrase(changed, newPassphrase))
        assertThrows(WrongSecretException::class.java) { vault.unlockWithPassphrase(changed, passphrase) }
        assertArrayEquals(setup.dataKey, vault.unlockWithRecoveryCode(changed, setup.recoveryCode))
        assertArrayEquals(setup.manifest.recoveryWrappedKey, changed.recoveryWrappedKey)
        assertNotEquals(setup.manifest.passphraseSalt.toList(), changed.passphraseSalt.toList())
    }

    @Test
    fun manifestRoundTripsAndContainsNoSecrets() {
        val setup = vault.setUp(passphrase)
        val json = setup.manifest.toJson()

        assertEquals(setup.manifest, KeyManifest.fromJson(json))
        assertFalse(json.contains(setup.recoveryCode))
        assertFalse(json.contains(String(passphrase)))
        assertFalse(json.contains(java.util.Base64.getEncoder().encodeToString(setup.dataKey)))
        assertTrue(json.contains("\"memoryKiB\":1024"))
    }

    @Test
    fun newerManifestVersionIsReportedNotDecrypted() {
        val json = vault.setUp(passphrase).manifest.toJson().replace("\"version\":1", "\"version\":2")
        assertThrows(UnsupportedManifestException::class.java) { KeyManifest.fromJson(json) }
    }
}
