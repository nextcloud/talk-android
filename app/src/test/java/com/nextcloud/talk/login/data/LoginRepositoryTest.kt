/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.login.data

import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class LoginRepositoryTest {

    // @get:Rule
    // val rule = InstantTaskExecutorRule()
    //
    // // Repository dependencies
    // @Mock lateinit var networkLoginDataSource: NetworkLoginDataSource
    // @Mock lateinit var localLoginDataSource: LocalLoginDataSource
    // lateinit var repo: LoginRepository
    //
    // @Before
    // fun setup() {
    //     MockitoAnnotations.openMocks(this)
    //
    //     repo = LoginRepository(networkLoginDataSource, localLoginDataSource)
    // }
    //
    // /**
    //  * QR Login Tests
    //  */
    //
    // @Test
    // fun `test startLoginFlowFromQr start account verification path`() = runTest {
    //     val expected = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expected)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expected)).thenReturn(false)
    //
    //     val mockDataURI = "nc://login/server:https://sermo.nextcloud.com&user:user1&password:asdjasd9810d01gd80b1dby87"
    //     repo.startLoginFlowFromQR(mockDataURI)
    //
    //     val bundle = repo.continueLoginFlow.first() // will throw error if empty
    //     assert(bundle.getString("KEY_USERNAME") == "user1")
    //     assert(bundle.getString("KEY_TOKEN") == "asdjasd9810d01gd80b1dby87")
    //     assert(bundle.getString("KEY_BASE_URL") == "https://sermo.nextcloud.com")
    //     assert(bundle.getString("KEY_ORIGINAL_PROTOCOL") == "https://")
    // }
    //
    // @Test
    // fun `test startLoginFlowFromQr malformed uri`() = runTest {
    //     val expected = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expected)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expected)).thenReturn(false)
    //
    //     try {
    //         val mockDataURI = "nc://login/server:https://sermo.nextcloud.com&user:user1"
    //         repo.startLoginFlowFromQR(mockDataURI)
    //
    //         assert(false) // Should not be reached
    //     } catch(_: IllegalArgumentException) {
    //         assert(true)
    //     }
    //
    //     try {
    //         val mockDataURI = "evil://login/server:https://sermo.nextcloud.com&user:user1&password:asdjasd9810d01gd80b1dby87"
    //         repo.startLoginFlowFromQR(mockDataURI)
    //
    //         assert(false) // Should not be reached
    //     } catch(_: IllegalArgumentException) {
    //         assert(true)
    //     }
    // }
    //
    // @Test
    // fun `test startLoginFlowFromQr user scheduled for deletion path`() = runTest {
    //     val expected = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expected)).thenReturn(true)
    //     val info = WorkInfo(
    //         id = UUID.fromString("NC"),
    //         state = WorkInfo.State.SUCCEEDED,
    //         tags = setOf()
    //     )
    //     val mockLive = MutableLiveData(info) as LiveData<WorkInfo?>
    //     `when`(localLoginDataSource.startAccountRemovalWorker()).thenReturn(mockLive)
    //
    //     val mockDataURI = "nc://login/server:https://sermo.nextcloud.com&user:user1&password:asdjasd9810d01gd80b1dby87"
    //     repo.startLoginFlowFromQR(mockDataURI)
    //
    //     val error = repo.errorFlow.first()
    //     assert(error == R.string.nc_common_error_sorry)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // @Test
    // fun `test startLoginFlowFromQr already exists and is reauthorized path`() = runTest {
    //     val expected = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expected)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expected)).thenReturn(true)
    //
    //     `when`(localLoginDataSource.updateUser(expected)).thenReturn(Unit)
    //
    //     val mockDataURI = "nc://login/server:https://sermo.nextcloud.com&user:user1&password:asdjasd9810d01gd80b1dby87"
    //     repo.startLoginFlowFromQR(mockDataURI, true)
    //
    //     // update user returns nothing, so this verifies that it was called with the expected parameters
    //     verify(localLoginDataSource).updateUser(expected)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // @Test
    // fun `test startLoginFlowFromQr already exists and is not reauthorized path`() = runTest {
    //     val expected = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expected)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expected)).thenReturn(true)
    //
    //     `when`(localLoginDataSource.updateUser(expected)).thenReturn(Unit)
    //
    //     val mockDataURI = "nc://login/server:https://sermo.nextcloud.com&user:user1&password:asdjasd9810d01gd80b1dby87"
    //     repo.startLoginFlowFromQR(mockDataURI, false)
    //
    //     // update user returns nothing, so this verifies that it was not called with the expected parameters
    //     verify(localLoginDataSource, never()).updateUser(expected)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // /**
    //  * Standard Login Tests
    //  */
    //
    // @Test
    // fun `test startLoginFlow start account verification path`() = runTest {
    //     val expectedCompletion = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expectedCompletion)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expectedCompletion)).thenReturn(false)
    //
    //     val expectedResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(expectedResponse)
    //     `when`(networkLoginDataSource.performLoginFlowV2(expectedResponse))
    //         .thenAnswer {
    //             Thread.sleep(1000L * 10)
    //             return@thenAnswer expectedCompletion
    //         }
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.launchWebFlow.first())
    //
    //     val bundle = repo.continueLoginFlow.first() // will throw error if empty
    //     assert(bundle.getString("KEY_USERNAME") == "user1")
    //     assert(bundle.getString("KEY_TOKEN") == "asdjasd9810d01gd80b1dby87")
    //     assert(bundle.getString("KEY_BASE_URL") == "https://sermo.nextcloud.com")
    //     assert(bundle.getString("KEY_ORIGINAL_PROTOCOL") == "https://")
    // }
    //
    // @Test
    // fun `test startLoginFlow network response null`() = runTest {
    //     val dummyResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(null)
    //     verify(networkLoginDataSource, never()).performLoginFlowV2(dummyResponse)
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.errorFlow.first())
    // }
    //
    // @Test
    // fun `test startLoginFlow user scheduled for deletion path`() = runTest {
    //     val expectedCompletion = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expectedCompletion)).thenReturn(true)
    //     val info = WorkInfo(
    //         id = UUID.fromString("NC"),
    //         state = WorkInfo.State.SUCCEEDED,
    //         tags = setOf()
    //     )
    //     val mockLive = MutableLiveData(info) as LiveData<WorkInfo?>
    //     `when`(localLoginDataSource.startAccountRemovalWorker()).thenReturn(mockLive)
    //
    //     val expectedResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(expectedResponse)
    //     `when`(networkLoginDataSource.performLoginFlowV2(expectedResponse))
    //         .thenAnswer {
    //             Thread.sleep(1000L * 10)
    //             return@thenAnswer expectedCompletion
    //         }
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.launchWebFlow.first())
    //
    //     val error = repo.errorFlow.first()
    //     assert(error == R.string.nc_common_error_sorry)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // @Test
    // fun `test startLoginFlow already exists and is reauthorized path`() = runTest {
    //     val expectedCompletion = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expectedCompletion)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expectedCompletion)).thenReturn(true)
    //
    //     `when`(localLoginDataSource.updateUser(expectedCompletion)).thenReturn(Unit)
    //
    //     val expectedResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(expectedResponse)
    //     `when`(networkLoginDataSource.performLoginFlowV2(expectedResponse))
    //         .thenAnswer {
    //             Thread.sleep(1000L * 10)
    //             return@thenAnswer expectedCompletion
    //         }
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.launchWebFlow.first())
    //
    //     // update user returns nothing, so this verifies that it was called with the expected parameters
    //     verify(localLoginDataSource).updateUser(expectedCompletion)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // @Test
    // fun `test startLoginFlow already exists and is not reauthorized path`() = runTest {
    //     val expectedCompletion = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expectedCompletion)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expectedCompletion)).thenReturn(true)
    //
    //     `when`(localLoginDataSource.updateUser(expectedCompletion)).thenReturn(Unit)
    //
    //     val expectedResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(expectedResponse)
    //     `when`(networkLoginDataSource.performLoginFlowV2(expectedResponse))
    //         .thenAnswer {
    //             Thread.sleep(1000L * 10)
    //             return@thenAnswer expectedCompletion
    //         }
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.launchWebFlow.first())
    //
    //     // update user returns nothing, so this verifies that it was not called with the expected parameters
    //     verify(localLoginDataSource, never()).updateUser(expectedCompletion)
    //
    //     val shouldRestart = repo.restartAppFlow.first()
    //     assert(shouldRestart)
    // }
    //
    // @Test
    // fun `test startLoginFlow pollLogin slow connection test`() = runTest {
    //     val expectedCompletion = NetworkLoginDataSource.LoginCompletion(
    //         status = 200,
    //         server = "https://sermo.nextcloud.com",
    //         loginName = "user1",
    //         appPassword = "asdjasd9810d01gd80b1dby87"
    //     )
    //     `when`(localLoginDataSource.checkIfUserIsScheduledForDeletion(expectedCompletion)).thenReturn(false)
    //     `when`(localLoginDataSource.checkIfUserExists(expectedCompletion)).thenReturn(false)
    //
    //     val expectedResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/poll",
    //         loginUrl = "https:\\/\\/sermo.nextcloud.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val mockUrl = "https://sermo.nextcloud.com/index.php/login/v2"
    //
    //     `when`(networkLoginDataSource.anonymouslyPostLoginRequest(mockUrl)).thenReturn(expectedResponse)
    //     `when`(networkLoginDataSource.performLoginFlowV2(expectedResponse))
    //         .thenAnswer {
    //             Thread.sleep(1000L * 40) // 40 seconds, so past interval
    //             return@thenAnswer expectedCompletion
    //         }
    //
    //     repo.startLoginFlow(mockUrl)
    //
    //     assertNotNull(repo.launchWebFlow.first())
    //
    //     val bundle = repo.continueLoginFlow.first() // will throw error if empty
    //     assert(bundle.getString("KEY_USERNAME") == "user1")
    //     assert(bundle.getString("KEY_TOKEN") == "asdjasd9810d01gd80b1dby87")
    //     assert(bundle.getString("KEY_BASE_URL") == "https://sermo.nextcloud.com")
    //     assert(bundle.getString("KEY_ORIGINAL_PROTOCOL") == "https://")
    // }
}
