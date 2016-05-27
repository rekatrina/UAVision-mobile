package com.rekatrina.uavision;

import android.Manifest;
import android.app.Activity;

import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;

import java.util.LinkedList;
import java.util.List;

import dji.sdk.Battery.DJIBattery;
import dji.sdk.Battery.DJIBattery.DJIBatteryStateUpdateCallback;
import dji.sdk.Camera.DJICamera;
import dji.sdk.Camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.Camera.DJICameraSettingsDef;
import dji.sdk.Codec.DJICodecManager;
import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.FlightController.DJIFlightControllerDataType;
import dji.sdk.FlightController.DJIFlightControllerDelegate;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypoint;
import dji.sdk.MissionManager.DJIWaypointMission;
import dji.sdk.Products.DJIAircraft;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIError;
import dji.sdk.util.Util;

public class MainActivity extends SlidingFragmentActivity implements MenuControlFragment.MissionClickLinstener {

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

    private DJIWaypointMission mWaypointMission;
    private DJIMissionManager mMissionManager;
    private DJIFlightController mFlightController;

    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAction = DJIWaypointMission.DJIWaypointMissionFinishedAction.NoAction;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;


    private double droneLocationLat = 181, droneLocationLng = 181;



    protected DJIBatteryStateUpdateCallback mBatteryStateUpdateCallback = new DJIBatteryStateUpdateCallback() {
        @Override
        public void onResult(DJIBattery.DJIBatteryState djiBatteryState) {
            TextView textView_uavBattery = (TextView)findViewById(R.id.textView_battery_uav);
            textView_uavBattery.setText(djiBatteryState.getBatteryEnergyRemainingPercent()+"%");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);
        initUI();
        initLeftMenu();
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

        // Register the broadcast receiver for receiving batrery.
        IntentFilter batteryFilter = new IntentFilter();
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(mBatteryReceiver, batteryFilter);
        Log.e(TAG, "onCreate");
    }

    protected BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))
            {
                int level = intent.getIntExtra("level",0); // the second arg is default value
                int scale = intent.getIntExtra("scale",100);
                TextView batteryText = (TextView)findViewById(R.id.textView_battery);
                batteryText.setText((level*100)/scale+"%");
//                if((level*100)/scale<40)
//                    batteryText.setTextColor(0x00FF00);
//                else
//                    batteryText.setTextColor(0xFF0000);
            }
        }
    };

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

    private void initLeftMenu(){
        Fragment leftMenuFragment = new MenuControlFragment();
        setBehindContentView(R.layout.left_menu_frame);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.left_menu_frame, leftMenuFragment).commit();
        SlidingMenu menu = getSlidingMenu();
        menu.setMode(SlidingMenu.LEFT);
        // 设置触摸屏幕的模式
        menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_FULLSCREEN);
        menu.setShadowWidthRes(R.dimen.shadow_width);
        menu.setShadowDrawable(R.drawable.shadow);
        // 设置滑动菜单视图的宽度
        menu.setBehindWidthRes(R.dimen.slidingmenu_offset);
        // 设置渐入渐出效果的值
        menu.setFadeDegree(0.35f);
        menu.setBehindScrollScale(1.0f);
        // menu.setSecondaryShadowDrawable(R.drawable.shadow);
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        initFlightController();
        initMissionManager();
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
        unregisterReceiver(mBatteryReceiver);
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
                if (camera != null) {
                    // Set the callback
                    camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }

                DJIBattery battery = mProduct.getBattery();
                if(battery != null){
                    battery.setBatteryStateUpdateCallback(mBatteryStateUpdateCallback);
                }
            }
        }
    }

    private void uninitPreviewer() {
        DJICamera camera = UAVisionApplication.getCameraInstance();
        if (camera != null){
            // Reset the callback
            camera.setDJICameraReceivedVideoDataCallback(null);
        }
        DJIBattery battery = UAVisionApplication.getBatteryInstance();
        if(battery != null){
            battery.setBatteryStateUpdateCallback(null);
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

    @Override
    public void onClick(View v){
        switch (v.getId()) {
            case R.id.btn_takeOff:{
                startWaypointMission();
                break;
            }
            case R.id.btn_goHome:{
                stopWaypointMission();
                break;
            }
            case R.id.btn_send:{
                if(mFlightController!=null) {
                    // send data from mobile to onboard
                    String msg = "hello world";
                    byte[] data = msg.getBytes();
                    mFlightController.sendDataToOnboardSDKDevice(data,
                            new DJIBaseComponent.DJICompletionCallback() {
                                @Override
                                public void onResult(DJIError pError) {
                                    if(pError!=null)
                                        Toast.makeText(MainActivity.this, pError.getDescription(), Toast.LENGTH_SHORT).show();
                                        else
                                        Toast.makeText(MainActivity.this, "send", Toast.LENGTH_SHORT).show();
                                }
                            });
                }
                else
                    Toast.makeText(MainActivity.this, "Null flightController", Toast.LENGTH_SHORT).show();
                break;
            }
            default:
                break;
        }
    }
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


    private void initMissionManager() {
        DJIBaseProduct product = UAVisionApplication.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast("Disconnected");
            mMissionManager = null;
            return;
        } else {

            mMissionManager = product.getMissionManager();
            mMissionManager.setMissionProgressStatusCallback(new DJIMissionManager.MissionProgressStatusCallback() {
                @Override
                public void missionProgressStatus(DJIMission.DJIMissionProgressStatus djiMissionProgressStatus) {

                }
            });
            mMissionManager.setMissionExecutionFinishedCallback(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    showToast("Execution finished"+ (error == null ? "Success!" : error.getDescription()));
                }
            });
        }

        mWaypointMission = new DJIWaypointMission();
        mWaypointMission.finishedAction = mFinishedAction;
        mWaypointMission.headingMode = mHeadingMode;
        mWaypointMission.autoFlightSpeed = 10.0f;
        initTestMission();
    }

    private void initFlightController() {

        DJIBaseProduct product = UAVisionApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof DJIAircraft) {
                mFlightController = ((DJIAircraft) product).getFlightController();
            }
        }

        if (mFlightController != null) {
            mFlightController.setUpdateSystemStateCallback(new DJIFlightControllerDelegate.FlightControllerUpdateSystemStateCallback() {

                @Override
                public void onResult(DJIFlightControllerDataType.DJIFlightControllerCurrentState state) {
                    droneLocationLat = state.getAircraftLocation().getLatitude();
                    droneLocationLng = state.getAircraftLocation().getLongitude();
                }
            });
        }
    }
    private void prepareWayPointMission(){

        if (mMissionManager != null && mWaypointMission != null) {

            DJIMission.DJIMissionProgressHandler progressHandler = new DJIMission.DJIMissionProgressHandler() {
                @Override
                public void onProgress(DJIMission.DJIProgressType type, float progress) {
                }
            };

            mMissionManager.prepareMission(mWaypointMission, progressHandler, new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    showToast(error == null ? "Mission Prepare Successfully" : error.getDescription());
                }
            });
        }

    }

    private void startWaypointMission(){
        prepareWayPointMission();
        if (mMissionManager != null) {

            mMissionManager.startMissionExecution(new DJIBaseComponent.DJICompletionCallback() {
                @Override
                public void onResult(DJIError error) {
                    showToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
                }
            });

        }
    }

    private void stopWaypointMission(){

        if (mMissionManager != null) {
            mMissionManager.stopMissionExecution(new DJIBaseComponent.DJICompletionCallback() {

                @Override
                public void onResult(DJIError error) {
                    showToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
                }
            });

            if (mWaypointMission != null){
                mWaypointMission.removeAllWaypoints();
            }
        }
    }

    private void initTestMission(){
        List<DJIWaypoint> testingWaypoints = new LinkedList<>();


        DJIWaypoint northPoint = new DJIWaypoint(droneLocationLat + 10 * Utils.ONE_METER_OFFSET, droneLocationLng, 20f);
        DJIWaypoint eastPoint = new DJIWaypoint(droneLocationLat, droneLocationLng + 10 * Utils.calcLongitudeOffset(droneLocationLng), 20f);
        DJIWaypoint southPoint = new DJIWaypoint(droneLocationLat - 10 * Utils.ONE_METER_OFFSET, droneLocationLng, 20f);
        DJIWaypoint westPoint = new DJIWaypoint(droneLocationLat, droneLocationLng - 10 * Utils.calcLongitudeOffset(droneLocationLng), 20f);
        DJIWaypoint pointHome = new DJIWaypoint(droneLocationLat, droneLocationLng, 10f);

        northPoint.addAction(new DJIWaypoint.DJIWaypointAction(DJIWaypoint.DJIWaypointActionType.GimbalPitch, -60));
        southPoint.addAction(new DJIWaypoint.DJIWaypointAction(DJIWaypoint.DJIWaypointActionType.RotateAircraft, 60));

        testingWaypoints.add(pointHome);
        testingWaypoints.add(northPoint);
        testingWaypoints.add(eastPoint);
        testingWaypoints.add(southPoint);
        testingWaypoints.add(westPoint);
        testingWaypoints.add(pointHome);

        mWaypointMission.addWaypoints(testingWaypoints);
    }
}
