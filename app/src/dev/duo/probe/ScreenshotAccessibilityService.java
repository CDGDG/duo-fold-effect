package dev.duo.probe;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;

/** Holds the 90-degree presentation until the next actual angle event. */
public class ScreenshotAccessibilityService extends AccessibilityService implements SensorEventListener {
  public static boolean connected;
  static ScreenshotAccessibilityService instance;
  final Handler handler=new Handler(Looper.getMainLooper());
  SensorManager sensors; DisplayManager displays; WindowManager wm;
  Glass glass; FoldMotion motion=new FoldMotion(0);
  float actualAngle=-1;
  int width,height,displayState,revision,previewGeneration;
  boolean requesting,ending,previewing,controlVisible;
  long lastCapture,allowCaptureAt;
  int failures;
  final Runnable request=()->capture();
  final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
    public void onDisplayAdded(int id){}
    public void onDisplayRemoved(int id){}
    public void onDisplayChanged(int id){if(id==0)displayChanged();}
  };
  @Override protected void onServiceConnected(){
    if(connected && instance==this)return;
    instance=this;connected=true;ending=false;
    stopService(new Intent(this,CaptureService.class));
    wm=getSystemService(WindowManager.class);sensors=getSystemService(SensorManager.class);
    displays=getSystemService(DisplayManager.class);readDisplay();
    displays.registerDisplayListener(displayListener,handler);
    Sensor hinge=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
    Log.i("DuoAccess","ANGLE-HOLD connected sensor="+(hinge!=null&&sensors.registerListener(this,hinge,SensorManager.SENSOR_DELAY_GAME)));
  }
  void readDisplay(){Display d=displays.getDisplay(0);if(d==null)return;Point p=new Point();d.getRealSize(p);width=p.x;height=p.y;displayState=d.getState();}
  boolean available(){return !ending&&!controlVisible&&!getSystemService(KeyguardManager.class).isKeyguardLocked()&&displayState==Display.STATE_ON;}
  void displayChanged(){
    int w=width,h=height,state=displayState;readDisplay();
    if(w==width&&h==height&&state==displayState)return;
    revision++;failures=0;hide();
    // Never map the old panel image onto the new panel. Keep the angle state.
    allowCaptureAt=SystemClock.uptimeMillis()+90;
    Log.i("DuoAccess","panel="+width+"x"+height+" target="+motion.target+" state="+displayState);
    if(available()&&!motion.endpoint())schedule(90);
    else if(motion.endpoint())motion=new FoldMotion(motion.target);
  }
  @Override public void onAccessibilityEvent(AccessibilityEvent e){
    // Event content is not read. Window notifications allow a retry after unlock.
    readDisplay();if(glass==null&&!requesting&&!motion.endpoint()&&available()&&failures<4)schedule(90);
  }
  @Override public void onInterrupt(){revision++;hide();}
  @Override public void onSensorChanged(SensorEvent e){
    float v=e.values[0];if(Float.isNaN(v)||v<0||v>180)return;
    float previous=actualAngle;actualAngle=v;
    Log.i("DuoAccess","hinge="+v);
    if(previewing){if(v==previous)return;previewGeneration++;previewing=false;}
    if(previous<0)motion=new FoldMotion(v/180f);
    if(v!=previous||previous<0)angle(v,"sensor");
  }
  public void onAccuracyChanged(Sensor sensor,int accuracy){}
  void angle(float value,String source){
    motion.targetAngle(value);failures=0;
    Log.i("DuoAccess","target="+motion.target+" position="+motion.position+" source="+source);
    if(glass!=null){glass.wake();return;}
    if(motion.endpoint()){
      revision++;handler.removeCallbacks(request);motion=new FoldMotion(motion.target);
      Log.i("DuoAccess","endpoint clean (no replay)");return;
    }
    schedule(0);
  }
  void schedule(long delay){handler.removeCallbacks(request);handler.postDelayed(request,delay);}
  void capture(){
    readDisplay();long now=SystemClock.uptimeMillis();
    if(!available()||requesting||glass!=null||motion.endpoint()||failures>=4)return;
    long wait=Math.max(allowCaptureAt-now,400-(now-lastCapture));
    if(wait>0){schedule(wait);return;}
    final int token=revision,w=width,h=height;requesting=true;lastCapture=now;
    Log.i("DuoAccess","capture request panel="+w+"x"+h);
    try{takeScreenshot(0,getMainExecutor(),new TakeScreenshotCallback(){
      public void onSuccess(ScreenshotResult result){
        requesting=false;
        try{
          readDisplay();
          if(token!=revision||w!=width||h!=height||!available()||motion.endpoint()){schedule(90);return;}
          Bitmap hw=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hw==null)return;
          if(hw.getWidth()!=w||hw.getHeight()!=h){hw.recycle();failures++;schedule(420);return;}
          Bitmap copy=hw.copy(Bitmap.Config.ARGB_8888,false);hw.recycle();
          // Preserve source pixels: the clear side must stay sharp and stationary.
          show(copy);Log.i("DuoAccess","snapshot="+w+"x"+h+" target="+motion.target);
        }finally{result.getHardwareBuffer().close();}
      }
      public void onFailure(int error){requesting=false;failures++;Log.w("DuoAccess","capture failed="+error);schedule(420);}
    });}catch(RuntimeException e){requesting=false;failures++;Log.e("DuoAccess","capture failed",e);}
  }
  void show(Bitmap bitmap){
    if(!available()||motion.endpoint())return;
    glass=new Glass(bitmap);
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
    p.setFitInsetsTypes(0);p.gravity=Gravity.TOP|Gravity.LEFT;
    p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    p.setTitle("Duo angle hold");
    try{wm.addView(glass,p);glass.wake();}catch(RuntimeException e){Log.e("DuoAccess","overlay failed",e);glass=null;}
  }
  void hide(){if(glass!=null){handler.removeCallbacks(glass);try{wm.removeViewImmediate(glass);}catch(Exception ignored){}glass=null;}}
  // Control screen stays usable even if the phone is held at the middle angle.
  static void controls(boolean visible){if(instance!=null){ScreenshotAccessibilityService s=instance;s.controlVisible=visible;
    if(visible){s.previewGeneration++;s.previewing=false;s.revision++;s.hide();s.motion=new FoldMotion(Math.max(0,s.actualAngle)/180f);}
    else if(!s.previewing&&!s.motion.endpoint())s.schedule(100);}}
  /** Explicit synthetic test only: 90 holds for six seconds, then endpoint. */
  public static boolean preview(boolean open){
    if(instance==null)return false;ScreenshotAccessibilityService s=instance;
    final int token=++s.previewGeneration;s.previewing=true;s.revision++;s.hide();s.motion=new FoldMotion(open?0:1);
    s.handler.postDelayed(()->{if(token==s.previewGeneration)s.angle(90,"preview-middle");},2000);
    s.handler.postDelayed(()->{if(token==s.previewGeneration)s.angle(open?180:0,"preview-endpoint");},8000);
    s.handler.postDelayed(()->{if(token==s.previewGeneration){s.previewing=false;s.motion=new FoldMotion(Math.max(0,s.actualAngle)/180f);}},9500);
    return true;
  }
  @Override public void onDestroy(){ending=true;connected=false;instance=null;handler.removeCallbacksAndMessages(null);hide();
    if(sensors!=null)sensors.unregisterListener(this);if(displays!=null)displays.unregisterDisplayListener(displayListener);super.onDestroy();}

  class Glass extends View implements Runnable {
    final Bitmap bitmap;final Paint paint=new Paint(3);
    final RenderNode surface=new RenderNode("Duo current app surface");
    final RenderNode soft=new RenderNode("Duo soft gradient"),deep=new RenderNode("Duo deep gradient");
    final String maskSource="uniform shader image; uniform float width; uniform float edge;"+
      "half4 main(float2 p) { float a=1.0-smoothstep(edge-0.20,edge+0.20,p.x/width); return image.eval(p)*a; }";
    final RuntimeShader softMask=new RuntimeShader(maskSource),deepMask=new RuntimeShader(maskSource);
    final RuntimeShader shade=new RuntimeShader("uniform float width; uniform float strength;"+
      "half4 main(float2 p) { float a=0.94*strength*(1.0-smoothstep(0.015,0.13,p.x/width)); return half4(0.0,0.0,0.0,a); }");
    final Paint shadePaint=new Paint();
    long previous;boolean firstDraw=true;
    Glass(Bitmap bitmap){super(ScreenshotAccessibilityService.this);this.bitmap=bitmap;}
    void wake(){handler.removeCallbacks(this);previous=SystemClock.uptimeMillis();handler.post(this);}
    public void run(){
      if(glass!=this)return;
      long now=SystemClock.uptimeMillis();boolean moving=motion.step((now-previous)/1000f);previous=now;
      setAlpha(motion.material());invalidate();
      if(moving){handler.postDelayed(this,16);return;}
      if(motion.endpoint()){hide();Log.i("DuoAccess","endpoint removed target="+motion.target);}
      else Log.i("DuoAccess","HOLD position="+motion.position+" target="+motion.target+" (waiting for sensor)");
    }
    void recordScreen(Canvas c,float w,float h){
      // A captured panel is already in its own display coordinates. Never add a
      // fictitious hinge to the flat cover or double-project the physical inner panel.
      c.drawBitmap(bitmap,0,0,paint);
    }
    void gradientLayer(Canvas canvas,RenderNode layer,RuntimeShader mask,float w,float h,float edge,float radius){
      if(radius<.05f)return;
      layer.setPosition(0,0,(int)w,(int)h);Canvas c=layer.beginRecording();
      recordScreen(c,w,h);layer.endRecording();
      mask.setFloatUniform("width",w);mask.setFloatUniform("edge",edge);
      layer.setRenderEffect(RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(mask,"image"),
        RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP)));
      canvas.drawRenderNode(layer);
    }
    protected void onDraw(Canvas canvas){
      if(firstDraw){firstDraw=false;Log.i("DuoAccess","first draw after capture request ms="+(SystemClock.uptimeMillis()-lastCapture)+" panel="+getWidth()+"x"+getHeight());}
      float w=getWidth(),h=getHeight(),strength=motion.material(),position=FoldMotion.clamp(motion.position);
      // Same source coordinates for sharp and blurred layers: no displaced duplicates.
      surface.setPosition(0,0,(int)w,(int)h);Canvas c=surface.beginRecording();
      recordScreen(c,w,h);surface.endRecording();
      surface.setRenderEffect(null);canvas.drawRenderNode(surface);
      float edge=1.05f-1.25f*position;
      gradientLayer(canvas,soft,softMask,w,h,edge+.06f,14*strength);
      gradientLayer(canvas,deep,deepMask,w,h,edge-.08f,60*strength);
      shade.setFloatUniform("width",w);
      shade.setFloatUniform("strength",strength);shadePaint.setShader(shade);
      canvas.drawRect(0,0,w,h,shadePaint);
    }
  }
}
