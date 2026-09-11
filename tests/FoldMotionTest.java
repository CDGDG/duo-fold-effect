package dev.duo.probe;
public class FoldMotionTest {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 static void settle(FoldMotion m){for(int i=0;i<600;i++)m.step(1f/60);}
 public static void main(String[] args){
  FoldMotion m=new FoldMotion(0);m.targetAngle(90);settle(m);
  check(m.position==.5f&&!m.endpoint(),"90 must remain at middle after 10 seconds");
  check(m.material()>.99f,"middle must remain visible even when spring is settled");
  for(int i=0;i<100;i++){m.targetAngle(90);m.step(1f/60);}
  check(m.position==.5f,"repeated 90 must not replay");
  m.targetAngle(180);settle(m);check(m.position==1&&m.endpoint()&&m.material()<.00001f,"180 must reveal normal app");
  m.targetAngle(90);for(int i=0;i<6;i++)m.step(1f/60);
  float position=m.position,velocity=m.velocity;m.targetAngle(180);
  check(m.position==position&&m.velocity==velocity,"reversal must preserve presentation and velocity");
  settle(m);check(m.position==1,"reversal must settle at new target");
  m.targetAngle(90);settle(m);check(m.position==.5f,"closing must also hold at 90");
  m.targetAngle(0);settle(m);check(m.position==0&&m.material()==0,"closed endpoint must clear overlay");
  m.targetAngle(Float.NaN);check(m.target==0,"invalid sensor must not poison animation");
  System.out.println("PASS: middle hold, duplicate signal, both endpoints, in-flight reversal, invalid input");
 }
}
