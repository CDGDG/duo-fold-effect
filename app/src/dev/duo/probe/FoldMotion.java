package dev.duo.probe;

/** Sensor-set targets, not a timer-driven transition. No Android dependencies. */
final class FoldMotion {
  float position, velocity, target;
  FoldMotion(float start){position=target=clamp(start);}
  static float clamp(float value){return Math.max(0,Math.min(1,value));}
  void targetAngle(float angle){if(!Float.isNaN(angle)&&!Float.isInfinite(angle))target=clamp(angle/180f);}
  boolean step(float seconds){
    float dt=Math.max(0,Math.min(.032f,seconds))/4;
    for(int i=0;i<4;i++){velocity+=(200*(target-position)-28.28427f*velocity)*dt;position+=velocity*dt;}
    if(Math.abs(target-position)<.0005f && Math.abs(velocity)<.005f){position=target;velocity=0;return false;}
    return true;
  }
  boolean endpoint(){return target==0 || target==1;}
  float material(){return (float)Math.sin(Math.PI*clamp(position));}
}
