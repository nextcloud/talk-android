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

        Assert.equals(CallRecordingViewModel.RecordingStartLoadingState, viewModel.viewState.value)

        // fake to execute setRecordingState which would be triggered by signaling message
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.equals(
            CallRecordingViewModel.RecordingStartedState(true).javaClass,
            viewModel.viewState.value?.javaClass
        )
    }

    @Test
    fun testCallRecordingViewModel_clickStopRecord() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.equals(true, (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo)

        viewModel.clickRecordButton()

        Assert.equals(CallRecordingViewModel.RecordingConfirmStopState, viewModel.viewState.value)

        viewModel.stopRecording()

        Assert.equals(CallRecordingViewModel.RecordingStoppedState, viewModel.viewState.value)
    }

    @Test
    fun testCallRecordingViewModel_keepConfirmState() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)

        Assert.equals(true, (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo)

        viewModel.clickRecordButton()

        Assert.equals(CallRecordingViewModel.RecordingConfirmStopState, viewModel.viewState.value)

        viewModel.clickRecordButton()

        Assert.equals(CallRecordingViewModel.RecordingConfirmStopState, viewModel.viewState.value)
    }

    @Test
    fun testCallRecordingViewModel_continueRecordingWhenDismissStopDialog() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)
        viewModel.clickRecordButton()

        Assert.equals(CallRecordingViewModel.RecordingConfirmStopState, viewModel.viewState.value)

        viewModel.dismissStopRecording()

        Assert.equals(
            CallRecordingViewModel.RecordingStartedState(false).javaClass,
            viewModel.viewState.value?.javaClass
        )
        Assert.equals(
            false,
            (viewModel.viewState.value as CallRecordingViewModel.RecordingStartedState).showStartedInfo
        )
    }
}
