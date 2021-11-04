/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Stefan Niedermann
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Stefan Niedermann <info@niedermann.it>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.nextcloud.talk.R;
import com.nextcloud.talk.databinding.ActivityTakePictureBinding;
import com.nextcloud.talk.models.TakePictureViewModel;
import com.nextcloud.talk.utils.BitmapShrinker;
import com.nextcloud.talk.utils.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.ViewModelProvider;

public class TakePhotoActivity extends AppCompatActivity {

    private static final String TAG = TakePhotoActivity.class.getSimpleName();

    private static final float MAX_SCALE = 6.0f;
    private static final float MEDIUM_SCALE = 2.45f;

    private ActivityTakePictureBinding binding;
    private TakePictureViewModel viewModel;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private OrientationEventListener orientationEventListener;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT);

    private Camera camera;
    private boolean crop = false, lowres = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTakePictureBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(TakePictureViewModel.class);

        setContentView(binding.getRoot());

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                final ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                camera = cameraProvider.bindToLifecycle(
                    this,
                    viewModel.getCameraSelector(),
                    getImageCapture(false, false),
                    getPreview(false));

                viewModel.getTorchToggleButtonImageResource()
                    .observe(
                        this,
                        res -> binding.toggleTorch.setIcon(ContextCompat.getDrawable(this, res)));
                viewModel.isTorchEnabled()
                    .observe(
                        this,
                        enabled -> camera.getCameraControl().enableTorch(enabled));
                binding.toggleTorch.setOnClickListener((v) -> viewModel.toggleTorchEnabled());

                binding.toggleCrop.setOnClickListener((v) -> {
                    crop = !crop;
                    binding.toggleCrop.setIcon(ContextCompat.getDrawable(this, crop ?
                        R.drawable.ic_baseline_flash_on_24 : R.drawable.ic_baseline_flash_off_24));
                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        viewModel.getCameraSelector(),
                        getImageCapture(crop, lowres),
                        getPreview(crop));
                });

                binding.toggleLowres.setOnClickListener((v) -> {
                    lowres = !lowres;
                    binding.toggleLowres.setIcon(ContextCompat.getDrawable(this, lowres ?
                        R.drawable.ic_baseline_flash_on_24 : R.drawable.ic_baseline_flash_off_24));
                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        viewModel.getCameraSelector(),
                        getImageCapture(crop, lowres),
                        getPreview(crop));
                });

                binding.switchCamera.setOnClickListener((v) -> {
                    viewModel.toggleCameraSelector();
                    cameraProvider.unbindAll();
                    camera = cameraProvider.bindToLifecycle(
                        this,
                        viewModel.getCameraSelector(),
                        getImageCapture(crop, lowres),
                        getPreview(crop));
                });
                binding.retake.setOnClickListener((v) -> {
                    Uri uri = (Uri) binding.photoPreview.getTag();
                    File photoFile = new File(uri.getPath());
                    if (!photoFile.delete()) {
                        Log.w(TAG, "Error deleting temp camera image");
                    }
                    binding.takePhoto.setEnabled(true);
                    binding.photoPreview.setTag(null);
                    showCameraElements();
                });
                binding.send.setOnClickListener((v) -> {
                    Uri uri = (Uri) binding.photoPreview.getTag();
                    setResult(RESULT_OK, new Intent().setDataAndType(uri, "image/jpeg"));
                    binding.photoPreview.setTag(null);
                    finish();
                });

                ScaleGestureDetector mDetector =
                    new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                        @Override
                        public boolean onScale(ScaleGestureDetector detector){
                            float ratio = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                            float delta = detector.getScaleFactor();
                            camera.getCameraControl().setZoomRatio(ratio * delta);
                            return true;
                        }
                    });
                binding.preview.setOnTouchListener((v, event) -> {
                    v.performClick();
                    mDetector.onTouchEvent(event);
                    return true;
                });

                // Enable enlarging the image more than default 3x maximumScale.
                // Medium scale adapted to make double-tap behaviour more consistent.
                binding.photoPreview.setMaximumScale(MAX_SCALE);
                binding.photoPreview.setMediumScale(MEDIUM_SCALE);
            } catch (IllegalArgumentException | ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error taking picture", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void onBackPressed() {
        Uri uri = (Uri) binding.photoPreview.getTag();

        if (uri != null) {
            File photoFile = new File(uri.getPath());
            if (!photoFile.delete()) {
                Log.w(TAG, "Error deleting temp camera image");
            }
            binding.photoPreview.setTag(null);
        }

        super.onBackPressed();
    }

    private void showCameraElements() {
        binding.send.setVisibility(View.GONE);
        binding.retake.setVisibility(View.GONE);
        binding.photoPreview.setVisibility(View.INVISIBLE);

        binding.preview.setVisibility(View.VISIBLE);
        binding.takePhoto.setVisibility(View.VISIBLE);
        binding.switchCamera.setVisibility(View.VISIBLE);
        binding.toggleTorch.setVisibility(View.VISIBLE);
    }

    private void showPictureProcessingElements() {
        binding.preview.setVisibility(View.INVISIBLE);
        binding.takePhoto.setVisibility(View.GONE);
        binding.switchCamera.setVisibility(View.GONE);
        binding.toggleTorch.setVisibility(View.GONE);

        binding.send.setVisibility(View.VISIBLE);
        binding.retake.setVisibility(View.VISIBLE);
        binding.photoPreview.setVisibility(View.VISIBLE);
    }

    private ImageCapture getImageCapture(boolean crop, boolean lowres) {
        final ImageCapture imageCapture;
        if (lowres) imageCapture = new ImageCapture.Builder()
            .setTargetResolution(new Size(crop ? 1080 : 1440, 1920)).build();
        else imageCapture = new ImageCapture.Builder()
            .setTargetAspectRatio(crop ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3).build();

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation;

                // Monitors orientation values to determine the target rotation value
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }

                imageCapture.setTargetRotation(rotation);
            }
        };
        orientationEventListener.enable();

        binding.takePhoto.setOnClickListener((v) -> {
            binding.takePhoto.setEnabled(false);
            final String photoFileName = dateFormat.format(new Date()) + ".jpg";
            try {
                final File photoFile = FileUtils.getTempCacheFile(this, "photos/" + photoFileName);
                final ImageCapture.OutputFileOptions options =
                    new ImageCapture.OutputFileOptions.Builder(photoFile).build();
                imageCapture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(this),
                    new ImageCapture.OnImageSavedCallback() {

                        @Override
                        public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                            setPreviewImage(photoFile);
                            showPictureProcessingElements();
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException e) {
                            Log.e(TAG, "Error", e);

                            if (!photoFile.delete()) {
                                Log.w(TAG, "Deleting picture failed");
                            }
                            binding.takePhoto.setEnabled(true);
                        }
                    });
            } catch (Exception e) {
                Toast.makeText(this, R.string.take_photo_error_deleting_picture, Toast.LENGTH_SHORT).show();
            }
        });

        return imageCapture;
    }

    private void setPreviewImage(File photoFile) {
        final Uri savedUri = Uri.fromFile(photoFile);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int doubleScreenWidth = displayMetrics.widthPixels * 2;
        int doubleScreenHeight = displayMetrics.heightPixels * 2;

        Bitmap bitmap = BitmapShrinker.shrinkBitmap(photoFile.getAbsolutePath(),
                                                    doubleScreenWidth,
                                                    doubleScreenHeight);

        binding.photoPreview.setImageBitmap(bitmap);
        binding.photoPreview.setTag(savedUri);
    }

    public int getImageOrientation(File imageFile) {
        int rotate = 0;
        try {
            ExifInterface exif = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                default:
                    rotate = 0;
                    break;
            }

            Log.i(TAG, "ImageOrientation - Exif orientation: " + orientation + " - " + "Rotate value: " + rotate);
        } catch (Exception e) {
            Log.w(TAG, "Error calculation rotation value");
        }
        return rotate;
    }

    private Preview getPreview(boolean crop) {
        Preview preview = new Preview.Builder()
            .setTargetAspectRatio(crop ? AspectRatio.RATIO_16_9 : AspectRatio.RATIO_4_3).build();
        preview.setSurfaceProvider(binding.preview.getSurfaceProvider());

        return preview;
    }

    @Override
    protected void onPause() {
        if (this.orientationEventListener != null) {
            this.orientationEventListener.disable();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.orientationEventListener != null) {
            this.orientationEventListener.enable();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (binding.photoPreview.getTag() != null) {
            savedInstanceState.putString("Uri", ((Uri) binding.photoPreview.getTag()).getPath());
        }

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        String uri = savedInstanceState.getString("Uri", null);

        if (uri != null) {
            File photoFile = new File(uri);
            setPreviewImage(photoFile);
            showPictureProcessingElements();
        }
    }

    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, TakePhotoActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
