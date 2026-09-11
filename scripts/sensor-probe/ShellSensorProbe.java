import android.content.Context;
import android.hardware.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.Arrays;

/** Bounded read-only shell-UID probe; no settings or permission changes. */
public class ShellSensorProbe {
 public static void main(String[] args) throws Exception {
  Looper.prepareMainLooper();
  Class<?> at=Class.forName("android.app.ActivityThread");
  Object thread=at.getMethod("systemMain").invoke(null);
  Context system=(Context)at.getMethod("getSystemContext").invoke(thread);
  Context context=system.createPackageContext("com.android.shell",0);
  System.out.println("uid="+android.os.Process.myUid()+" SSENSOR="+context.checkSelfPermission("com.samsung.permission.SSENSOR"));
  SensorManager sm=context.getSystemService(SensorManager.class);
  SensorEventListener listener=new SensorEventListener(){
   long last; public void onSensorChanged(SensorEvent e){if(e.sensor.getType()!=36 && android.os.SystemClock.uptimeMillis()-last<500)return;last=android.os.SystemClock.uptimeMillis();System.out.println("event type="+e.sensor.getType()+" values="+Arrays.toString(e.values));}
   public void onAccuracyChanged(Sensor s,int a){}
  };
  for(Sensor s:sm.getSensorList(Sensor.TYPE_ALL)){
   System.out.println("sensor type="+s.getType()+" name="+s.getName()+" resolution="+s.getResolution());
   String name=s.getName().toLowerCase();
   if(s.getType()==36 || s.getType()==1 || s.getType()==4 || (s.getType()>=65687 && s.getType()<=65690) || name.contains("fold") || name.contains("lid_angle")){
    try{System.out.println("register type="+s.getType()+" result="+sm.registerListener(listener,s,SensorManager.SENSOR_DELAY_GAME));}
    catch(Exception e){System.out.println("register type="+s.getType()+" error="+e);}
   }
  }
  try {
   Object manager=context.getSystemService("scontext");
   System.out.println("contextManager="+manager);
   if(manager!=null)for(Method m:manager.getClass().getMethods())if(m.getName().contains("Available")||m.getName().contains("ServiceList"))System.out.println("contextMethod="+m);
   Class<?> c=Class.forName("com.samsung.android.hardware.context.SemContextManager");
   Object manager2=c.getConstructor(Context.class,Looper.class).newInstance(context,Looper.getMainLooper());
   System.out.println("contextServices="+c.getMethod("getCurrentServiceList").invoke(manager2));
   for(int id:new int[]{43,55})System.out.println("contextAvailable type="+id+" result="+c.getMethod("isAvailableService",int.class).invoke(manager2,id));
  }catch(Exception e){System.out.println("contextError="+e);}
  new Handler(Looper.getMainLooper()).postDelayed(()->{sm.unregisterListener(listener);System.out.println("finished");System.exit(0);},15000);
  Looper.loop();
 }
}
