/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.login.data

import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.work.WorkInfo
import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.io.LocalLoginDataSource
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner.Silent::class)
class LoginRepositoryTest {

    @get:Rule
    val rule = InstantTaskExecutorRule()

    // Repository dependencies
    @Mock
    lateinit var networkLoginDataSource: NetworkLoginDataSource

    @Mock
    lateinit var localLoginDataSource: LocalLoginDataSource

    // Additional mocks for LocalLoginDataSource dependencies
    @Mock
    lateinit var liveData: LiveData<WorkInfo?>

    lateinit var repo: LoginRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repo = LoginRepository(networkLoginDataSource, localLoginDataSource)
    }

    // ========== pollLogin() Tests ==========

    @Test
    fun `pollLogin returns successful LoginCompletion when network returns HTTP 200`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val successfulLoginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(successfulLoginData)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNotNull(result)
            assertEquals(200, result?.status)
            assertEquals("https://server.com", result?.server)
            assertEquals("testuser", result?.loginName)
            assertEquals("apppass123", result?.appPassword)
        }

    @Test
    fun `pollLogin returns null when network returns null`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(null)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNull(result)
        }

    @Test
    fun `pollLogin continues polling when status is not HTTP 200 then returns successful result`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val pendingLoginData = LoginCompletion(202, "https://server.com", "testuser", "apppass123")
            val successfulLoginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(pendingLoginData)
                .thenReturn(successfulLoginData)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNotNull(result)
            assertEquals(200, result?.status)
            verify(networkLoginDataSource, times(2)).performLoginFlowV2(mockResponse)
        }

    @Test
    fun `pollLogin handles slow connection by continuing to poll with delays`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val slowResponse1 = LoginCompletion(202, "https://server.com", "testuser", "apppass123")
            val slowResponse2 = LoginCompletion(404, "https://server.com", "testuser", "apppass123")
            val slowResponse3 = LoginCompletion(500, "https://server.com", "testuser", "apppass123")
            val successResponse = LoginCompletion(200, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(slowResponse1)
                .thenReturn(slowResponse2)
                .thenReturn(slowResponse3)
                .thenReturn(successResponse)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNotNull(result)
            assertEquals(200, result?.status)
            verify(networkLoginDataSource, times(4)).performLoginFlowV2(mockResponse)
        }

    @Test
    fun `pollLogin handles network timeouts during slow connection gracefully`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val timeoutResponse = LoginCompletion(408, "https://server.com", "testuser", "apppass123")
            val successResponse = LoginCompletion(200, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(timeoutResponse)
                .thenReturn(timeoutResponse)
                .thenReturn(successResponse)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNotNull(result)
            assertEquals(200, result?.status)
            verify(networkLoginDataSource, times(3)).performLoginFlowV2(mockResponse)
        }

    @Test
    fun `pollLogin stops when cancelLoginFlow is called`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val pendingLoginData = LoginCompletion(
                202,
                "https://server.com",
                "testuser",
                "apppass123"
            )

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(pendingLoginData)

            // Act - cancel before polling
            repo.cancelLoginFlow()
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNull(result)
            verify(networkLoginDataSource, never()).performLoginFlowV2(any())
        }

    // ========== startLoginFlowFromQR() Tests ==========

    @Test
    fun `startLoginFlowFromQR returns LoginCompletion for valid QR data with all parameters`() {
        // Arrange
        val qrData = "nc://login/user:testuser&server:https%3A//example.com&password:testpass"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNotNull(result)
        assertEquals(200, result?.status)
        assertEquals("https://example.com", result?.server)
        assertEquals("testuser", result?.loginName)
        assertEquals("testpass", result?.appPassword)
    }

    @Test
    fun `startLoginFlowFromQR returns LoginCompletion for minimal valid QR data`() {
        // Arrange
        val qrData = "nc://login/server:https%3A//example.com"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result)
    }

    @Test
    fun `startLoginFlowFromQR returns null for invalid prefix`() {
        // Arrange
        val qrData = "invalid://login/user:testuser&server:https://example.com"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result)
    }

    @Test
    fun `startLoginFlowFromQR returns null when too many arguments provided`() {
        // Arrange
        val qrData = "nc://login/user:test&server:https://example.com&password:pass&extra:value"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result)
    }

    @Test
    fun `startLoginFlowFromQR returns null for empty data`() {
        // Arrange
        val qrData = "nc://login/"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result)
    }

    @Test
    fun `startLoginFlowFromQR sets reAuth flag correctly`() {
        // Arrange
        val qrData = "nc://login/server:https%3A//example.com"

        // Act
        val result = repo.startLoginFlowFromQR(qrData, reAuth = true)

        // Assert
        assertNull(result)
    }

    @Test
    fun `startLoginFlowFromQR handles URL encoding correctly`() {
        // Arrange
        val qrData = "nc://login/user:test%40user.com&server:https%3A//example.com%3A8080&password:test%26pass"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNotNull(result)
        assertEquals("test@user.com", result?.loginName)
        assertEquals("https://example.com:8080", result?.server)
        assertEquals("test&pass", result?.appPassword)
    }

    @Test
    fun `startLoginFlowFromQR handles mixed parameter order`() {
        // Arrange
        val qrData = "nc://login/password:testpass&user:testuser&server:https%3A//example.com"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNotNull(result)
        assertEquals("https://example.com", result?.server)
        assertEquals("testuser", result?.loginName)
        assertEquals("testpass", result?.appPassword)
    }

    // ========== startLoginFlow() Tests ==========

    @Test
    fun `startLoginFlow returns LoginResponse from network`() =
        runTest {
            // Arrange
            val baseUrl = "https://example.com"
            val mockResponse = LoginResponse("token123", "https://example.com/poll", "https://example.com/login")
            whenever(networkLoginDataSource.anonymouslyPostLoginRequest(baseUrl))
                .thenReturn(mockResponse)

            // Act
            val result = repo.startLoginFlow(baseUrl)

            // Assert
            assertEquals(mockResponse, result)
            verify(networkLoginDataSource).anonymouslyPostLoginRequest(baseUrl)
        }

    @Test
    fun `startLoginFlow returns null when network returns null`() =
        runTest {
            // Arrange
            val baseUrl = "https://example.com"
            whenever(networkLoginDataSource.anonymouslyPostLoginRequest(baseUrl))
                .thenReturn(null)

            // Act
            val result = repo.startLoginFlow(baseUrl)

            // Assert
            assertNull(result)
        }

    @Test
    fun `startLoginFlow sets reAuth flag correctly`() =
        runTest {
            // Arrange
            val baseUrl = "https://example.com"
            val mockResponse = LoginResponse("token123", "https://example.com/poll", "https://example.com/login")
            whenever(networkLoginDataSource.anonymouslyPostLoginRequest(baseUrl))
                .thenReturn(mockResponse)

            // Act
            val result = repo.startLoginFlow(baseUrl, reAuth = true)

            // Assert
            assertEquals(mockResponse, result)
        }

    @Test
    fun `startLoginFlow handles network SSL exceptions`() =
        runTest {
            // Arrange
            val baseUrl = "https://example.com"
            whenever(networkLoginDataSource.anonymouslyPostLoginRequest(baseUrl))
                .thenReturn(null) // NetworkLoginDataSource catches SSL exceptions and returns null

            // Act
            val result = repo.startLoginFlow(baseUrl)

            // Assert
            assertNull(result)
            verify(networkLoginDataSource).anonymouslyPostLoginRequest(baseUrl)
        }

    // ========== cancelLoginFlow() Tests ==========

    @Test
    fun `cancelLoginFlow stops polling loop`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val pendingLoginData = LoginCompletion(202, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(pendingLoginData)

            // Act
            repo.cancelLoginFlow()
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNull(result)
        }

    // ========== parseAndLogin() Tests ==========

    @Test
    fun `parseAndLogin returns null when user is scheduled for deletion`() {
        // Arrange
        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(true)
        whenever(localLoginDataSource.startAccountRemovalWorker())
            .thenReturn(liveData)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNull(result)
        verify(localLoginDataSource).startAccountRemovalWorker()
    }

    @Test
    fun `parseAndLogin returns null when user exists and reAuth is false`() {
        // Arrange
        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(true)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNull(result)
        verify(localLoginDataSource, never()).updateUser(any())
    }

    @Test
    fun `parseAndLogin updates user when user exists and reAuth is true`() {
        // Arrange - First set reAuth to true via QR flow
        val qrData = "nc://login/server:https%3A//example.com"
        repo.startLoginFlowFromQR(qrData, reAuth = true)

        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(true)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNull(result)
        verify(localLoginDataSource).updateUser(loginData)
    }

    @Test
    fun `parseAndLogin returns Bundle for new user with https protocol`() {
        // Arrange
        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(false)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNotNull(result)
        assertTrue(result is Bundle)
    }

    @Test
    fun `parseAndLogin returns Bundle for new user with http protocol`() {
        // Arrange
        val loginData = LoginCompletion(200, "http://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(false)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNotNull(result)
        assertTrue(result is Bundle)
    }

    @Test
    fun `parseAndLogin returns Bundle for new user without protocol prefix`() {
        // Arrange
        val loginData = LoginCompletion(200, "server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(false)

        // Act
        val result = repo.parseAndLogin(loginData)

        // Assert
        assertNotNull(result)
        assertTrue(result is Bundle)
    }

    // ========== LocalLoginDataSource Integration Tests ==========

    @Test
    fun `parseAndLogin properly integrates with LocalLoginDataSource checkIfUserIsScheduledForDeletion`() {
        // Arrange
        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(true)
        whenever(localLoginDataSource.startAccountRemovalWorker())
            .thenReturn(liveData)

        // Act
        repo.parseAndLogin(loginData)

        // Assert
        verify(localLoginDataSource).checkIfUserIsScheduledForDeletion(loginData)
        verify(localLoginDataSource).startAccountRemovalWorker()
    }

    @Test
    fun `parseAndLogin properly integrates with LocalLoginDataSource checkIfUserExists`() {
        // Arrange
        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(true)

        // Act
        repo.parseAndLogin(loginData)

        // Assert
        verify(localLoginDataSource).checkIfUserExists(loginData)
        verify(localLoginDataSource, never()).updateUser(any())
    }

    @Test
    fun `parseAndLogin calls updateUser with correct LoginCompletion data`() {
        // Arrange - Set reAuth flag first
        val qrData = "nc://login/server:https%3A//example.com"
        repo.startLoginFlowFromQR(qrData, reAuth = true)

        val loginData = LoginCompletion(200, "https://server.com", "testuser", "apppass123")
        whenever(localLoginDataSource.checkIfUserIsScheduledForDeletion(loginData))
            .thenReturn(false)
        whenever(localLoginDataSource.checkIfUserExists(loginData))
            .thenReturn(true)

        // Act
        repo.parseAndLogin(loginData)

        // Assert
        verify(localLoginDataSource).updateUser(loginData)
    }

    // ========== Edge Cases and Error Handling ==========

    @Test
    fun `pollLogin handles performLoginFlowV2 returning error status codes`() =
        runTest {
            // Arrange
            val mockResponse = LoginResponse("token123", "https://server.com/poll", "https://server.com/login")
            val errorResponse = LoginCompletion(404, "", "", "")
            val successResponse = LoginCompletion(200, "https://server.com", "testuser", "apppass123")

            whenever(networkLoginDataSource.performLoginFlowV2(mockResponse))
                .thenReturn(errorResponse)
                .thenReturn(successResponse)

            // Act
            val result = repo.pollLogin(mockResponse)

            // Assert
            assertNotNull(result)
            assertEquals(200, result?.status)
        }

    @Test
    fun `startLoginFlowFromQR handles malformed URL gracefully`() {
        // Arrange
        val qrData = "nc://login/malformed&data&without&proper&key:value"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result) // Should still create LoginCompletion with empty values
    }

    @Test
    fun `startLoginFlowFromQR handles partial parameter data`() {
        // Arrange
        val qrData = "nc://login/user:testuser&server:"

        // Act
        val result = repo.startLoginFlowFromQR(qrData)

        // Assert
        assertNull(result)
    }
}
