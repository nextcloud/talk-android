/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2024 Ricki Hirner (bitfire web engineering)
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl

import java.net.InetAddress
import java.net.Socket
import java.security.GeneralSecurityException
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class SSLSocketFactoryCompat(keyManager: KeyManager?, trustManager: X509TrustManager) : SSLSocketFactory() {

    private var delegate: SSLSocketFactory

    companion object {
        // Android 5.0+ (API level 21) provides reasonable default settings
        // but it still allows SSLv3
        // https://developer.android.com/reference/javax/net/ssl/SSLSocket.html
        var protocols: Array<String>? = null
        var cipherSuites: Array<String>? = null

        init {
            // Since Android 6.0 (API level 23),
            // - TLSv1.1 and TLSv1.2 is enabled by default
            // - SSLv3 is disabled by default
            // - all modern ciphers are activated by default
            protocols = null
            cipherSuites = null
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

    override fun getDefaultCipherSuites(): Array<String>? = cipherSuites ?: delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String>? = cipherSuites ?: delegate.supportedCipherSuites

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        val ssl = delegate.createSocket(s, host, port, autoClose)
        if (ssl is SSLSocket) {
            upgradeTLS(ssl)
        }
        return ssl
    }

    override fun createSocket(host: String, port: Int): Socket {
        val ssl = delegate.createSocket(host, port)
        if (ssl is SSLSocket) {
            upgradeTLS(ssl)
        }
        return ssl
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        val ssl = delegate.createSocket(host, port, localHost, localPort)
        if (ssl is SSLSocket) {
            upgradeTLS(ssl)
        }
        return ssl
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        val ssl = delegate.createSocket(host, port)
        if (ssl is SSLSocket) {
            upgradeTLS(ssl)
        }
        return ssl
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket {
        val ssl = delegate.createSocket(address, port, localAddress, localPort)
        if (ssl is SSLSocket) {
            upgradeTLS(ssl)
        }
        return ssl
    }

    private fun upgradeTLS(ssl: SSLSocket) {
        protocols?.let { ssl.enabledProtocols = it }
        cipherSuites?.let { ssl.enabledCipherSuites = it }
    }
}
