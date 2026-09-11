package dev.duo.probe;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import java.nio.ByteBuffer;

/** A brief frozen-frame overlay, never presented as live cross-window blur. */
public class CaptureService extends Service implements SensorEventListener {
  public static boolean active;
  public static String lastStatus="중지됨 · 시작 버튼으로 화면 공유";
  Handler handler = new Handler(Looper.getMainLooper());
  MediaProjection projection;
  VirtualDisplay display;
  ImageReader reader;
  WindowManager wm;
  SensorManager sensors;
  Bitmap clean;
  Glass overlay;
  Runnable animationTick;
  long cleanAt, lastCopy, acceptAfter, frames, lastPulse;
  int captureW, captureH;
  float lastAngle = -1;
  boolean closing;
  boolean awaitingResizedFrame;
  int resizeGeneration;

  public IBinder onBind(Intent i) {return null;}
  public int onStartCommand(Intent i, int flags, int id) {
    if (i==null) {stopSelf();return START_NOT_STICKY;}
    if ("STOP".equals(i.getAction())) {stopSelf();return START_NOT_STICKY;}
    if ("TEST".equals(i.getAction())) {
      if(active) handler.postDelayed(() -> pulse("manual",1400),5000);
      else stopSelf();
      return START_NOT_STICKY;
    }
    if(active) return START_NOT_STICKY;
    NotificationManager nm=getSystemService(NotificationManager.class);
    nm.createNotificationChannel(new NotificationChannel("capture","Duo 화면 효과",NotificationManager.IMPORTANCE_LOW));
    PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,CaptureService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
    PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
    Notification n=new Notification.Builder(this,"capture").setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("Duo 캡처 효과 실행 중").setContentText("접기·펴기 시 화면 한 장으로 전환 효과 표시")
      .setContentIntent(open).addAction(new Notification.Action.Builder(null,"중지",stop).build()).setOngoing(true).build();
    startForeground(7,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    try {
      wm=getSystemService(WindowManager.class);
      projection=getSystemService(MediaProjectionManager.class).getMediaProjection(i.getIntExtra("result",0),i.getParcelableExtra("token"));
      if(projection==null) throw new IllegalStateException("No projection");
      projection.registerCallback(new MediaProjection.Callback() {
        @Override public void onStop() {
          if(!closing)lastStatus="시스템에서 종료됨 · 시작 버튼으로 다시 공유";
          Log.i("DuoCapture","projection stopped by system");stopSelf();
        }
        @Override public void onCapturedContentResize(int w,int h) {
          if(display!=null) resize(w,h);
        }
      },handler);
      Rect b=wm.getMaximumWindowMetrics().getBounds();
      makeReader(b.width(),b.height());
      display=projection.createVirtualDisplay("DuoCapture",captureW,captureH,getResources().getConfiguration().densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
      sensors=getSystemService(SensorManager.class);
      Sensor hinge=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
      boolean ok=hinge!=null && sensors.registerListener(this,hinge,SensorManager.SENSOR_DELAY_GAME);
      active=true;
      lastStatus="실행 중";
      Log.i("DuoCapture","started capture="+captureW+"x"+captureH+" sensor="+ok);
    } catch(Exception e) {lastStatus="시작 실패 · 다시 공유 필요";Log.e("DuoCapture","start failed",e);stopSelf();}
    return START_NOT_STICKY;
  }
  void makeReader(int w,int h) {
    float scale=Math.min(1,720f/Math.max(w,h));
    captureW=Math.max(2,Math.round(w*scale));captureH=Math.max(2,Math.round(h*scale));
    reader=ImageReader.newInstance(captureW,captureH,PixelFormat.RGBA_8888,3);
    reader.setOnImageAvailableListener(r -> {
      try(Image image=r.acquireLatestImage()) {
        long now=SystemClock.uptimeMillis();
        if(image==null || closing || overlay!=null || now<acceptAfter || now-lastCopy<80) return;
        lastCopy=now;
        Image.Plane p=image.getPlanes()[0];
        int stride=p.getRowStride()/p.getPixelStride();
        Bitmap padded=Bitmap.createBitmap(stride,image.getHeight(),Bitmap.Config.ARGB_8888);
        ByteBuffer bytes=p.getBuffer();padded.copyPixelsFromBuffer(bytes);
        Bitmap next=Bitmap.createBitmap(padded,0,0,image.getWidth(),image.getHeight());
        if(next!=padded)padded.recycle();
        if(clean!=null)clean.recycle();clean=next;cleanAt=now;frames++;
        if(frames==1 || frames%120==0)Log.i("DuoCapture","clean frames="+frames+" size="+next.getWidth()+"x"+next.getHeight());
      } catch(Exception e) {Log.e("DuoCapture","frame failed",e);}
    },handler);
  }
  void resize(int w,int h) {
    int nw=Math.max(2,Math.round(w*Math.min(1,720f/Math.max(w,h))));
    int nh=Math.max(2,Math.round(h*Math.min(1,720f/Math.max(w,h))));
    if(nw==captureW && nh==captureH)return;
    hide();
    awaitingResizedFrame=true;
    final int generation=++resizeGeneration;
    lastPulse=0;
    ImageReader old=reader;
    display.setSurface(null);
    makeReader(w,h);
    display.resize(captureW,captureH,getResources().getConfiguration().densityDpi);
    display.setSurface(reader.getSurface());old.close();
    if(clean!=null){clean.recycle();clean=null;}
    acceptAfter=SystemClock.uptimeMillis()+180;
    Log.i("DuoCapture","resize physical="+w+"x"+h+" capture="+captureW+"x"+captureH);
    final long deadline=SystemClock.uptimeMillis()+1600;
    handler.postDelayed(new Runnable(){public void run(){
      if(closing || generation!=resizeGeneration)return;
      if(clean!=null) {
        awaitingResizedFrame=false;
        pulse("display-resize",700);
      } else if(SystemClock.uptimeMillis()<deadline) {
        handler.postDelayed(this,40);
      } else {
        awaitingResizedFrame=false;
        Log.i("DuoCapture","resize effect skipped: no clean frame");
      }
    }},220);
  }
  void pulse(String reason,int duration) {
    long now=SystemClock.uptimeMillis();
    if(!active || closing || overlay!=null || clean==null || now-lastPulse<900) return;
    // A static underlying app may produce no new frames: age alone is not a failure.
    lastPulse=now;
    overlay=new Glass(clean.copy(Bitmap.Config.ARGB_8888,false));
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
      PixelFormat.TRANSLUCENT);
    p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    p.gravity=Gravity.TOP|Gravity.LEFT;p.alpha=.8f;p.setTitle("Duo snapshot glass");
    try {wm.addView(overlay,p);}catch(Exception e){Log.e("DuoCapture","overlay failed",e);hide();return;}
    Log.i("DuoCapture","pulse="+reason+" frameAgeMs="+(now-cleanAt)+" duration="+duration);
    // Activity animators can pause when the activity is backgrounded. This short
    // foreground-service overlay uses an explicit elapsed-time clock and deadline.
    final long start=SystemClock.uptimeMillis();
    animationTick=new Runnable() {public void run() {
      if(overlay==null || closing)return;
      float t=Math.min(1,(SystemClock.uptimeMillis()-start)/(float)duration);
      if(t>=1){Log.i("DuoCapture","pulse ended");hide();return;}
      float strength=(float)Math.sin(Math.PI*t);
      overlay.strength=strength;
      overlay.setAlpha(Math.max(.01f,Math.min(1,strength*5)));
      overlay.setRenderEffect(strength<.01f?null:RenderEffect.createBlurEffect(1+strength*25,1+strength*25,Shader.TileMode.CLAMP));
      overlay.invalidate();
      handler.postDelayed(this,16);
    }};
    handler.post(animationTick);
  }
  void hide() {
    if(animationTick!=null){handler.removeCallbacks(animationTick);animationTick=null;}
    if(overlay!=null) {
      try{wm.removeViewImmediate(overlay);}catch(Exception ignored){}
      overlay=null;
    }
    // Drop frames that can still contain the just-removed overlay.
    acceptAfter=SystemClock.uptimeMillis()+180;
  }
  @Override public void onSensorChanged(SensorEvent e) {
    float v=e.values[0];Log.i("DuoCapture","hinge="+v);
    if(lastAngle>=0 && v!=lastAngle) handler.postDelayed(() -> {
      if(!awaitingResizedFrame)pulse("hinge="+v,700);
    },120);
    lastAngle=v;
  }
  public void onAccuracyChanged(Sensor s,int a){}
  @Override public void onDestroy() {
    if("실행 중".equals(lastStatus))lastStatus="중지됨 · 시작 버튼으로 화면 공유";
    closing=true;active=false;handler.removeCallbacksAndMessages(null);hide();
    if(sensors!=null)sensors.unregisterListener(this);
    if(display!=null)display.release();
    if(reader!=null)reader.close();
    if(projection!=null)projection.stop();
    if(clean!=null)clean.recycle();
    stopForeground(STOP_FOREGROUND_REMOVE);
    Log.i("DuoCapture","destroyed");super.onDestroy();
  }
  class Glass extends View {
    final Bitmap bitmap;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    float strength;
    Glass(Bitmap b){super(CaptureService.this);bitmap=b;}
    @Override protected void onDraw(Canvas c) {
      float w=getWidth(),h=getHeight();
      // Warp horizontal strips to make an actual displacement of captured pixels.
      for(int y=0;y<bitmap.getHeight();y+=8) {
        int end=Math.min(bitmap.getHeight(),y+8);
        float offset=(float)Math.sin(y/(float)bitmap.getHeight()*Math.PI*2)*strength*w*.012f;
        c.drawBitmap(bitmap,new Rect(0,y,bitmap.getWidth(),end),new RectF(offset-8,y*h/bitmap.getHeight(),w+offset+8,end*h/bitmap.getHeight()),paint);
      }
      paint.setShader(new LinearGradient(0,0,w,0,new int[]{0x00ffffff,Color.argb((int)(strength*55),220,240,255),0x00ffffff},null,Shader.TileMode.CLAMP));
      c.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
  }
}
