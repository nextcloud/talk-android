package com.nextcloud.talk.viewmodels

import com.nextcloud.talk.test.fakes.FakeCallRecordingRepository
import com.vividsolutions.jts.util.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class CallRecordingViewModelTest : AbstractViewModelTest() {

    private val repository = FakeCallRecordingRepository()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testCallRecordingViewModel_clickStartRecord() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.clickRecordButton()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingStartingState)

        // fake to execute setRecordingState which would be triggered by signaling message
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingStartedState)
    }

    @Test
    fun testCallRecordingViewModel_clickStopRecord() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.equals(true, (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo)

        viewModel.clickRecordButton()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingConfirmStopState)

        viewModel.stopRecording()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingStoppedState)
    }

    @Test
    fun testCallRecordingViewModel_keepConfirmState() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.equals(true, (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo)

        viewModel.clickRecordButton()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingConfirmStopState)

        viewModel.clickRecordButton()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingConfirmStopState)
    }

    @Test
    fun testCallRecordingViewModel_continueRecordingWhenDismissStopDialog() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)
        viewModel.clickRecordButton()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingConfirmStopState)

        viewModel.dismissStopRecording()

        Assert.isTrue(viewModel.viewState.value is CallRecordingViewModel.RecordingStartedState)

        Assert.equals(
            false,
            (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo
        )
    }
}
