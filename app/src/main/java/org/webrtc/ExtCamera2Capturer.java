package org.webrtc;

import android.content.Context;
import android.hardware.camera2.CameraManager;

import org.jetbrains.annotations.Nullable;

public class ExtCamera2Capturer extends Camera2Capturer {

    @Nullable private final CameraManager cameraManager;
    private final boolean disableEIS, zoomOut;

    public ExtCamera2Capturer(Context context, String cameraName, CameraEventsHandler eventsHandler,
                              boolean disableEIS, boolean zoomOut) {
        super(context, cameraName, eventsHandler);
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.disableEIS = disableEIS;
        this.zoomOut = zoomOut;
    }

    @Override
    protected void createCameraSession(CameraSession.CreateSessionCallback createSessionCallback,
                                       CameraSession.Events events, Context applicationContext,
                                       SurfaceTextureHelper surfaceTextureHelper, String cameraName, int width, int height,
                                       int framerate) {

        CameraSession.CreateSessionCallback myCallback = new CameraSession.CreateSessionCallback() {
            @Override
            public void onDone(CameraSession cameraSession) {
                createSessionCallback.onDone(cameraSession);
            }

            @Override
            public void onFailure(CameraSession.FailureType failureType, String s) {
                createSessionCallback.onFailure(failureType, s);
            }
        };

        ExtCamera2Session.create(myCallback, events, applicationContext, cameraManager,
                                 surfaceTextureHelper, cameraName, width, height, framerate, disableEIS, zoomOut);
    }
}
