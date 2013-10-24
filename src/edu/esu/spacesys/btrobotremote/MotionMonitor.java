package edu.esu.spacesys.btrobotremote;
import java.lang.Math;
import java.lang.InterruptedException;
import android.util.Log;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

//Monitors the motion of an android device
//uses the accelerometer and gyroscope to 
//monitor motions
//TODO: implement gravity and rotation sensors (is it more accurate?)
public class MotionMonitor{
    private final String TAG = "Motion_Sensor";

    //Motion Monitor States
    //STATE_MONITORING = MotionMonitor is listening to sensors via Monitor thread
    //STATE_IDLE = MotionMonitor is not doing anything, and is not listening to sensors
    //TODO = perhaps a STATE_LOGGING for logging data to a file or database?
    private static final int STATE_MONITORING = 1;
    private static final int STATE_IDLE = 0;
    private int mState;

    //vectors for readings
    private float accel[]; //most recent acceleration values read from device
    private float prevAccel[]; //previous acceleration values  read from device
    private float quaterRotation[];//needed to get rotation matrix
    private float rotation[];  //current rotation of device

    private final int EPSILON = 5; //margin of error;
    private float gyroTimeStamp; //most recent time of rotation readings
    private final float NANO_2_SEC = 1.0f / 1000000000.0f;

    //use this handler to communicate with thread that is using this class
    private final Handler mHandler;

    //thread for listening to sensors
    private SensorMonitorThread mMonitorThread;

    //tells what activity to get sensor service from
    private final Context mContext;
        
    //keys for accessing data into bundle
    public static final String KEY_ACCEL  = "key_accel_vector"; //acceleration vector
    public static final String KEY_ROTATE = "key_rotate_vector";//rotation vector

    public static  final int MESSAGE_MOTION = 0xFE;

    //arguments with message
    public static final int ARG_ACCEL = 0;
    public static final int ARG_GYRO = 1;

    boolean keepLog = false;



    /**
    *Constructor for Motion Monitor
    *@param context = activity to get sensors from
    *@param handler = handler used to send sensor data to
    */
    public MotionMonitor(Context context, Handler handler, boolean log){
        mHandler = handler;
        mContext = context;
        keepLog = log;

        mMonitorThread = null;

        //default state is idle;
        mState = STATE_IDLE;
        
        //initialize vectors and time stamps
        accel = new float[3];
        prevAccel=new float[3];
        rotation = new float[3];
        quaterRotation = new float [4];
        gyroTimeStamp = 0;
    }
    public synchronized void start(){
        //cancel any previously running threads 
        if( mState == STATE_MONITORING){
            if(mMonitorThread != null)stop();
        }
        //start monitoring
        mMonitorThread = new SensorMonitorThread(mContext);
        mMonitorThread.start();
        mState=STATE_MONITORING;
    }
    public synchronized  void stop(){
        Log.i(TAG, "Stopping Motion Monitor..");
        if(mMonitorThread == null){return;}
        mMonitorThread.cancel();

        //we need to use join method to interupt thread from whatever it is doing
        try{
            mMonitorThread.join();
        }
        catch(InterruptedException e){
            Log.e(TAG, "Interupted monitor process from waiting");
        }
        mState = STATE_IDLE;
        mMonitorThread = null;
    }
    //gets the current state of sensor monitor
    public synchronized int getState(){return mState;}
    /**
     *performs a high pass filter on given values. 
     *This ignores values with low reading, and keeps values with higher readings
     *See: http://en.wikipedia.org/wiki/High-pass_filter#Algorithmic_implementation
     *Useful for the accelerometer
     *@param values = values to perform high pass filter on
     */
    private synchronized void high_pass_filter(final float  [] values ){
          //alpha determines the memory of the filter
          //if alpha = 0, the filter will do nothing and the values will remain unchanged (short memory)
          //if alpha = 1, the filter will output the previously read valued (long memory);
          //TODO can I generate alpha dynamically?
          final float alpha = 0.2f;

          //loss pass filter
          prevAccel[0] = alpha * prevAccel[0] + (1 - alpha) * values[0];
          prevAccel[1] = alpha * prevAccel[1] + (1 - alpha) * values[1];
          prevAccel[2] = alpha * prevAccel[2] + (1 - alpha) * values[2];

        //subtract low pass filter results  from read values to apply high pass filter
          accel[0] = values[0] - prevAccel[0];
          accel[1] = values[1] - prevAccel[1];
          accel[2] = values[2] - prevAccel[2];
    }
    /**
    *update the rotation vector based on values read from the gyroscope
    *the values vector is converted into a quaternion to retrieve the current rotation matrix
    *To update the rotation, the curent rotation vector is multiplied by the rotation matrix

    *@param values = valued read from gyrometer (x, y, z axis)
    */
    private synchronized void update_rotation_vector(float [] values, float timestamp){
    
        if(gyroTimeStamp !=0){
            final float dT = (timestamp - gyroTimeStamp) * NANO_2_SEC;

            // Axis of the rotation sample, not normalized yet.
            float axisX = values[0];
            float axisY = values[1];
            float axisZ = values[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = (float) Math.sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            if (omegaMagnitude > EPSILON) {
              axisX /= omegaMagnitude;
              axisY /= omegaMagnitude;
              axisZ /= omegaMagnitude;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
            float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
            quaterRotation[0] = sinThetaOverTwo * axisX;
            quaterRotation[1] = sinThetaOverTwo * axisY;
            quaterRotation[2] = sinThetaOverTwo * axisZ;
            quaterRotation[3] = cosThetaOverTwo;
      }
      gyroTimeStamp = timestamp;

      //get the rotation matrix from quaternion vecter
      float[] deltaRotationMatrix = new float[9];
      SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, quaterRotation);

      //get the orientation of the device based on the rotation matrix
      SensorManager.getOrientation(deltaRotationMatrix, rotation);
    }
    private class SensorMonitorThread extends Thread implements SensorEventListener{
        private SensorManager mManager;

        //sensors to monitor
        private Sensor mAccelSensor;
        private Sensor mGyroSensor;

        private volatile Looper mLoop;
        private volatile boolean keepMonitoring = false;
        
        //we aren't monitoring magnometer
        //but we need it for the getRotationMatrix
        private Sensor magSensor;
        
        //used for logging sensor stats
         OutputStreamWriter gyroLog;
         OutputStreamWriter accelLog;
        public SensorMonitorThread(Context context){
            mManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            this.setName("SensorMonitorThread");
            if(keepLog){
                out = new OutputStreamWriter(openFileOutput("gyroLog.csv",0));
            }
        }
        @Override
        public void run(){
            Log.i(TAG, "Sensor Monitor is running");

            //Googe's documentation states that getDefaultSensor might do averages or filtering
            //but their code does no such thing; it just returns first element in the sensor list
            //@see: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/hardware/SensorManager.java
            //To save code, i'm using getDefaultSensor. 
            mAccelSensor = mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mGyroSensor = mManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            Looper.prepare(); //prepare message queue

            //need to save looper to be able to call its quit method later
            mLoop = Looper.myLooper(); 

            //get handler for this thread
            Handler monitorHandler = new Handler();

            //register sensor listeners for this thread 
            //note: have to pass handler to this thread, or else it will listen on main UI thread
            mManager.registerListener(this, mAccelSensor, SensorManager.SENSOR_DELAY_UI, monitorHandler);
            mManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_UI, monitorHandler);

            Looper.loop(); //thread stays alive through this message loop
            
            Log.i(TAG, "Sensor Monitor thread successfully shutdown");
        }
       @Override
        //sensor events occur here
        public void onSensorChanged(SensorEvent event){
            if(event == null){Log.e(TAG, "Sensor event is null");}
            //calculate acceleration through high pass filters
            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                high_pass_filter(event.values);
                
                //send result back to handler
                  Bundle bundle = new Bundle(); 
                  bundle.putFloatArray(KEY_ACCEL, accel);
                  Message msg = mHandler.obtainMessage(MESSAGE_MOTION, ARG_ACCEL, -1,  bundle); 
                  mHandler.sendMessageDelayed(msg, 800);
            }
            //calculate rotation using the gyroscope
            else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
                update_rotation_vector(event.values, event.timestamp);
                
                  //send result back to handler
                 Bundle bundle = new Bundle(); 
                 bundle.putFloatArray(KEY_ROTATE, rotation);
                 String result = String.format("%3f %3f %3f", rotation[0],rotation[1], rotation[2]);
                 gyroLog.write(result);
                 gyroLog.write.newLine();
                  Message msg = mHandler.obtainMessage(MESSAGE_MOTION, ARG_GYRO, -1, bundle); 
                  mHandler.sendMessageDelayed(msg, 200);
            }
            else{
                //don't care
            }
        }
        public void cancel(){
            Log.i(TAG, "attempting to cancel thread");
            mManager.unregisterListener(this); //remember to unregister sensors
            mLoop.quit(); //stops thread
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy){

        }
    }
}
