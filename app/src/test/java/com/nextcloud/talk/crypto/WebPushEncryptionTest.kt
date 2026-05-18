/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Google Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nextcloud.talk.crypto

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.hybrid.PredefinedHybridParameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.security.GeneralSecurityException

@RunWith(JUnit4::class)
class WebPushEncryptionTest {

    @Before
    fun setUp() {
        HybridConfig.register()
    }

    @Test
    fun testEncryptDecrypt_shouldWork() {
        val privateKeysetHandle =
            KeysetHandle.generateNew(PredefinedHybridParameters.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
        val publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle()

        val hybridEncrypt = publicKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val hybridDecrypt = privateKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)

        val message = byteArrayOf(1, 2, 3)
        val contextInfo = ByteArray(0)
        val ciphertext = hybridEncrypt.encrypt(message, contextInfo)
        val decrypted = hybridDecrypt.decrypt(ciphertext, contextInfo)
        assertArrayEquals(message, decrypted)
    }

    @Test
    fun testEncryptDecrypt_withContextInfo_shouldWork() {
        val privateKeysetHandle =
            KeysetHandle.generateNew(PredefinedHybridParameters.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
        val publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle()

        val hybridEncrypt = publicKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val hybridDecrypt = privateKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)

        val message = byteArrayOf(1, 2, 3)

        val ciphertext = hybridEncrypt.encrypt(message, byteArrayOf(1, 2, 3))
        try {
            hybridDecrypt.decrypt(ciphertext, ByteArray(0))
            fail("Expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }

        try {
            hybridDecrypt.decrypt(ciphertext, byteArrayOf(4, 5, 6))
            fail("Expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }

        val decrypted = hybridDecrypt.decrypt(ciphertext, byteArrayOf(1, 2, 3))
        assertArrayEquals(message, decrypted)
    }

    @Test
    fun testModifyCiphertext_shouldFail() {
        val privateKeysetHandle =
            KeysetHandle.generateNew(PredefinedHybridParameters.ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM)
        val publicKeysetHandle = privateKeysetHandle.getPublicKeysetHandle()

        val hybridEncrypt = publicKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
        val hybridDecrypt = privateKeysetHandle.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java)

        val message = byteArrayOf(1, 2, 3)
        var ciphertext = hybridEncrypt.encrypt(message, ByteArray(0))

        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        try {
            hybridDecrypt.decrypt(ciphertext, ByteArray(0))
            fail("Expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }

        ciphertext = hybridEncrypt.encrypt(message, ByteArray(0))
        ciphertext[ciphertext.lastIndex] = (ciphertext[ciphertext.lastIndex].toInt() xor 1).toByte()
        try {
            hybridDecrypt.decrypt(ciphertext, ByteArray(0))
            fail("Expected GeneralSecurityException")
        } catch (e: GeneralSecurityException) {
            // expected
        }
    }
}
