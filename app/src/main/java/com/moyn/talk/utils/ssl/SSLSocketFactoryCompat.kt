/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.moyn.talk.utils.ssl

import android.os.Build
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.GeneralSecurityException
import java.util.LinkedList
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class SSLSocketFactoryCompat(
    keyManager: KeyManager?,
    trustManager: X509TrustManager
) : SSLSocketFactory() {

    private var delegate: SSLSocketFactory

    companion object {
        // Android 5.0+ (API level 21) provides reasonable default settings
        // but it still allows SSLv3
        // https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
        var protocols: Array<String>? = null
        var cipherSuites: Array<String>? = null

        init {
            if (Build.VERSION.SDK_INT >= 23) {
                // Since Android 6.0 (API level 23),
                // - TLSv1.1 and TLSv1.2 is enabled by default
                // - SSLv3 is disabled by default
                // - all modern ciphers are activated by default
                protocols = null
                cipherSuites = null
            } else {
                val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket?
                try {
                    socket?.let {
                        /* set reasonable protocol versions */
                        // - enable all supported protocols (enables TLSv1.1 and TLSv1.2 on Android <5.0)
                        // - remove all SSL versions (especially SSLv3) because they're insecure now
                        val _protocols = LinkedList<String>()
                        for (protocol in socket.supportedProtocols.filterNot { it.contains("SSL", true) })
                            _protocols += protocol
                        protocols = _protocols.toTypedArray()

                        /* set up reasonable cipher suites */
                        val knownCiphers = arrayOf<String>(
                            // TLS 1.2
                            "TLS_RSA_WITH_AES_256_GCM_SHA384",
                            "TLS_RSA_WITH_AES_128_GCM_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
                            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                            // maximum interoperability
                            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
                            "SSL_RSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_RSA_WITH_AES_128_CBC_SHA",
                            // additionally
                            "TLS_RSA_WITH_AES_256_CBC_SHA",
                            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
                            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
                            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"
                        )
                        val availableCiphers = socket.supportedCipherSuites

                        /* For maximum security, preferredCiphers should *replace* enabled ciphers (thus
                         * disabling ciphers which are enabled by default, but have become unsecure), but for
                         * the security level of DAVdroid and maximum compatibility, disabling of insecure
                         * ciphers should be a server-side task */

                        // for the final set of enabled ciphers, take the ciphers enabled by default, ...
                        val _cipherSuites = LinkedList<String>()
                        _cipherSuites.addAll(socket.enabledCipherSuites)
                        // ... add explicitly allowed ciphers ...
                        _cipherSuites.addAll(knownCiphers)
                        // ... and keep only those which are actually available
                        _cipherSuites.retainAll(availableCiphers)

                        cipherSuites = _cipherSuites.toTypedArray()
                    }
                } catch (e: IOException) {
                    // Exception is to be ignored
                } finally {
                    socket?.close() // doesn't implement Closeable on all supported Android versions
                }
            }
        }
    }

    init {
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(
                if (keyManager != null) arrayOf(keyManager) else null,
                arrayOf(trustManager),
                null
            )
            delegate = sslContext.socketFactory
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException() // system has no TLS
        }
    }

    override fun getDefaultCipherSuites(): Array<String>? = cipherSuites
        ?: delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String>? = cipherSuites
        ?: delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val ssl = delegate.createSocket(s, host, port, autoClose)
        if (ssl is SSLSocket)
            upgradeTLS(ssl)
        return ssl
    }

    override fun createSocket(host: String, port: Int): Socket {
        val ssl = delegate.createSocket(host, port)
        if (ssl is SSLSocket)
            upgradeTLS(ssl)
        return ssl
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val ssl = delegate.createSocket(host, port, localHost, localPort)
        if (ssl is SSLSocket)
            upgradeTLS(ssl)
        return ssl
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val ssl = delegate.createSocket(host, port)
        if (ssl is SSLSocket)
            upgradeTLS(ssl)
        return ssl
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val ssl = delegate.createSocket(address, port, localAddress, localPort)
        if (ssl is SSLSocket)
            upgradeTLS(ssl)
        return ssl
    }

    private fun upgradeTLS(ssl: SSLSocket) {
        protocols?.let { ssl.enabledProtocols = it }
        cipherSuites?.let { ssl.enabledCipherSuites = it }
    }
}
