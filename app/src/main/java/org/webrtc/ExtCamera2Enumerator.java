package org.webrtc;

import android.content.Context;
import android.hardware.camera2.CameraManager;

import org.jetbrains.annotations.Nullable;

public class ExtCamera2Enumerator extends Camera2Enumerator {

    final Context context;
    @Nullable final CameraManager cameraManager;
    private final boolean disableEIS, zoomOut;

    public ExtCamera2Enumerator(Context context, boolean disableEIS, boolean zoomOut) {
        super(context);
        this.context = context;
        this.disableEIS = disableEIS;
        this.zoomOut = zoomOut;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public CameraVideoCapturer createCapturer(String deviceName,
                                              CameraVideoCapturer.CameraEventsHandler eventsHandler) {
        return new ExtCamera2Capturer(context, deviceName, eventsHandler, disableEIS, zoomOut);
    }
}
