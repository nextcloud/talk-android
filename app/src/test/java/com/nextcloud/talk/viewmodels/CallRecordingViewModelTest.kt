package com.nextcloud.talk.viewmodels

import com.nextcloud.talk.test.fakes.FakeCallRecordingRepository
import com.vividsolutions.jts.util.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

internal class CallRecordingViewModelTest {

    val repository = FakeCallRecordingRepository()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testCallRecordingViewModel_startRecord() {
        val viewModel = CallRecordingViewModel(repository)

        viewModel.clickRecordButton()

        // implement extension function for liveData to await value?!

        Assert.equals(CallRecordingViewModel.RecordingStartedState, viewModel.viewState)
    }
}
