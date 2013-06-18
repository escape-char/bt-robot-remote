package edu.esu.spacesys.btrobotremote;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.lang.Math;

import android.app.Activity;
import android.app.ActionBar;
import android.util.Log;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

//for main option menu
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import android.view.View;
import android.view.View.OnClickListener;

public class BTRobotRemoteActivity extends Activity
{
    private static final String TAG = "BT-Robot-Remote";
    
    //monitors device for motions
    private MotionMonitor motionMonitor;
    
    //type of data one can receive from
    //motion monitor
    private final int ACCEL_DATA = 0;
    private final int ROTATE_DATA = 1;
    
    private TextView statsArea;
    private MotionHandler mHandler;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final ActionBar actionBar = getActionBar();
        

        if(actionBar == null){Log.e(TAG, "There is no ACTIONBAR");}
        actionBar.setDisplayShowTitleEnabled(false);
    }
    @Override
    public void onStart(){super.onStart();}

    //called when application is first created and resuming from pause
    //put initilization stuff here
    @Override
    public void onResume(){
         super.onResume();
        //begin Monitoring
        mHandler = new MotionHandler(this);
        motionMonitor = new MotionMonitor(this, mHandler);
        motionMonitor.start();
    }
    //called when Options Menu is first created
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        //populate option menu
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    //called when user is inactive from activity
    //save stuff here
    @Override
    public void onPause(){
        super.onPause();
        //stop monitoring sensors
        motionMonitor.stop();
        motionMonitor=null;
    }

    //Android Lint suggests that Handlers should be static because memory leak might occur
    static class MotionHandler extends Handler{
        private final int ACCEL_DATA = 0xFE;
        private final int ROTATE_DATA = 0xFD;

        private final String ACCEL_VECTOR="accel_vector";
        private final String ROTATE_VECTOR="rotate_vector";
        
        //since the handler is static we have to use a weak reference to communicate with
        //with the main UI activity
        private final WeakReference<BTRobotRemoteActivity> outer;
        
        public MotionHandler(BTRobotRemoteActivity activity){
            outer = new WeakReference<BTRobotRemoteActivity> (activity);
        }
        @Override
        public void handleMessage(Message msg){
            //get the main UI activity from weak reference
            BTRobotRemoteActivity activity = outer.get();
            //successfully got reference to main UI activity
             String string = "";
            if(activity != null){
                switch(msg.what){
                //acceleration data
                case ACCEL_DATA:{
                     Bundle b = (Bundle) msg.obj;
                     float [] data = b.getFloatArray(ACCEL_VECTOR);
                    if(data == null){Log.e(TAG, "Failed type message data to float []");}
                     float x = data[0];
                     float  y = data[1];
                     float  z = data[2];
                     string += "Accelerometer[x, y, z]: " + String.format("%.4f, %.4f, %.4f\n", x, y, z);
                     TextView stats = (TextView) activity.findViewById(R.id.accel_stats);
                     stats.setText(string);
                     
                     //forward
                     if(y <= -0.15f) {activity.findViewById(R.id.forward).setPressed(true);}
                     else{activity.findViewById(R.id.forward).setPressed(false);}

                     //backward
                     if(y>=0.15f){activity.findViewById(R.id.bottom).setPressed(true);}
                     else{activity.findViewById(R.id.bottom).setPressed(false);}

                     //left
                     if(x >= 0.15f) {activity.findViewById(R.id.left).setPressed(true);}
                     else{activity.findViewById(R.id.left).setPressed(false);}

                    //left 
                     if(x<= -0.15f) {activity.findViewById(R.id.right).setPressed(true);}
                     else{activity.findViewById(R.id.right).setPressed(false);}

                    break;
                }
                //rotation data
                case ROTATE_DATA:{
                     Bundle b = (Bundle) msg.obj;
                     float [] data = b.getFloatArray(ROTATE_VECTOR);
                     float x = data[0];
                     float  y = data[1];
                     float  z = data[2];
                    if(data == null){Log.e(TAG, "Failed type message data to float []");}
                     string += "Gyroscope[x, y, z]: " + String.format("%.4f, %.4f, %.4f\n", x, y, z);
                     TextView stats = (TextView) activity.findViewById(R.id.gyro_stats);
                     stats.setText(string);
                    break; 
                }
                default:{
                     Log.d(TAG, "recieved foreign unknown object");
                }}
            }
            else {
               Log.e(TAG, "failed to typecast weak reference");
            } 
        }
  }

}
