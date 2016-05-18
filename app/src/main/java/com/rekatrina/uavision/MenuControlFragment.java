
/**
 * Created by wangzifeng on 2016/5/17.
 */
package com.rekatrina.uavision;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import dji.sdk.FlightController.DJIFlightController;
import dji.sdk.MissionManager.DJICustomMission;
import dji.sdk.MissionManager.DJIMission;
import dji.sdk.MissionManager.DJIMissionManager;
import dji.sdk.MissionManager.DJIWaypointMission;


// fragment as listener is unimplement
public class MenuControlFragment extends Fragment implements OnClickListener, DJIMissionManager.MissionProgressStatusCallback{
    private View mView = null;

    private DJIMissionManager mMissionManager;
    private DJICustomMission mTakeOffMission;
    private DJIMission mMission;
    private DJIFlightController mFightController;

    private float altitude = 100.0f;
    private float mSpeed = 10.0f;

    private DJIWaypointMission.DJIWaypointMissionFinishedAction mFinishedAct = DJIWaypointMission.DJIWaypointMissionFinishedAction.NoAction;
    private DJIWaypointMission.DJIWaypointMissionHeadingMode mHeadingMode = DJIWaypointMission.DJIWaypointMissionHeadingMode.Auto;

    private Button mTakeOffBtn;
    private Button mGoHomeBtn;
    private Button mRoundBtn;

    public interface MissionClickLinstener{
        void onClick(View view);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(mView == null)
        {
            initView(inflater, container);
        }
        mTakeOffBtn = (Button) mView.findViewById(R.id.btn_takeOff);
        mGoHomeBtn  = (Button) mView.findViewById(R.id.btn_goHome);
        mRoundBtn = (Button) mView.findViewById(R.id.btn_seeAround);
        mTakeOffBtn.setOnClickListener(this);
        mGoHomeBtn.setOnClickListener(this);
        mRoundBtn.setOnClickListener(this);
        return mView;
    }

    private void initView(LayoutInflater inflater, ViewGroup container)
    {
        mView = inflater.inflate(R.layout.left_menu, container, false);
    }

    @Override
    public void missionProgressStatus(DJIMission.DJIMissionProgressStatus progressStatus) {

    }

    @Override
    public void onClick(View v)
    {
        switch (v.getId()) {
            case R.id.btn_takeOff:{
                ((MissionClickLinstener)getActivity()).onClick(v);
                break;
            }
            case R.id.btn_goHome:{
                ((MissionClickLinstener)getActivity()).onClick(v);
                break;
            }
            case R.id.btn_seeAround:{
                ((MissionClickLinstener)getActivity()).onClick(v);
                break;
            }
            default:
                break;
        }

    }
}
