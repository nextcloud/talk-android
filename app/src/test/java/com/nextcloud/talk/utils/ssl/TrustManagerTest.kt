/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.application.NextcloudTalkApplication
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket

/**
 * Tests for the hostname verifier returned by [TrustManager.getHostnameVerifier] and for the
 * server trust check, both of which honour certificates the user trusted for a specific host
 * via the certificate dialog ([TrustManager.addCertInTrustStore]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class TrustManagerTest {

    private lateinit var trustManager: TrustManager

    @Before
    fun setUp() {
        // TrustManager() needs NextcloudTalkApplication.sharedApplication for Context.getDir().
        // Attach a real context without running NextcloudTalkApplication.onCreate(), which would
        // pull in Dagger, WorkManager and WebRTC init that this test doesn't need.
        val fakeApplication = NextcloudTalkApplication()
        ReflectionHelpers.callInstanceMethod<Any?>(
            fakeApplication,
            "attachBaseContext",
            ReflectionHelpers.ClassParameter.from(Context::class.java, ApplicationProvider.getApplicationContext())
        )
        setSharedApplication(fakeApplication)

        trustManager = newTrustManager()
    }

    @After
    fun tearDown() {
        setSharedApplication(null)
    }

    // NextcloudTalkApplication.sharedApplication's setter is `protected` (only meant to be set
    // from onCreate()/onTerminate()); reflection is the only way in from a test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        ReflectionHelpers.callInstanceMethod<Any?>(
            NextcloudTalkApplication.Companion,
            "setSharedApplication",
            ReflectionHelpers.ClassParameter.from(NextcloudTalkApplication::class.java, application)
        )
    }

    @Test
    fun `verify accepts connection when default hostname verifier passes`() {
        // By the time the hostname verifier runs, checkServerTrusted() has already let the TLS
        // handshake through for this exact certificate (CA-trusted, or previously trusted here).
        val certificate = selfSignedCertificate(host = "example.com", withSan = true)
        trustManager.addCertInTrustStore("example.com", certificate)

        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("example.com", sslSession)).thenReturn(true)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertTrue(hostnameVerifier.verify("example.com", sslSession))
    }

    @Test
    fun `verify rejects legacy certificate without SAN that was never manually trusted`() {
        val certificate = selfSignedCertificate(host = "192.168.178.162", withSan = false)
        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("192.168.178.162", sslSession)).thenReturn(false)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertFalse(hostnameVerifier.verify("192.168.178.162", sslSession))
    }

    @Test
    fun `verify accepts legacy certificate without SAN once the user manually trusted it`() {
        val certificate = selfSignedCertificate(host = "192.168.178.162", withSan = false)
        trustManager.addCertInTrustStore("192.168.178.162", certificate)

        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("192.168.178.162", sslSession)).thenReturn(false)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertTrue(hostnameVerifier.verify("192.168.178.162", sslSession))
    }

    @Test
    fun `verify rejects certificate the user trusted for another host`() {
        val certificate = selfSignedCertificate(host = "192.168.178.162", withSan = false)
        trustManager.addCertInTrustStore("192.168.178.162", certificate)

        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("192.168.178.200", sslSession)).thenReturn(false)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertFalse(hostnameVerifier.verify("192.168.178.200", sslSession))
    }

    @Test
    fun `verify ignores certificates stored under the legacy subject alias`() {
        val certificate = selfSignedCertificate(host = "192.168.178.162", withSan = false)
        storeUnderLegacyAlias(certificate)
        trustManager = newTrustManager()

        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("192.168.178.162", sslSession)).thenReturn(false)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertFalse(hostnameVerifier.verify("192.168.178.162", sslSession))
    }

    @Test
    fun `certificates with the same subject are stored independently`() {
        val first = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        val second = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        trustManager.addCertInTrustStore("cloud.example.com", first)
        trustManager.addCertInTrustStore("cloud.example.com", second)

        trustManager.checkServerTrusted(arrayOf(first), AUTH_TYPE, socketFor("cloud.example.com"))
        trustManager.checkServerTrusted(arrayOf(second), AUTH_TYPE, socketFor("cloud.example.com"))
    }

    @Test
    fun `checkServerTrusted accepts certificate the user trusted for the connected host`() {
        val certificate = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        trustManager.addCertInTrustStore("cloud.example.com", certificate)

        trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE, socketFor("cloud.example.com"))
    }

    @Test
    fun `checkServerTrusted is case insensitive for the host`() {
        val certificate = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        trustManager.addCertInTrustStore("Cloud.Example.com", certificate)

        trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE, socketFor("cloud.example.COM"))
    }

    @Test(expected = CertificateException::class)
    fun `checkServerTrusted rejects certificate the user trusted for another host`() {
        val certificate = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        trustManager.addCertInTrustStore("cloud.example.com", certificate)

        trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE, socketFor("evil.example.org"))
    }

    @Test(expected = CertificateException::class)
    fun `checkServerTrusted without a host rejects user trusted certificate`() {
        val certificate = selfSignedCertificate(host = "cloud.example.com", withSan = true)
        trustManager.addCertInTrustStore("cloud.example.com", certificate)

        trustManager.checkServerTrusted(arrayOf(certificate), AUTH_TYPE)
    }

    @Test
    fun `verify rejects connection when there are no peer certificates`() {
        val sslSession = mock<SSLSession>()
        whenever(sslSession.peerCertificates).thenReturn(arrayOf<X509Certificate>())
        val defaultVerifier = mock<HostnameVerifier>()

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertFalse(hostnameVerifier.verify("example.com", sslSession))
    }

    @Test
    fun `verify rejects connection when peer certificates cannot be read`() {
        val sslSession = mock<SSLSession>()
        whenever(sslSession.peerCertificates).thenThrow(SSLPeerUnverifiedException("no certificates"))
        val defaultVerifier = mock<HostnameVerifier>()

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertFalse(hostnameVerifier.verify("example.com", sslSession))
    }

    private fun selfSignedCertificate(host: String, withSan: Boolean): X509Certificate {
        val builder = HeldCertificate.Builder().commonName(host)
        if (withSan) {
            builder.addSubjectAlternativeName(host)
        }
        return builder.build().certificate
    }

    private fun sessionWithCertificate(certificate: X509Certificate): SSLSession {
        val sslSession = mock<SSLSession>()
        whenever(sslSession.peerCertificates).thenReturn(arrayOf(certificate))
        return sslSession
    }

    // No one answers the certificate dialog in these tests, so don't wait for an answer
    private fun newTrustManager(): TrustManager = TrustManager().apply { userDecisionTimeoutMillis = 0 }

    private fun socketFor(host: String): SSLSocket {
        val handshakeSession = mock<SSLSession>()
        whenever(handshakeSession.peerHost).thenReturn(host)
        val socket = mock<SSLSocket>()
        whenever(socket.handshakeSession).thenReturn(handshakeSession)
        return socket
    }

    private fun storeUnderLegacyAlias(certificate: X509Certificate) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keystoreFile = File(context.getDir("CertsKeystore", Context.MODE_PRIVATE), "keystore.bks")
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setCertificateEntry(certificate.subjectDN.name, certificate)
        FileOutputStream(keystoreFile).use { keyStore.store(it, null) }
    }

    companion object {
        private const val AUTH_TYPE = "ECDHE_ECDSA"
    }
}
