package edu.esu.spacesys.btrobotremote;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.lang.Math;
import android.content.Intent;

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
import android.bluetooth.BluetoothDevice;
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

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    //header for message
    //every message must have this heaader
    private int HEADER = 'H';

    //message commands
    private char MOVE = 'M';
    private char  IR = 'I';
    private char SONAR='S';

    //parameters for move commands
    private char LEFT='L';
    private char RIGHT='R';
    private char FORWARD='F';
    private char BACKWARD='B';

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
            finish(); //destroy activity
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
        // Initialize the BluetoothClient to perform bluetooth connections
        if(mClientService == null) mClientService = new BluetoothClientService(this, mHandler);
    }

    //called when application is first created and resuming from pause
    //put initilization stuff here
    @Override
    public void onResume(){
        Log.i(TAG, "-- ON RESUME --");
         super.onResume();
        // If BT is not on, request that it be enabled.false
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
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

  //updates the oriention of the device based on data read from accelerometer
  //this is what highlights the buttons based rotation of phone
  public  void updateOrientation(float [] data){
     if(data == null){Log.e(TAG, "Failed type message data to float []"); return;}
     if(data.length != 3){Log.e(TAG, "updateOrientation data must have length of 3");return;}

     float x = data[0];
     float  y = data[1];
     float  z = data[2];

     //use this as the threshold for detecting direction
     final float NEUTRAl = 0.01f;
     final float THRESHOLD  = 0.10f;

     //commands to send based on orientation
     final String MOVE_FORWARD="HMF";
     final String MOVE_BACKWARD="HMB";
     final String MOVE_RIGHT="HMR";
     final String MOVE_LEFT="HML";

     String msg = "Accelerometer[x, y, z]: " + String.format("%.4f, %.4f, %.4f\n", x, y, z);
     TextView stats = (TextView) findViewById(R.id.accel_stats);
     stats.setText(msg);
     //forward
     if(y <= -(THRESHOLD)) {
         findViewById(R.id.forward).setPressed(true);
         mClientService.write(MOVE_FORWARD.getBytes());
     }
     else{findViewById(R.id.forward).setPressed(false);}

     //backward
     if(y>=THRESHOLD){
         findViewById(R.id.bottom).setPressed(true);
         mClientService.write(MOVE_BACKWARD.getBytes());
     }
     else{findViewById(R.id.bottom).setPressed(false);}

     //left
     if(x >= THRESHOLD) {
         findViewById(R.id.left).setPressed(true);
         mClientService.write(MOVE_LEFT.getBytes());

     }
     else{findViewById(R.id.left).setPressed(false);}

     //right
     if(x<= -(THRESHOLD)) {
         findViewById(R.id.right).setPressed(true);
         mClientService.write(MOVE_RIGHT.getBytes());
     }
     else{findViewById(R.id.right).setPressed(false);}
  }
  //sets the status for bluetooth connection
  public void setStatus(int status){
    Log.d(TAG, "-- SET STATUS --");
    switch(status){
        //connecting to remote device
        case BluetoothClientService.STATE_CONNECTING:{
                Toast.makeText(this, "Connecting to remote bluetooth device...", Toast.LENGTH_SHORT).show();
                ToggleButton statusButton  = (ToggleButton) this.findViewById(R.id.status_button);
                statusButton.setBackgroundResource(R.drawable.connecting_button);
            break;
        }
        //not connected
        case BluetoothClientService.STATE_NONE:{
                Toast.makeText(this, "Unable to connect to remote device", Toast.LENGTH_SHORT).show();
                ToggleButton statusButton  = (ToggleButton) this.findViewById(R.id.status_button);
                statusButton.setBackgroundResource(R.drawable.disconnect_button);
                statusButton.setTextOff(getString(R.string.disconnected));
                statusButton.setChecked(false);
            break;
        }
        //established connection to remote device
        case BluetoothClientService.STATE_CONNECTED:{
                Toast.makeText(this, "Connection established with remote bluetooth device...", Toast.LENGTH_SHORT).show();
                ToggleButton statusButton  = (ToggleButton) this.findViewById(R.id.status_button);
                statusButton.setBackgroundResource(R.drawable.connect_button);
                statusButton.setTextOff(getString(R.string.connected));
                statusButton.setChecked(true);
                //start motion monitor
                 motionMonitor = new MotionMonitor(this, mHandler);
                 motionMonitor.start();
                break;
        }
    }
  }
  //this is called when status button is clicked
  //the status button is the circle in the middle of the other buttons
  public void onStatusClicked(View view){
      Log.i(TAG, "-- ON STATUS CLICKED --");
      ToggleButton tb = (ToggleButton) view;

      //let setStatus change the check status instead
      tb.setChecked(false);

      if(!tb.isChecked()){
         Log.i(TAG, "-- ON STATUS CHECKED --");
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
      }
      else{
          Log.i(TAG, "-- ON STATUS UNCHECKED --");
      }

  }    
  //attempts a connection with remote device
  //data = contains information about device (Mac address mostly)
   private void connectToDevice(Intent data, boolean secure){
        Log.i(TAG, "-- connect to device --");
        if(data == null){Log.e(TAG, "data is null");}
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);

        Log.i(TAG, "-- getting  to device --");
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        Log.i(TAG, "-- connecting to device  to device --");
        // Attempt to connect to the device
        if(mClientService == null){Log.e(TAG, "bluetooth client service is null");}
        mClientService.connect(device, secure);
        Log.i(TAG,"-- after connection function");
    }
    //this method is called once an activity has returned
    //the activity must have been called with startActivityWithResult method first
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        
        Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        //for now, only dealiing with insecure connection
        //i don't think its possible to have secure connections with a serial profile
        case REQUEST_CONNECT_DEVICE_INSECURE:
               if(resultCode == Activity.RESULT_OK){
                   setupBluetooth();//since BT is enabled, setup client service
                   connectToDevice(data, false);
                }
               else
                    setStatus(BluetoothClientService.STATE_NONE);
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth is now enabled", Toast.LENGTH_SHORT).show();
            }
            else {
                // User did not enable Bluetooth or an error occurred
                Log.d(TAG, "BT not enabled");
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
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
