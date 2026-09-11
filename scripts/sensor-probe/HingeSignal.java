import android.content.Context;
import android.hardware.*;
import android.os.*;

/** Bounded shell sensor reader; no screen capture or permission modifications. */
public class HingeSignal {
 public static void main(String[] args) throws Exception {
  int seconds=Math.max(1,Math.min(300,Integer.parseInt(args[0])));
  Looper.prepareMainLooper();
  Class<?> at=Class.forName("android.app.ActivityThread");
  Object thread=at.getMethod("systemMain").invoke(null);
  Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
  Context context=system.createPackageContext("com.android.shell",0);
  SensorManager sm=context.getSystemService(SensorManager.class);
  Sensor sensor=sm.getDefaultSensor(36);
  SensorEventListener listener=new SensorEventListener(){
   public void onSensorChanged(SensorEvent e){System.out.println("HINGE="+e.values[0]);System.out.flush();}
   public void onAccuracyChanged(Sensor s,int a){}
  };
  if(sensor==null || !sm.registerListener(listener,sensor,SensorManager.SENSOR_DELAY_GAME))throw new IllegalStateException("Hinge registration failed");
  new Handler(Looper.getMainLooper()).postDelayed(()->{sm.unregisterListener(listener);System.exit(0);},seconds*1000L);
  Looper.loop();
 }
}
