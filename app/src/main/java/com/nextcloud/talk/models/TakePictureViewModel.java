/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Stefan Niedermann <info@niedermann.it>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models;

import com.nextcloud.talk.R;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import static androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA;
import static androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA;

public class TakePictureViewModel extends ViewModel {

    @NonNull
    private CameraSelector cameraSelector = DEFAULT_BACK_CAMERA;

    @NonNull
    private final MutableLiveData<Boolean> torchEnabled = new MutableLiveData<>(Boolean.FALSE);

    @NonNull
    private final MutableLiveData<Boolean> lowResolutionEnabled = new MutableLiveData<>(Boolean.FALSE);

    @NonNull
    private final MutableLiveData<Boolean> cropEnabled = new MutableLiveData<>(Boolean.FALSE);

    @NonNull
    public CameraSelector getCameraSelector() {
        return this.cameraSelector;
    }

    public void toggleCameraSelector() {
        if (this.cameraSelector == DEFAULT_BACK_CAMERA) {
            this.cameraSelector = DEFAULT_FRONT_CAMERA;
            if (this.torchEnabled.getValue()) {
                toggleTorchEnabled();
            }
        } else {
            this.cameraSelector = DEFAULT_BACK_CAMERA;
        }
    }

    public void disableTorchIfEnabled() {
        if (this.torchEnabled.getValue()) {
            toggleTorchEnabled();
        }
    }

    public void toggleTorchEnabled() {
        //noinspection ConstantConditions
        this.torchEnabled.postValue(!this.torchEnabled.getValue());
    }

    public void toggleLowResolutionEnabled() {
        //noinspection ConstantConditions
        this.lowResolutionEnabled.postValue(!this.lowResolutionEnabled.getValue());
    }

    public void toggleCropEnabled() {
        //noinspection ConstantConditions
        this.cropEnabled.postValue(!this.cropEnabled.getValue());
    }

    public LiveData<Boolean> isTorchEnabled() {
        return this.torchEnabled;
    }

    public LiveData<Boolean> isLowResolutionEnabled() {
        return this.lowResolutionEnabled;
    }

    public LiveData<Boolean> isCropEnabled() {
        return this.cropEnabled;
    }

    public LiveData<Integer> getTorchToggleButtonImageResource() {
        return Transformations.map(isTorchEnabled(), enabled -> enabled
            ? R.drawable.ic_baseline_flash_on_24
            : R.drawable.ic_baseline_flash_off_24);
    }

    public LiveData<Integer> getLowResolutionToggleButtonImageResource() {
        return Transformations.map(isLowResolutionEnabled(), enabled -> enabled
            ? R.drawable.ic_low_quality
            : R.drawable.ic_high_quality);
    }

    public LiveData<Integer> getCropToggleButtonImageResource() {
        return Transformations.map(isCropEnabled(), enabled -> enabled
            ? R.drawable.ic_crop_16_9
            : R.drawable.ic_crop_4_3);
    }
}
