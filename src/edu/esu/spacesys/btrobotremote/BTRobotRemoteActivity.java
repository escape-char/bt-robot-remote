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

import android.bluetooth.BluetoothAdapter;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.TextView;
import android.view.View;

public class BTRobotRemoteActivity extends Activity
{
    private static final String TAG = "BT-Robot-Remote";
    
    //monitors device for motions
    private MotionMonitor motionMonitor;
    
    //type of data one can receive from
    //motion monitor
    private final int ACCEL_DATA = 0;
    private final int ROTATE_DATA = 1;
    
    //motion stats go in here
    private TextView statsArea;

    //handler to communicate with other threads
    private UiHandler mHandler;

    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothClientService mClientService = null;

    // Key names received from the BluetoothClientService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private String mConnectedDeviceName = null;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        final ActionBar actionBar = getActionBar();
        
        mHandler = new UiHandler(this);

        if(actionBar == null){Log.e(TAG, "There is no ACTIONBAR");}
        actionBar.setDisplayShowTitleEnabled(false);

        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            //finish(); //destroy activity
        }
    }
    public void setDeviceName(String name){
        mConnectedDeviceName = name;
    }
    public String getDeviceName(){
        return mConnectedDeviceName;
    }
    @Override
    public void onStart(){
        super.onStart();
        Log.i(TAG, "++ ON START ++");
    }
    public void setupBluetooth(){
        Log.d(TAG, "setupBluetooth()");
        // Initialize the BluetoothChatService to perform bluetooth connections
        //mClientService = new BluetoothClientService(this, mHandler);
    }

    //called when application is first created and resuming from pause
    //put initilization stuff here
    @Override
    public void onResume(){
        Log.i(TAG, "-- ON RESUME --");
         super.onResume();
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
    @Override
    public void onPause(){
        super.onPause();
        Log.i(TAG, "-- ON PAUSE --");

        //stop motion monitor
        if(motionMonitor!=null){motionMonitor.stop();motionMonitor=null;}
    }
   @Override
    public void onStop(){
        super.onStop();
        Log.i(TAG, "-- ON STOP --");
        //stop bluetooth service
        if(mClientService != null)mClientService.stop(); mClientService = null;

    }
    private void sendCommand(int command){
        //not connected
        if(mClientService.getState() != BluetoothClientService.STATE_CONNECTED)
            return;
    }
  public  void updateOrientation(float [] data){
     if(data == null){Log.e(TAG, "Failed type message data to float []"); return;}
     if(data.length != 3){Log.e(TAG, "updateOrientation data must have length of 3");return;}

     float x = data[0];
     float  y = data[1];
     float  z = data[2];
     String msg = "Accelerometer[x, y, z]: " + String.format("%.4f, %.4f, %.4f\n", x, y, z);
     TextView stats = (TextView) findViewById(R.id.accel_stats);
     stats.setText(msg);
                   
     //forward
     if(y <= -0.15f) {findViewById(R.id.forward).setPressed(true);}
     else{findViewById(R.id.forward).setPressed(false);}

     //backward
     if(y>=0.15f){findViewById(R.id.bottom).setPressed(true);}
     else{findViewById(R.id.bottom).setPressed(false);}

     //left
     if(x >= 0.15f) {findViewById(R.id.left).setPressed(true);}
     else{findViewById(R.id.left).setPressed(false);}

    //left 
     if(x<= -0.15f) {findViewById(R.id.right).setPressed(true);}
     else{findViewById(R.id.right).setPressed(false);}
  }
  public void setStatus(int status){
  }
  //this is called when status button is clicked
  public void onStatusClicked(View view){
      Log.i(TAG, "-- ON STATUS CLICKED --");
      ToggleButton tb = (ToggleButton) view;

      if(tb.isChecked()){
          Log.i(TAG, "-- ON STATUS CHECKED --");
      }
      else{
          Log.i(TAG, "-- ON STATUS UNCHECKED --");
      }

  }
    //Android Lint suggests that Handlers should be static because memory leak might occur
    static class UiHandler extends Handler{

        private final int ACCEL_DATA = 0xFE;
        private final int ROTATE_DATA = 0xFD;

        private final String ACCEL_VECTOR="accel_vector";
        private final String ROTATE_VECTOR="rotate_vector";
        
        //since the handler is static we have to use a weak reference to communicate with
        //with the main UI activity
        private final WeakReference<BTRobotRemoteActivity> outer;
        
        public UiHandler(BTRobotRemoteActivity activity){
            outer = new WeakReference<BTRobotRemoteActivity> (activity);
        }
        @Override
        public void handleMessage(Message msg){
            //get the main UI activity from weak reference
            BTRobotRemoteActivity activity = outer.get();

            //can't access main UI activity
            if(activity == null){return;}

            //find out what message was sent
            switch(msg.what){
            //bluetooth
            case BluetoothClientService.MESSAGE_STATE_CHANGE:{
                //update bluetooth connection status
                activity.setStatus(msg.arg1);
                break;
            }
            //service wrote data 
            case BluetoothClientService.MESSAGE_WRITE:{
                 //store data as a string
                 String writeMessage = new String((byte[]) msg.obj);
                break;
            }
            //service read data
            case BluetoothClientService.MESSAGE_READ:{
                String readMessage = new String((byte[])msg.obj, 0, msg.arg1);
                break;
            }
            //service retreived device's name
            case BluetoothClientService.MESSAGE_DEVICE_NAME:{
                // save the connected device's name
                activity.setDeviceName(msg.getData().getString(DEVICE_NAME));
                Toast.makeText(activity.getApplicationContext(), "Connected to "
                               + activity.getDeviceName(), Toast.LENGTH_SHORT).show();
                break;
            }
            //motion sensor
            //TODO
            case MotionMonitor.MESSAGE_MOTION:{
                 if(msg.arg1 == MotionMonitor.ARG_ACCEL){
                     Bundle b = (Bundle) msg.obj;
                     if(b == null){Log.e(TAG, "Failed to convert message object to bundle");}
                     float [] data = b.getFloatArray(MotionMonitor.KEY_ACCEL);
                     activity.updateOrientation(data);
                 }
                 else if(msg.arg1 == MotionMonitor.ARG_GYRO){
                     Bundle b = (Bundle) msg.obj;
                    if(b == null){Log.e(TAG, "Failed type message data to float []");}
                    //set data in gyrostats view 
                     float [] data = b.getFloatArray(MotionMonitor.KEY_ROTATE);
                     TextView stats = (TextView) activity.findViewById(R.id.gyro_stats);
                     stats.setText(String.format("%4f, %4f, %4f", data[0], data[1], data[2]));
                 }
                 else{Log.e(TAG, "Unsupported argument for MESSAGE_MOTION in handleMessage()");}
            }
            //ignore other types
            default:{
            }}
      }
    }
}
