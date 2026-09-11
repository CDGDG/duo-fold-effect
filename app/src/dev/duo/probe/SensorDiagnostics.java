package dev.duo.probe;

import android.content.Context;
import android.hardware.*;
import android.util.Log;

/** Report real API results; do not change hidden-API or permission policy. */
final class SensorDiagnostics {
  static void run(Context context) {
    SensorManager manager=context.getSystemService(SensorManager.class);
    SensorEventListener test=new SensorEventListener() {
      public void onSensorChanged(SensorEvent event){}
      public void onAccuracyChanged(Sensor sensor,int accuracy){}
    };
    for (Sensor sensor:manager.getSensorList(Sensor.TYPE_ALL)) {
      String name=(sensor.getName()+" "+sensor.getStringType()).toLowerCase();
      if(!name.contains("fold") && !name.contains("hinge"))continue;
      Log.i("DuoDiagnostics","sensor "+sensor+" resolution="+sensor.getResolution());
      try {
        boolean registered=manager.registerListener(test,sensor,SensorManager.SENSOR_DELAY_GAME);
        Log.i("DuoDiagnostics","type="+sensor.getType()+" registered="+registered);
      }catch(Exception e){Log.i("DuoDiagnostics","type="+sensor.getType()+" denied="+e);}
      finally{manager.unregisterListener(test,sensor);}
    }
    try {
      Object m=context.getSystemService("scontext");
      Log.i("DuoDiagnostics","scontext="+(m==null?"null":m.getClass().getName()));
      if(m!=null) {
        Class<?> constants=Class.forName("android.hardware.scontext.SContext");
        for(java.lang.reflect.Field field:constants.getFields()) {
          if(field.getType()!=int.class || !field.getName().contains("HALL"))continue;
          int type=field.getInt(null);
          Object available=m.getClass().getMethod("isAvailableService",int.class).invoke(m,type);
          Log.i("DuoDiagnostics","context "+field.getName()+" type="+type+" available="+available);
        }
      }
    }catch(Exception e){Log.i("DuoDiagnostics","context query="+e);}
    try {
      Class<?> type=Class.forName("com.samsung.android.hardware.context.SemContextManager");
      Object m=type.getConstructor(Context.class,android.os.Looper.class).newInstance(context,android.os.Looper.getMainLooper());
      Log.i("DuoDiagnostics","semcontext services="+type.getMethod("getCurrentServiceList").invoke(m));
    }catch(Exception e){Log.i("DuoDiagnostics","semcontext query="+e);}
  }
}
