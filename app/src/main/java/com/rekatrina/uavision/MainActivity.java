package com.rekatrina.uavision;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import dji.sdk.Camera.DJICamera;
import dji.sdk.Camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.Camera.DJICameraSettingsDef;
import dji.sdk.Codec.DJICodecManager;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;

public class MainActivity extends Activity {

    private static  final String TAG = MainActivity.class.getName();

    protected CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;
    private DJIBaseProduct mProduct = null;
    private DJICamera mCamera = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextView mConnectStatus;

    protected TextureView mVideoSurface = null;
    private Button mCaptureBtn, mShootPhotoModeBtn, mRecordVideoModeBtn;
    private ToggleButton mRecordBtn;
    private TextView recordingTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initUI();

        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    // Send the raw H264 video data to codec manager for decoding
                    Log.e(TAG,"mCodecManager sendto Decoder");
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                } else {
                    Log.e(TAG, "mCodecManager is null");
                }
            }
        };

        DJICamera camera = UAVisionApplication.getCameraInstance();

        if (camera != null) {
            camera.setDJICameraUpdatedSystemStateCallback(new DJICamera.CameraUpdatedSystemStateCallback() {
                @Override
                public void onResult(DJICamera.CameraSystemState cameraSystemState) {
                    if (null != cameraSystemState) {

                        int recordTime = cameraSystemState.getCurrentVideoRecordingTimeInSeconds();
                        int minutes = (recordTime % 3600) / 60;
                        int seconds = recordTime % 60;

                        final String timeString = String.format("%02d:%02d", minutes, seconds);
                        final boolean isVideoRecording = cameraSystemState.isRecording();

                        MainActivity.this.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                recordingTime.setText(timeString);
                                /*
                                 * Update recordingTime TextView visibility and mRecordBtn's check state
                                 */
                                if (isVideoRecording){
                                    recordingTime.setVisibility(View.VISIBLE);
                                }else
                                {
                                    recordingTime.setVisibility(View.INVISIBLE);
                                }
                            }
                        });
                    }
                }
            });
        }

        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(UAVisionApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);
        Log.e(TAG, "onCreate");
    }
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            onProductChange();
        }
    };
    private void updateTitleBar() {
        if(mConnectStatus == null) return;
        boolean ret = false;
        DJIBaseProduct product = UAVisionApplication.getProductInstance();
        if (product != null) {
            if(product.isConnected()) {
                //The product is connected
                mConnectStatus.setText(UAVisionApplication.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if(product instanceof DJIAircraft) {
                    DJIAircraft aircraft = (DJIAircraft)product;
                    if(aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatus.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        if(!ret) {
            // The product or the remote controller are not connected.
            mConnectStatus.setText("Disconnected");
        }
    }

    protected void onProductChange() {
        initPreviewer();
    }

    private void initUI() {
        mConnectStatus = (TextView) findViewById(R.id.textView_connectStatus);
        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.surface_video);

        recordingTime = (TextView) findViewById(R.id.timer);
        mCaptureBtn = (Button) findViewById(R.id.btn_capture);
        mRecordBtn = (ToggleButton) findViewById(R.id.btn_record);
        mShootPhotoModeBtn = (Button) findViewById(R.id.btn_shoot_photo_mode);
        mRecordVideoModeBtn = (Button) findViewById(R.id.btn_record_video_mode);

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(mSurfaceTextureListner);
        }

        mCaptureBtn.setOnClickListener(mBtnClickListener);
        mRecordBtn.setOnClickListener(mBtnClickListener);
        mShootPhotoModeBtn.setOnClickListener(mBtnClickListener);
        mRecordVideoModeBtn.setOnClickListener(mBtnClickListener);

        recordingTime.setVisibility(View.INVISIBLE);

        mRecordBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked)
                    startRecord();
                else
                    stopRecord();
            }
        });

    }


    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        updateTitleBar();
        if(mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause1");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onExit(View v) {
        Log.e(TAG, "Exit");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
    private void initPreviewer() {
        Log.e(TAG,"init Previewer");
        try {
            mProduct = UAVisionApplication.getProductInstance();
        }
        catch(Exception exception){
            mProduct = null;
        }
        if (mProduct == null || !mProduct.isConnected()) {
            mCamera = null;
            showToast("No Connected");
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(mSurfaceTextureListner);
            }
            if (!mProduct.getModel().equals(DJIBaseProduct.Model.UnknownAircraft)) {
                DJICamera camera = mProduct.getCamera();
                if (camera != null){
                    // Set the callback
                    camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    private void uninitPreviewer() {
        DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null){
            // Reset the callback
            UAVisionApplication.getCameraInstance().setDJICameraReceivedVideoDataCallback(null);
        }
    }

    private TextureView.SurfaceTextureListener mSurfaceTextureListner = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureAvailable");
            if (mCodecManager == null) {
                mCodecManager = new DJICodecManager(MainActivity.this, surface, width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.e(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.e(TAG,"onSurfaceTextureDestroyed");
            if (mCodecManager != null) {
                mCodecManager.cleanSurface();
                mCodecManager = null;
            }
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Log.e(TAG,"onSurfaceTextureUpdated");
        }
    };

    private View.OnClickListener mBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btn_capture:{
                    captureAction();
                    break;
                }
                case R.id.btn_shoot_photo_mode:{
                    switchCameraMode(DJICameraSettingsDef.CameraMode.ShootPhoto);
                    break;
                }
                case R.id.btn_record_video_mode:{
                    switchCameraMode(DJICameraSettingsDef.CameraMode.RecordVideo);
                    break;
                }
                default:
                    break;
            }
        }
    };

    private void switchCameraMode(DJICameraSettingsDef.CameraMode cameraMode){

        DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null) {
            camera.setCameraMode(cameraMode, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {

                    if (error == null) {
                        showToast("Switch Camera Mode Succeeded");
                    } else {
                        showToast(error.getDescription());
                    }
                }
            });
        }
    }

    // Method for taking photo
    private void captureAction(){

        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.ShootPhoto;

        final DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null) {

            DJICameraSettingsDef.CameraShootPhotoMode photoMode = DJICameraSettingsDef.CameraShootPhotoMode.Single; // Set the camera capture mode as Single mode
            camera.startShootPhoto(photoMode, new DJIBaseComponent.DJICompletionCallback() {

                @Override
                public void onResult(DJIError error) {
                    if (error == null) {
                        showToast("take photo: success");
                    } else {
                        showToast(error.getDescription());
                    }
                }

            }); // Execute the startShootPhoto API
        }
    }

    // Method for starting recording
    private void startRecord(){

        DJICameraSettingsDef.CameraMode cameraMode = DJICameraSettingsDef.CameraMode.RecordVideo;
        final DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null) {
            camera.startRecordVideo(new DJIBaseComponent.DJICompletionCallback(){
                @Override
                public void onResult(DJIError error)
                {
                    if (error == null) {
                        showToast("Record video: success");
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the startRecordVideo API
        }
    }

    // Method for stopping recording
    private void stopRecord(){

        DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null) {
            camera.stopRecordVideo(new DJIBaseComponent.DJICompletionCallback(){

                @Override
                public void onResult(DJIError error)
                {
                    if(error == null) {
                        showToast("Stop recording: success");
                    }else {
                        showToast(error.getDescription());
                    }
                }
            }); // Execute the stopRecordVideo API
        }
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }


}
