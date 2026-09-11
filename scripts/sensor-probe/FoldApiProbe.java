import android.content.Context;
import android.os.Looper;
import java.lang.reflect.*;
/** Read-only inventory of potential alternate fold APIs, without permission changes. */
public class FoldApiProbe {
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");
  Object t=at.getMethod("systemMain").invoke(null);
  Context c=(Context)at.getMethod("getSystemContext").invoke(t);
  for(String name:new String[]{"input","input_device","scontext","device_state","sensor"}){
   Object manager=c.getSystemService(name);System.out.println("SERVICE "+name+" "+(manager==null?"null":manager.getClass().getName()));
   if(manager!=null)for(Method m:manager.getClass().getMethods()){
    String n=m.getName().toLowerCase();if(n.contains("fold")||n.contains("hinge")||n.contains("lid"))System.out.println(m);
   }
  }
 }
}
