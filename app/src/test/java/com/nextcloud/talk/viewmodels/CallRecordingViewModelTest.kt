package com.nextcloud.talk.viewmodels

import com.nextcloud.talk.test.fakes.FakeCallRecordingRepository
import com.vividsolutions.jts.util.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

class CallRecordingViewModelTest : AbstractViewModelTest() {

    val repository = FakeCallRecordingRepository()

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
    }

    @Test
    fun testCallRecordingViewModel_clickStopRecord() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)
        viewModel.clickRecordButton()

        Assert.equals(CallRecordingViewModel.RecordingConfirmStopState, viewModel.viewState.value)

        viewModel.stopRecording()

        Assert.equals(CallRecordingViewModel.RecordingStopLoadingState, viewModel.viewState.value)
    }

    @Test
    fun testCallRecordingViewModel_keepConfirmState() {
        val viewModel = CallRecordingViewModel(repository)
        viewModel.setData("foo")
        viewModel.setRecordingState(CallRecordingViewModel.RECORDING_STARTED_VIDEO_CODE)
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

        Assert.equals(CallRecordingViewModel.RecordingStartedState, viewModel.viewState.value)
    }
}
