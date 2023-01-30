package com.nextcloud.talk.viewmodels

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nextcloud.talk.test.fakes.FakeCallRecordingRepository
import com.vividsolutions.jts.util.Assert
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

class CallRecordingViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    val repository = FakeCallRecordingRepository()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testCallRecordingViewModel_startRecord() {
        val viewModel = CallRecordingViewModel(repository)

        viewModel.setData("foo")

        viewModel.clickRecordButton()

        // implement extension function for liveData to await value?!

        Assert.equals(CallRecordingViewModel.RecordingStartLoadingState, viewModel.viewState.value)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            RxAndroidPlugins.setInitMainThreadSchedulerHandler {
                Schedulers.trampoline()
            }
        }
    }
}
