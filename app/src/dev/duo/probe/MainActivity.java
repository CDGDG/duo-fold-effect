package dev.duo.probe;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.util.Log;
import java.util.Locale;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.provider.Settings;
import android.net.Uri;

/** Public-API feasibility probe with an opt-in capture overlay test. */
public class MainActivity extends Activity implements SensorEventListener {
  SensorManager sensors;
  Sensor hinge;
  TextView status;
  Demo demo;
  long previous, count;
  float angle = 180, interval;
  boolean manual;
  final android.os.Handler statusHandler = new android.os.Handler();
  String shownCaptureState="";
  final Runnable refreshStatus = new Runnable() {public void run(){
    String state=(CaptureService.active?"실행 중":CaptureService.lastStatus)+ScreenshotAccessibilityService.connected;
    if(!state.equals(shownCaptureState)){shownCaptureState=state;update();}
    statusHandler.postDelayed(this,500);
  }};
  final StringBuilder samples = new StringBuilder("timestamp_ns,angle,interval_ms\n");

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    sensors = getSystemService(SensorManager.class);
    SensorDiagnostics.run(this);
    hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(24, 60, 24, 40);
    layout.setOnApplyWindowInsetsListener((v, insets) -> {
      Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
      v.setPadding(24 + bars.left, 16 + bars.top, 24 + bars.right, 16 + bars.bottom);
      return insets;
    });
    layout.setBackgroundColor(Color.rgb(12, 19, 30));
    status = new TextView(this);
    status.setTextColor(Color.WHITE);
    status.setTextSize(17);
    layout.addView(status);
    Button capture = new Button(this);
    capture.setText("다른 앱 위 캡처 효과 시작");
    capture.setOnClickListener(v -> beginCapture());
    layout.addView(capture);
    Button pulse = new Button(this);
    pulse.setText("펼침 단계 시험 · 90에서 6초 유지 → 180");
    pulse.setOnClickListener(v -> {
      if(ScreenshotAccessibilityService.preview(true)){startActivity(new Intent(Settings.ACTION_SETTINGS));return;}
      if (!CaptureService.active) { Toast.makeText(this,"먼저 접근성 실험을 켜세요",0).show(); return; }
      startService(new Intent(this,CaptureService.class).setAction("TEST"));
      startActivity(new Intent(Settings.ACTION_SETTINGS));
    });
    layout.addView(pulse);
    Button closePreview=new Button(this);closePreview.setText("접힘 단계 시험 · 90에서 6초 유지 → 0");
    closePreview.setOnClickListener(v->{if(ScreenshotAccessibilityService.preview(false))startActivity(new Intent(Settings.ACTION_SETTINGS));else Toast.makeText(this,"먼저 접근성 실험을 켜세요",0).show();});layout.addView(closePreview);
    Button stop = new Button(this);
    stop.setText("캡처 및 효과 중지");
    stop.setOnClickListener(v -> {stopService(new Intent(this,CaptureService.class));if(ScreenshotAccessibilityService.instance!=null)ScreenshotAccessibilityService.instance.disableSelf();});
    layout.addView(stop);
    Button accessibility=new Button(this);
    accessibility.setText("접근성 캡처 실험 설정");
    accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    layout.addView(accessibility);
    Button mode = new Button(this);
    mode.setText("실제 힌지 센서 모드 · 탭하면 수동 테스트");
    mode.setOnClickListener(v -> {
      manual = !manual;
      mode.setText(manual ? "수동 테스트 모드 · 탭하면 실제 센서" : "실제 힌지 센서 모드 · 탭하면 수동 테스트");
      update();
    });
    layout.addView(mode);
    SeekBar seek = new SeekBar(this);
    seek.setMax(180); seek.setProgress(180);
    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onProgressChanged(SeekBar s, int p, boolean user) { if(user && manual) {angle=p; update();} }
      public void onStartTrackingTouch(SeekBar s) {}
      public void onStopTrackingTouch(SeekBar s) {}
    });
    layout.addView(seek);
    demo = new Demo();
    layout.addView(demo, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView note = new TextView(this);
    note.setText("기기를 천천히 접고 펴세요.\n아래 그림만 흐려지는 자체 화면 테스트입니다.\n수동 모드는 센서 검증 결과에 포함하지 않습니다.");
    note.setTextColor(Color.LTGRAY);
    layout.addView(note);
    setContentView(layout);
    Log.i("DuoProbe", "blurEnabled=" + getWindowManager().isCrossWindowBlurEnabled() + " hinge=" + hinge);
    update();
  }
  void beginCapture() {
    if(ScreenshotAccessibilityService.connected){Toast.makeText(this,"접근성 실험을 먼저 꺼주세요",Toast.LENGTH_LONG).show();return;}
    if (!Settings.canDrawOverlays(this)) {
      startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));
      Toast.makeText(this,"다른 앱 위 표시를 허용한 뒤 돌아와 시작을 눌러주세요",Toast.LENGTH_LONG).show();
      return;
    }
    if (CaptureService.active) { Toast.makeText(this,"캡처 실행 중입니다",0).show(); return; }
    MediaProjectionManager m = getSystemService(MediaProjectionManager.class);
    startActivityForResult(android.os.Build.VERSION.SDK_INT >= 34 ? m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : m.createScreenCaptureIntent(), 41);
  }
  @Override protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request,result,data);
    if (request==41 && result==RESULT_OK && data!=null) {
      startForegroundService(new Intent(this,CaptureService.class).putExtra("result",result).putExtra("token",data));
      Toast.makeText(this,"캡처 시작. 테스트 버튼을 누르거나 다른 앱에서 접고 펴세요",Toast.LENGTH_LONG).show();
    }
  }
  @Override public void onResume() {
    super.onResume();
    ScreenshotAccessibilityService.controls(true);
    statusHandler.post(refreshStatus);
    if (hinge != null) Log.i("DuoProbe", "registered=" + sensors.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME));
  }
  @Override public void onPause() {
    super.onPause(); sensors.unregisterListener(this);
    ScreenshotAccessibilityService.controls(false);
    statusHandler.removeCallbacks(refreshStatus);
    try(java.io.FileOutputStream f = openFileOutput("hinge.csv", MODE_PRIVATE)) {
      f.write(samples.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch(Exception e) { Log.e("DuoProbe", "save", e); }
  }
  @Override public void onSensorChanged(SensorEvent e) {
    interval = previous == 0 ? 0 : (e.timestamp - previous)/1000000f;
    previous=e.timestamp; count++;
    if (samples.length() < 1000000) samples.append(e.timestamp).append(',').append(e.values[0]).append(',').append(interval).append('\n');
    Log.i("DuoProbe", "sensor angle="+e.values[0]+" intervalMs="+interval+" count="+count);
    if (!manual) { angle=e.values[0]; update(); }
  }
  @Override public void onAccuracyChanged(Sensor s, int accuracy) {}
  void update() {
    status.setText(String.format(Locale.US,"DUO / 실기기 진단\n힌지: %s\n각도 %.1f° · 이벤트 %d · 간격 %.1f ms\n다른 창 블러: %s · %s",hinge==null?"없음":hinge.getName(), angle,count,interval,getWindowManager().isCrossWindowBlurEnabled(),manual?"수동":"센서"));
    status.append("\n캡처: "+(CaptureService.active?"실행 중":CaptureService.lastStatus));
    status.append(" · 접근성 "+(ScreenshotAccessibilityService.connected?"연결됨":"꺼짐"));
    float strength = Math.max(0, Math.min(1, (175-angle)/95));
    demo.setRenderEffect(strength < .005 ? null : RenderEffect.createBlurEffect(1+strength*32,1+strength*32,Shader.TileMode.CLAMP));
    demo.setScaleX(1-strength*.035f);
    demo.setAlpha(1-strength*.15f);
  }
  class Demo extends View {
    Paint p = new Paint(3);
    Demo() {super(MainActivity.this);}
    @Override protected void onDraw(Canvas c) {
      float w=getWidth(), h=getHeight();
      float unit=Math.min(w,h*1.5f);
      p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff153854,0xff5664a4,0xffd99481},null,Shader.TileMode.CLAMP));
      c.drawRoundRect(0,0,w,h,35,35,p); p.setShader(null);
      p.setColor(Color.WHITE);p.setTextSize(Math.min(unit*.075f,h*.11f));c.drawText("Fold playground",w*.07f,h*.16f,p);
      p.setTextSize(Math.min(unit*.032f,h*.045f));c.drawText("LIVE HINGE / LOCAL CONTENT",w*.07f,h*.23f,p);
      for(int i=0;i<6;i++) {
        float x=w*.07f+(i%3)*w*.30f, y=h*.34f+(i/3)*h*.24f;
        p.setColor(new int[]{0xffffb578,0xff9de4d0,0xffc4c1ff,0xffe8b4d3,0xff91c8ee,0xfff0de9c}[i]);
        c.drawRoundRect(x,y,x+w*.24f,y+h*.16f,24,24,p);
        p.setColor(0xff24334b);p.setTextSize(Math.min(unit*.06f,h*.09f));c.drawText("0"+(i+1),x+w*.05f,y+h*.10f,p);
      }
    }
  }
}
