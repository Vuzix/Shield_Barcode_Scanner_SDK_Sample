/*
 Copyright (c) 2018, Vuzix Corporation
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.

 Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.

 Neither the name of Vuzix Corporation nor the names of
 its contributors may be used to endorse or promote products derived
 from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vuzix.barcodesdkdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vuzix.sample.barcode_from_image.R;
import com.vuzix.sdk.barcode.Scanner2;
import com.vuzix.sdk.barcode.ScanResult2;
import com.vuzix.sdk.barcode.Scanner2Factory;

/**
 * Barcode scanner sample code.
 *
 * Most of code is to get camera up and running. Only barcode aspect of code is in the
 * getBarcodeResults() and getAlphaChannel() methods.
 *
 * Position barcode in view frame, take picture with enter key. If results are found
 * a toast with result text will show, if not then nothing will happen. Make sure barcode
 * is focused.
 */

public class MainActivity extends Activity {
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSessions;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private Scanner2 mScanner;


    private Handler mBackgroundHandler;
    private Handler mHandler;

    private boolean mTakingPicture;
    private final static int TAKEPICTURE_COMPLETED = 1001;
    private static final int REQUEST_PERMISSIONS = 200;
    private static final long PREVIEW_TIME_MILLISECS = 1000;

    public void getBarcodeResults(ImageReader reader, int imageWidth, int imageHeight) {
        Image image = reader.acquireLatestImage(); // get the image
        ByteBuffer buffer = image.getPlanes()[0].getBuffer(); // Y component is all we need
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);

        ScanResult2[] results = mScanner.scan(data, imageWidth, imageHeight,null); // pass data into scanner object
        if (results.length > 0) // if results, show toast
            Toast.makeText(MainActivity.this, results[0].getText(), Toast.LENGTH_LONG).show();
    }

    /*
    gets every fourth byte of a pixel
    (what we care about for greyscale image)
     */
    private byte[] getAlphaChannel(byte[] origBytes, int origSize) {
        byte[] newBytes = new byte[origSize / 4];
        int j = 0;
        for (int i = 0; i < origSize; i += 4) {
            newBytes[j] = origBytes[i];
            j += 1;
        }
        return newBytes;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        //Call into the SDK to create a scanner instance.
        try {
            mScanner = Scanner2Factory.getScanner(this);
        }catch (Exception ex){

        }
        mTextureView = (TextureView) findViewById(R.id.texture);
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what){
                    case TAKEPICTURE_COMPLETED:
                        onPictureComplete();
                        break;
                    default:
                        super.handleMessage(msg);
                        break;
                }
            }
        };

        HandlerThread mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        openCamera();
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_ENTER:
                if (!mTakingPicture) {
                    mTakingPicture = true;
                    takeStillPicture();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                finish();
        }
        return true;
    }

    private void onPictureComplete() {
        try {
            Thread.sleep(PREVIEW_TIME_MILLISECS);
        } catch (InterruptedException e) {
            // Ignore
        }
        mTakingPicture = false;
        createCameraPreview();
    }

    protected void takeStillPicture() {
        if (null == mCameraDevice) return;
        precaptureTrigger();
        createCameraStillCapture();
    }

    protected synchronized void createCameraPreview() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            if ((null == texture) || (null == mCameraDevice)) return;
            Surface surface = new Surface(texture);
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (null == mCameraDevice) return;
                    mCameraCaptureSessions = session;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private synchronized void openCamera() {
        CameraManager mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            assert mCameraManager != null;
            String mCameraId = mCameraManager.getCameraIdList()[0];
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSIONS);
                return;
            }
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCameraDevice = camera;
                    createCameraPreview();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    mCameraDevice.close();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    if (null != mCameraDevice) {
                        mCameraDevice.close();
                        mCameraDevice = null;
                    }
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            mCameraDevice = null;
        } catch(SecurityException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        try {
            mCameraCaptureSessions.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void capture(){
        try {
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            mCameraCaptureSessions.capture(mCaptureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCameraStillCapture(){
        try {
            final int imageWidth = 1920, imageHeight = 1080;
            List<Surface> outputSurfaces = new ArrayList<Surface>();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            Surface surface = new Surface(texture);
            outputSurfaces.add(surface);
            ImageReader reader = ImageReader.newInstance(imageWidth, imageHeight, ImageFormat.YUV_420_888, 1);
            outputSurfaces.add(reader.getSurface());

            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureRequestBuilder.addTarget(surface);
            mCaptureRequestBuilder.addTarget(reader.getSurface());

            reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    getBarcodeResults(reader, imageWidth, imageHeight);

                    Message msg = mHandler.obtainMessage();
                    msg.what = TAKEPICTURE_COMPLETED;
                    mHandler.sendMessage(msg);
                }

            }, mBackgroundHandler);

            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSessions = session;
                    capture();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void precaptureTrigger(){
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        Surface surface = new Surface(texture);
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            mCaptureRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                        session.capture(mCaptureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {

                            }
                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                createCameraStillCapture();
                            }
                        }, mBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.exit(0);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "Sorry!, you don't have permission to run this app", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}
