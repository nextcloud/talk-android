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
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession

/**
 * Regression tests for the hostname verifier returned by [TrustManager.getHostnameVerifier].
 *
 * Before the fix, a certificate without a matching Subject Alternative Name (e.g. legacy
 * self-signed certificates that only set a Common Name) was always rejected, even after the
 * user had explicitly trusted that exact certificate via the certificate dialog
 * ([TrustManager.addCertInTrustStore]).
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

        trustManager = TrustManager()
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
        trustManager.addCertInTrustStore(certificate)

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
        trustManager.addCertInTrustStore(certificate)

        val sslSession = sessionWithCertificate(certificate)
        val defaultVerifier = mock<HostnameVerifier>()
        whenever(defaultVerifier.verify("192.168.178.162", sslSession)).thenReturn(false)

        val hostnameVerifier = trustManager.getHostnameVerifier(defaultVerifier)

        assertTrue(hostnameVerifier.verify("192.168.178.162", sslSession))
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
}
