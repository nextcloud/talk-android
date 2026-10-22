/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.api.LoggingHttpInterceptor
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.AccountCookieInterceptor
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.net.CookieManager
import java.net.Proxy
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

/**
 * Checks the hostname verification of the client [RestModule] actually builds, with real certificates.
 *
 * [com.nextcloud.talk.utils.ssl.TrustManagerTest] covers our wrapper with a mocked default verifier; this test
 * pins the default the wrapper is given (okhttp's own) and fails if hostname verification is ever weakened,
 * whether by okhttp or by the way the client is set up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class RestModuleHostnameVerificationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var trustManager: TrustManager
    private lateinit var hostnameVerifier: HostnameVerifier

    @Before
    fun setUp() {
        // TrustManager() needs NextcloudTalkApplication.sharedApplication for Context.getDir(), see TrustManagerTest
        val fakeApplication = NextcloudTalkApplication()
        ReflectionHelpers.callInstanceMethod<Any?>(
            fakeApplication,
            "attachBaseContext",
            ReflectionHelpers.ClassParameter.from(Context::class.java, ApplicationProvider.getApplicationContext())
        )
        setSharedApplication(fakeApplication)
        trustManager = TrustManager()

        val client = RestModule(ApplicationProvider.getApplicationContext()).provideHttpClient(
            Proxy.NO_PROXY,
            mock<AppPreferences>(),
            trustManager,
            SSLSocketFactoryCompat(null, trustManager),
            Cache(temporaryFolder.newFolder(), CACHE_SIZE),
            CookieManager(),
            Dispatcher(),
            mock<LoggingHttpInterceptor>(),
            AccountCookieInterceptor()
        )
        hostnameVerifier = client.hostnameVerifier
    }

    @After
    fun tearDown() {
        setSharedApplication(null)
    }

    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        ReflectionHelpers.callInstanceMethod<Any?>(
            NextcloudTalkApplication.Companion,
            "setSharedApplication",
            ReflectionHelpers.ClassParameter.from(NextcloudTalkApplication::class.java, application)
        )
    }

    @Test
    fun `a certificate for the requested host is accepted`() {
        val certificate = certificateFor(SERVER_HOST)

        assertTrue(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))
    }

    @Test
    fun `a certificate for another host is rejected`() {
        val certificate = certificateFor(OTHER_HOST)

        assertFalse(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))
    }

    @Test
    fun `a certificate for a subdomain of the requested host is rejected`() {
        val certificate = certificateFor("sub.$SERVER_HOST")

        assertFalse(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))
    }

    @Test
    fun `a certificate whose host only appears as common name is rejected`() {
        // since RFC 2818 the common name is not used for hostname verification, only subject alternative names
        val certificate = HeldCertificate.Builder().commonName(SERVER_HOST).build().certificate

        assertFalse(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))
    }

    @Test
    fun `a certificate for another host is accepted once the user trusted it for the requested host`() {
        val certificate = certificateFor(OTHER_HOST)
        assertFalse(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))

        trustManager.addCertInTrustStore(SERVER_HOST, certificate)

        assertTrue(hostnameVerifier.verify(SERVER_HOST, sessionWith(certificate)))
    }

    @Test
    fun `a certificate the user trusted for one host is rejected for another host`() {
        val certificate = certificateFor(SERVER_HOST)
        trustManager.addCertInTrustStore(SERVER_HOST, certificate)

        assertFalse(hostnameVerifier.verify(OTHER_HOST, sessionWith(certificate)))
    }

    private fun certificateFor(host: String): X509Certificate =
        HeldCertificate.Builder()
            .commonName(host)
            .addSubjectAlternativeName(host)
            .build()
            .certificate

    private fun sessionWith(certificate: X509Certificate): SSLSession {
        val sslSession = mock<SSLSession>()
        whenever(sslSession.peerCertificates).thenReturn(arrayOf(certificate))
        return sslSession
    }

    companion object {
        private const val SERVER_HOST = "cloud.example.com"
        private const val OTHER_HOST = "evil.example.org"
        private const val CACHE_SIZE = 1024L
    }
}
