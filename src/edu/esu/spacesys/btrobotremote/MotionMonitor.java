package edu.esu.spacesys.btrobotremote;
import java.lang.Math;
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
    private final int STATE_MONITORING = 1;
    private final int STATE_IDLE = 0;
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
        
    /**
    *Constructor for Motion Monitor
    *@param context = activity to get sensors from
    *@param handler = handler used to send sensor data to
    */
    public MotionMonitor(Context context, Handler handler){
        mHandler = handler;
        mContext = context;

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
    public void start(){
        //cancel any previously running threads 
        if( mState == STATE_MONITORING || mMonitorThread != null){
            mState = STATE_IDLE;
            mMonitorThread = null;
        }
        //start monitoring
        mMonitorThread = new SensorMonitorThread(mContext);
        mMonitorThread.start();
        mState=STATE_MONITORING;
    }
    public void stop(){
        mState = STATE_IDLE;
        mMonitorThread = null;
    }
    public int getState(){return mState;}
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
        
        //we aren't monitoring magnometer
        //but we need it for the getRotationMatrix
        private Sensor magSensor;

        //keys for accessing data into bundle
        private final String ACCEL_VECTOR  = "accel_vector"; //acceleration vector
        private final String ROTATE_VECTOR = "rotate_vector";//rotation vector

        //constants for type of data to send to calling thread
        private final int ACCEL_DATA = 0xFE;
        private final int ROTATE_DATA = 0xFD ;

        public SensorMonitorThread(Context context){
            mManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
            this.setName("SensorMonitorThread");
        }
        @Override
        public void run(){
            Log.i(TAG, "running Sensor Monitor");
            //Google's Documentation, as of 06/15/13,  on SensorManager indicates that getDefaultSensor will may include averages and
            //exclude high readings. They recommend to use getSensorList for raw data. However, their source code indicates that
            //all they do is get the first sensor in the sensor list; there is no filtering of readings
            //@see: https://github.com/android/platform_frameworks_base/blob/master/core/java/android/hardware/SensorManager.java
            //To save code, i'm using getDefaultSensor. 
            mAccelSensor = mManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mGyroSensor = mManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

            //by default the registerListener method for Sensors listens on the main  UI thread
            //we need to pass the listener the handler to this thread or else events will occur in th UI thread
            Looper.prepare();
                    Handler monitorHandler = new Handler();
                    mManager.registerListener(this, mAccelSensor, SensorManager.SENSOR_DELAY_UI, monitorHandler);
                    mManager.registerListener(this, mGyroSensor, SensorManager.SENSOR_DELAY_UI, monitorHandler);
            Looper.loop();

            //keep going while mState is monitoring
            //TODO there has to be a better way of keeping the thread alive
            while(mState == STATE_MONITORING){}
        }
       @Override
        //sensor events occur here
        public void onSensorChanged(SensorEvent event){
            //calculate acceleration through high pass filters
            if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
                high_pass_filter(event.values);
                
                //send result back to handler
                  Bundle bundle = new Bundle(); 
                  bundle.putFloatArray(ACCEL_VECTOR, accel);
                  Message msg = Message.obtain(mHandler, ACCEL_DATA, bundle); 
                  mHandler.sendMessageDelayed(msg, 200);

            }
            //calculate rotation using the gyroscope
            else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
                update_rotation_vector(event.values, event.timestamp);
                
                  //send result back to handler
                 Bundle bundle = new Bundle(); 
                 bundle.putFloatArray(ROTATE_VECTOR, rotation);
                  Message msg = Message.obtain(mHandler, ROTATE_DATA, bundle); 
                  mHandler.sendMessageDelayed(msg, 200);
            }
            else{
                //don't care
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy){

        }
    }
}
