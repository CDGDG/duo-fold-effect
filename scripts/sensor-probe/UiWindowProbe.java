import android.app.UiAutomation;
import android.os.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.*;
import android.graphics.Rect;
import java.lang.reflect.*;
public class UiWindowProbe {
 static void node(AccessibilityNodeInfo n,int depth){
  if(n==null||depth>35)return;
  Rect r=new Rect();n.getBoundsInScreen(r);
  if(n.getText()!=null)System.out.println("NODE\t"+n.getText().toString().replace('\n',' ')+'\t'+r.left+","+r.top+","+r.right+","+r.bottom);
  for(int i=0;i<n.getChildCount();i++)node(n.getChild(i),depth+1);
 }
 public static void main(String[] args)throws Exception{
  HandlerThread t=new HandlerThread("duo-ui");t.start();
  Class<?> c=Class.forName("android.app.UiAutomationConnection");Object conn=c.getConstructor().newInstance();
  UiAutomation ui=(UiAutomation)UiAutomation.class.getConstructor(Looper.class,Class.forName("android.app.IUiAutomationConnection")).newInstance(t.getLooper(),conn);
  try{UiAutomation.class.getMethod("connect",int.class).invoke(ui,UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
   AccessibilityServiceInfo info=ui.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;ui.setServiceInfo(info);SystemClock.sleep(350);
   for(AccessibilityWindowInfo win:ui.getWindows()) {System.out.println("WINDOW "+win);node(win.getRoot(),0);}
  }finally{UiAutomation.class.getMethod("disconnect").invoke(ui);t.quitSafely();}
 }
}
