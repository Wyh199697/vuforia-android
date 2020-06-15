/*===============================================================================
Copyright (c) 2020 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.engine.CoreSamples.app.ObjectRecognition;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteAbortException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.kircherelectronics.fsensor.BaseFilter;
import com.kircherelectronics.fsensor.filter.averaging.MeanFilter;
import com.kircherelectronics.fsensor.observer.SensorSubject;
import com.kircherelectronics.fsensor.sensor.FSensor;
import com.kircherelectronics.fsensor.sensor.acceleration.AccelerationSensor;
import com.kircherelectronics.fsensor.sensor.gyroscope.ComplementaryGyroscopeSensor;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableList;
import com.vuforia.TrackableResult;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;
import com.vuforia.engine.CoreSamples.ui.SampleAppMessage;
import com.vuforia.engine.SampleApplication.SampleActivityBase;
import com.vuforia.engine.SampleApplication.SampleApplicationControl;
import com.vuforia.engine.SampleApplication.SampleApplicationException;
import com.vuforia.engine.SampleApplication.SampleApplicationSession;
import com.vuforia.engine.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.engine.SampleApplication.utils.SampleAppTimer;
import com.vuforia.engine.SampleApplication.utils.SampleApplicationGLView;
import com.vuforia.engine.SampleApplication.utils.Texture;
import com.vuforia.engine.CoreSamples.R;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenu;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuGroup;
import com.vuforia.engine.CoreSamples.ui.SampleAppMenu.SampleAppMenuInterface;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Vector;

/**
 * The main activity for the ObjectTargets sample.
 * Object Targets allows users to create 3D targets for detection and tracking
 * To create your own Object Target, download the Vuforia Object Scanner tool from the
 * Vuforia developer website
 *
 * This class does high-level handling of the Vuforia lifecycle and any UI updates
 *
 * For ObjectTarget-specific rendering, check out ObjectTargetRenderer.java
 * For the low-level Vuforia lifecycle code, check out SampleApplicationSession.java
 */
class UIHandler<T> extends Handler{

    protected WeakReference<T> ref;

    public UIHandler(T cla){
        ref = new WeakReference<T>(cla);
    }

    public T getRef(){
        return ref != null ? ref.get() : null;
    }
}

public class ObjectTargets extends SampleActivityBase implements SampleApplicationControl,
    SampleAppMenuInterface, OnClickListener, OnEditorActionListener
{
    private static final String LOGTAG = "ObjectRecognition";
    
    private SampleApplicationSession vuforiaAppSession;
    
    private DataSet mCurrentDataset;

    private SampleApplicationGLView mGlView;

    private ObjectTargetRenderer mRenderer;
    
    // The textures we will use for rendering:
    private Vector<Texture> mTextures;
    
    private GestureDetector mGestureDetector;
    
    private boolean mFlash = false;
    private boolean mDeviceTracker = false;
    
    private View mFlashOptionView;
    
    private RelativeLayout mUILayout;
    
    private SampleAppMenu mSampleAppMenu;
    ArrayList<View> mSettingsAdditionalViews = new ArrayList<>();

    private SampleAppMessage mSampleAppMessage;
    private SampleAppTimer mRelocalizationTimer;
    private SampleAppTimer mStatusDelayTimer;

    private int mCurrentStatusInfo;
    
    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;
    
    private boolean mIsDroidDevice = false;

    //DataBase
    private DBHelper dbHelper;
    private SQLiteDatabase db;

    //Sensor
    private FSensor rotationSensor;
    private FSensor accSensor;
    private BaseFilter baseFilter;

    //Button
    private Button start_button;
    private Button add_button;
    private Button complete_button;
    private EditText shape_name;

    //Cord
    private float[] camera_cord;
    private float[] world_cord = new float[3];
    private float[] rotation;
    private float[] acceleration;
    float[] t_a = new float[3];
    private boolean f_y = true;
    private double y_begin = 0;
    private boolean isStart = false;
    private cordShape cordshape = new cordShape();
    private float[] offset = new float[3];
    private ArrayList<float[]> Acc = new ArrayList<>();
    private ArrayList<Float> delTime = new ArrayList<>();
    private long start = 0;
    private long end = 0;

    private SensorSubject.SensorObserver rotationObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            // Do interesting things here
            rotation = values;
            if(f_y && rotation[0] != 0){
                y_begin = Math.toDegrees(rotation[0]);
                f_y = false;
            }else {
                rotation[0] = (float) (Math.toDegrees(rotation[0]) - y_begin);
            }
            //rotation[0] = (float) Math.toDegrees(rotation[0]);
            rotation[1] = (float) Math.toDegrees(rotation[1]);
            rotation[2] = (float) Math.toDegrees(rotation[2]);
        }
    };

    private SensorSubject.SensorObserver accObserver = new SensorSubject.SensorObserver() {
        @Override
        public void onSensorChanged(float[] values) {
            end = System.currentTimeMillis();
            acceleration = values;
            double G = 9.70;
            if (rotation == null){
                return;
            }
            double a = rotation[1]*Math.PI/180; //pitch
            double b = rotation[2]*Math.PI/180; //roll
            double c = rotation[0]*Math.PI/180; //yaw
            double[] t_g = new double[3];
            double[] ng_a = new double[3];
            t_g[0] = Math.cos(a)*Math.sin(b)*G;
            t_g[1] = -Math.sin(a)*G;
            t_g[2] = Math.cos(a)*Math.cos(b)*G;
            acceleration[0] = (float) (acceleration[0]-t_g[0]);
            acceleration[1] = (float) (acceleration[1]-t_g[1]);
            acceleration[2] = (float) (acceleration[2]-t_g[2]);
            t_a[0] = (float) ((Math.cos(b)*Math.cos(c) + Math.sin(a)*Math.sin(b)*Math.sin(c))*acceleration[0] + Math.cos(a)*Math.sin(c)*acceleration[1] + (Math.cos(b)*Math.sin(a)*Math.sin(c) - Math.cos(c)*Math.sin(b))*acceleration[2]);
            t_a[1] = (float) ((Math.cos(c)*Math.sin(a)*Math.sin(b) - Math.cos(b)*Math.sin(c))*acceleration[0] + Math.cos(a)*Math.cos(c)*acceleration[1] + (Math.sin(b)*Math.sin(c) + Math.cos(b)*Math.cos(c)*Math.sin(a))*acceleration[2]);
            t_a[2] = (float) (Math.cos(a)*Math.sin(b)*acceleration[0] + (-Math.sin(a))*acceleration[1] + Math.cos(a)*Math.cos(b)*acceleration[2]);
            t_a = ((MeanFilter) baseFilter).filter(t_a);
            if(isStart){
                float[] temp = Arrays.copyOf(t_a,3);
                Acc.add(temp);
                delTime.add(((float)(end - start))/1000.0f);
            }
            start = end;
        }
    };

    private float[] calculateOffset(){
        float[] vel = new float[3];
        float[] pos = new float[3];
        int len = Acc.size();
        for(int i = 1; i < len; i++){
            vel[0] = vel[0] + Acc.get(i)[0] * delTime.get(i - 1);
            vel[1] = vel[1] + Acc.get(i)[1] * delTime.get(i - 1);
            vel[2] = vel[2] + Acc.get(i)[2] * delTime.get(i - 1);
            pos[0] = pos[0] + vel[0]*delTime.get(i - 1);
            pos[1] = pos[1] + vel[1]*delTime.get(i - 1);
            pos[2] = pos[2] + vel[2]*delTime.get(i - 1);
        }
        return pos;
    }

    private final MainHandler mHandler = new MainHandler(this);

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_start: {
                if (isStart) {
                    start_button.setText("start");
                    cordshape = new cordShape();
                    isStart = false;
                } else {
                    start_button.setText("stop");
                    isStart = true;
                }
                break;
            }
            case R.id.button_add: {
                offset = calculateOffset();
                cordshape.addCord(world_cord, offset);
                break;
            }
            case R.id.button_complete:{
                shape_name.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            // 当按了搜索之后关闭软键盘
            ((InputMethodManager) shape_name.getContext().getSystemService(
                    Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
                    ObjectTargets.this.getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
            shape_name.setVisibility(View.INVISIBLE);
            ContentValues values = new ContentValues();
            values.put("name", shape_name.getText().toString());
            SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss");
            Date date = new Date();
            values.put("time", sdf.format(date));
            ArrayList cord = cordshape.getShape();
            String cords = "";
            int len = cord.size();
            for(int i = 0; i < len; i++){
                cords = cords + Arrays.toString((float[]) cord.get(i)) + ",";
            }
            values.put("shape", cords);
            db.insert("shapes", null, values);
            return true;
        }
        return false;
    }

    private class MainHandler extends UIHandler{
        private MainHandler(ObjectTargets activity){
            super(activity);
        }

        @Override
        public void handleMessage(Message msg){
            super.handleMessage(msg);
            ObjectTargets activity = (ObjectTargets)ref.get();
            if(activity != null){
                if (activity.isFinishing()) {
                    return;
                }
                switch (msg.what){
                    case 1:
                        if(mRenderer.camera_cord == null){
                            break;
                        }
                        float c_x = camera_cord[3];
                        float c_y = camera_cord[7];
                        float c_z = camera_cord[11];
                        TextView camera_cord_text = findViewById(R.id.camera_cord);
                        String c_cord = "x: " + (-c_x*100) + "\ny: " + c_y*100 + "\nz: " + (-c_z*100);
                        String eu_cord = "\nyaw: " + rotation[0] + "\npitch: " + rotation[1] + "\nroll: " + rotation[2];
                        float c = (float) (rotation[0]*Math.PI/180);
                        float b = (float) (rotation[1]*Math.PI/180);
                        float a = (float) (rotation[2]*Math.PI/180);
                        world_cord[0] = (float) (Math.cos(b)*Math.cos(c)*c_x + (Math.cos(c)*Math.sin(a)*Math.sin(b) - Math.cos(a)*Math.sin(c))*c_y - (Math.sin(a)*Math.sin(c) + Math.cos(a)*Math.cos(c)*Math.sin(b))*c_z);
                        world_cord[1] = (float) (Math.cos(b)*Math.sin(c)*c_x + (Math.cos(a)*Math.cos(c) + Math.sin(a)*Math.sin(b)*Math.sin(c))*c_y + (Math.cos(c)*Math.sin(a) - Math.cos(a)*Math.sin(b)*Math.sin(c))*c_z);
                        world_cord[2] = (float) (Math.sin(b)*c_x - Math.cos(b)*Math.sin(a)*c_y + Math.cos(a)*Math.cos(b)*c_z);

                        String w_cord = "\nw_x: " + world_cord[0]*100 + "\nw_y: " + world_cord[1]*100 + "\nw_z: " + world_cord[2]*100;
                        String str_a = "\na_x: " + t_a[0]*100 + "\na_y: " + t_a[1]*100 + "\na_z: " + t_a[2]*100;
                        String str_off = "\noff_x: " + offset[0]*100 + "\noff_y: " + offset[1]*100 + "\noff_z: " + offset[2]*100;
                        camera_cord_text.setText(c_cord + eu_cord + w_cord + str_a + str_off);

                        camera_cord_text.setTextColor(Color.rgb(255, 0, 0));
                }
            }
        }
    }

    //private TextView camera_cord = findViewById(R.id.camera_cord);
    

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new SampleApplicationSession(this);
        
        startLoadingAnimation();
        
        vuforiaAppSession
            .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        // Load any sample specific textures:
        mTextures = new Vector<>();
        loadTextures();
        
        mGestureDetector = new GestureDetector(this, new GestureListener());
        
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
            "droid");

        // Relocalization timer and message
        mSampleAppMessage = new SampleAppMessage(this, mUILayout, mUILayout.findViewById(R.id.topbar_layout), false);
        mRelocalizationTimer = new SampleAppTimer(10000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (vuforiaAppSession != null)
                {
                    vuforiaAppSession.resetDeviceTracker();
                }

                super.onFinish();
            }
        };

        mStatusDelayTimer = new SampleAppTimer(1000, 1000)
        {
            @Override
            public void onFinish()
            {
                if (mRenderer.isTargetCurrentlyTracked())
                {
                    super.onFinish();
                    return;
                }

                if (!mRelocalizationTimer.isRunning())
                {
                    mRelocalizationTimer.startTimer();
                }

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mSampleAppMessage.show(getString(R.string.instruct_relocalize));
                    }
                });

                super.onFinish();
            }
        };
        dbHelper = new DBHelper(ObjectTargets.this, "shapes.db", null, 1);
        db = dbHelper.getWritableDatabase();
        start_button = findViewById(R.id.button_start);
        start_button.setOnClickListener(this);
        add_button = findViewById(R.id.button_add);
        add_button.setOnClickListener(this);
        complete_button = findViewById(R.id.button_complete);
        complete_button.setOnClickListener(this);
        shape_name = findViewById(R.id.shape_name);
        shape_name.setOnEditorActionListener(this);
        rotationSensor = new ComplementaryGyroscopeSensor(this);
        rotationSensor.register(rotationObserver);
        rotationSensor.start();
        accSensor = new AccelerationSensor(this);
        accSensor.register(accObserver);
        accSensor.start();
        baseFilter = new MeanFilter();
        ((MeanFilter) baseFilter).setTimeConstant((float) 0.5);
        super.onResume();
    }


    // Load specific textures from the APK, which we will later use for rendering.
    private void loadTextures()
    {
        mTextures.add(Texture.loadTextureFromApk(
            "ObjectRecognition/CubeWireframe.png", getAssets()));
    }
    

    private class GestureListener extends
        GestureDetector.SimpleOnGestureListener
    {
        // Used to set autofocus one second after a manual focus is triggered
        private final Handler autofocusHandler = new Handler();
        
        
        @Override
        public boolean onDown(MotionEvent e)
        {
            return true;
        }


        // Process Single Tap event to trigger autofocus
        @Override
        public boolean onSingleTapUp(MotionEvent e)
        {
            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);
            if (!result)
                Log.e("SingleTapUp", "Unable to trigger focus");

            // Generates a Handler to trigger continuous auto-focus
            // after 1 second
            autofocusHandler.postDelayed(new Runnable()
            {
                public void run()
                {
                    final boolean autofocusResult = CameraDevice.getInstance().setFocusMode(
                            CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

                    if (!autofocusResult)
                        Log.e("SingleTapUp", "Unable to re-enable continuous auto-focus");
                }
            }, 1000L);
            
            return true;
        }
    }
    

    @Override
    protected void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        vuforiaAppSession.onResume();
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
    }
    

    @Override
    protected void onPause()
    {
        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        // Turn off the flash
        if (mFlashOptionView != null && mFlash)
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            {
                ((Switch) mFlashOptionView).setChecked(false);
            } else
            {
                ((CheckBox) mFlashOptionView).setChecked(false);
            }
        }
        
        vuforiaAppSession.onPause();
    }
    

    @Override
    protected void onDestroy()
    {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
        // Unload texture:
        mTextures.clear();
        mTextures = null;
        
        System.gc();
    }
    

    private void initApplicationAR()
    {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);



        mRenderer = new ObjectTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
        mGlView.setPreserveEGLContextOnPause(true);

        setRendererReference(mRenderer);
    }
    
    
    private void startLoadingAnimation()
    {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay, null);
        
        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        RelativeLayout topbarLayout = mUILayout.findViewById(R.id.topbar_layout);
        topbarLayout.setVisibility(View.VISIBLE);

        TextView title = mUILayout.findViewById(R.id.topbar_title);
        title.setText(getText(R.string.feature_object_reco));

        mSettingsAdditionalViews.add(topbarLayout);

        loadingDialogHandler.mLoadingDialogContainer = mUILayout
            .findViewById(R.id.loading_indicator);
        
        // Shows the loading indicator at start
        loadingDialogHandler
            .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));
    }
    

    @Override
    public boolean doLoadTrackersData()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();
        
        if (mCurrentDataset == null)
            return false;
        
        if (!mCurrentDataset.load("ObjectRecognition/object_OT.xml",
            STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;
        
        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;
        
        TrackableList trackableList = mCurrentDataset.getTrackables();
        for (Trackable trackable : trackableList)
        {
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "
                + trackable.getUserData());
        }
        
        return true;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
            .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;
        
        if (mCurrentDataset != null && mCurrentDataset.isActive())
        {
            if (objectTracker.getActiveDataSets().at(0).equals(mCurrentDataset)
                && !objectTracker.deactivateDataSet(mCurrentDataset))
            {
                result = false;
            }
            else if (!objectTracker.destroyDataSet(mCurrentDataset))
            {
                result = false;
            }
            
            mCurrentDataset = null;
        }
        
        return result;
    }


    @Override
    public void onVuforiaResumed()
    {
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }


    // Called once Vuforia has been initialized or
    // an error has caused Vuforia initialization to stop
    @Override
    public void onInitARDone(SampleApplicationException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            mSampleAppMenu = new SampleAppMenu(this, this, "Object Reco",
                mGlView, mUILayout, mSettingsAdditionalViews);
            setSampleAppMenuSettings();

            vuforiaAppSession.startAR();

        }
        else
        {
            Log.e(LOGTAG, exception.getString());
            if(exception.getCode() == SampleApplicationException.LOADING_TRACKERS_FAILURE)
            {
                showInitializationErrorMessage( 
                    getString(R.string.INIT_OBJECT_DATASET_NOT_FOUND_TITLE),
                    getString(R.string.INIT_OBJECT_DATASET_NOT_FOUND));
            }
            else
            {
                showInitializationErrorMessage( getString(R.string.INIT_ERROR),
                    exception.getString() );
            }
        }
    }


    @Override
    public void onVuforiaStarted()
    {
        mRenderer.updateRenderingPrimitives();

        // Set camera focus mode
        if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO))
        {
            // If continuous autofocus mode fails, attempt to set to a different mode
            if(!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO))
            {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
            }
        }

        showProgressIndicator(false);
    }


    private void showProgressIndicator(boolean show)
    {
        if (show)
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        }
        else
        {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }


    private void showInitializationErrorMessage(String title, String message)
    {
        final String errorMessage = message;
        final String messageTitle = title;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (mErrorDialog != null)
                {
                    mErrorDialog.dismiss();
                }
                
                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                    ObjectTargets.this);
                builder
                    .setMessage(errorMessage)
                    .setTitle(messageTitle)
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int id)
                            {
                                finish();
                            }
                        });
                
                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }
    

    // Called every frame
    @Override
    public void onVuforiaUpdate(State state)
    {
        camera_cord = mRenderer.camera_cord;
        Message msg = new Message();
        msg.what = 1;
        mHandler.sendMessage(msg);
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        Tracker tracker;
        
        // Initialize the Object Tracker
        tracker = tManager.initTracker(ObjectTracker.getClassType());

        if (tracker == null)
        {
            Log.e(
                LOGTAG,
                "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        }
        else
        {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null)
        {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
        }

        return result;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start())
        {
            Log.i(LOGTAG, "Successfully started Object Tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to start Object Tracker");
            result = false;
        }

        if (isDeviceTrackingActive())
        {
            PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager
                    .getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null && deviceTracker.start())
            {
                Log.i(LOGTAG, "Successfully started Device Tracker");
            }
            else
            {
                Log.e(LOGTAG, "Failed to start Device Tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker != null)
        {
            objectTracker.stop();
            Log.i(LOGTAG, "Successfully stopped object tracker");
        }
        else
        {
            Log.e(LOGTAG, "Failed to stop object tracker");
            result = false;
        }

        // Stop device tracker
        if(isDeviceTrackingActive())
        {

            Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null)
            {
                deviceTracker.stop();
                Log.i(LOGTAG, "Successfully stopped device tracker");
            }
            else
            {
                Log.e(LOGTAG, "Could not stop device tracker");
            }
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        TrackerManager tManager = TrackerManager.getInstance();
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());
        
        return result;
    }
    
    
    @Override
    public boolean onTouchEvent(MotionEvent event)
    {
        // Process the Gestures
        return (mSampleAppMenu != null && mSampleAppMenu.processEvent(event)
                || mGestureDetector.onTouchEvent(event));
    }
    
    
    private boolean isDeviceTrackingActive()
    {
        return mDeviceTracker;
    }


    // Menu options
    private final static int CMD_BACK = -1;
    private final static int CMD_DEVICE_TRACKER = 1;
    private final static int CMD_FLASH = 2;

    private void setSampleAppMenuSettings()
    {
        SampleAppMenuGroup group;
        
        group = mSampleAppMenu.addGroup("", false);
        group.addTextItem(getString(R.string.menu_back), -1);

        group = mSampleAppMenu.addGroup("", true);
        group.addSelectionItem(getString(R.string.menu_device_tracker),
                CMD_DEVICE_TRACKER, false);

        group = mSampleAppMenu.addGroup(getString(R.string.menu_camera), true);
        mFlashOptionView = group.addSelectionItem(
            getString(R.string.menu_flash), CMD_FLASH, false);
        
        mSampleAppMenu.attachMenu();
    }


    // In this function you can define the desired behavior for each menu option
    // Each case corresponds to a menu option
    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
                finish();
                break;
            
            case CMD_FLASH:
                result = CameraDevice.getInstance().setFlashTorchMode(!mFlash);
                
                if (result)
                {
                    mFlash = !mFlash;
                } else
                {
                    showToast(getString(mFlash ? R.string.menu_flash_error_off
                        : R.string.menu_flash_error_on));
                    Log.e(LOGTAG,
                        getString(mFlash ? R.string.menu_flash_error_off
                            : R.string.menu_flash_error_on));
                }
                break;
            
            case CMD_DEVICE_TRACKER:
                TrackerManager trackerManager = TrackerManager.getInstance();
                PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                        trackerManager.getTracker(PositionalDeviceTracker.getClassType());

                if (deviceTracker != null)
                {
                    if (!mDeviceTracker)
                    {
                        if (!deviceTracker.start())
                        {
                            Log.e(LOGTAG,"Failed to start device tracker");
                            result = false;
                        }
                        else
                        {
                            Log.d(LOGTAG,"Successfully started device tracker");
                        }
                    }
                    else
                    {
                        deviceTracker.stop();
                        clearSampleAppMessage();
                    }
                }
                else
                {
                    Log.e(LOGTAG, "Device tracker is null!");
                    result = false;
                }

                if (result)
                {
                    mDeviceTracker = !mDeviceTracker;
                }
                else
                {
                    clearSampleAppMessage();
                }

                break;
        }
        
        return result;
    }


    public void checkForRelocalization(final int statusInfo)
    {
        if (mCurrentStatusInfo == statusInfo)
        {
            return;
        }

        mCurrentStatusInfo = statusInfo;

        if (mCurrentStatusInfo == TrackableResult.STATUS_INFO.RELOCALIZING)
        {
            // If the status is RELOCALIZING, start the timer
            if (!mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.startTimer();
            }
        }
        else
        {
            // If the status is not RELOCALIZING, stop the timers and hide the message
            if (mStatusDelayTimer.isRunning())
            {
                mStatusDelayTimer.stopTimer();
            }

            if (mRelocalizationTimer.isRunning())
            {
                mRelocalizationTimer.stopTimer();
            }

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    mSampleAppMessage.hide();
                }
            });
        }
    }


    private void clearSampleAppMessage()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if (mSampleAppMessage != null)
                {
                    mSampleAppMessage.hide();
                }
            }
        });
    }

    
    private void showToast(String text)
    {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
