#!/usr/bin/env python3
"""Tap by current window node bounds without suppressing accessibility services."""
import os,subprocess,sys,time
time.sleep(.5)
adb=[os.environ.get('ADB', 'adb')]
if os.environ.get('ANDROID_SERIAL'):
 adb += ['-s', os.environ['ANDROID_SERIAL']]
s=subprocess.check_output(adb+['shell','CLASSPATH=/data/local/tmp/duo-ui.dex','app_process','/system/bin','UiWindowProbe'],text=True)
nodes=[]
for line in s.splitlines():
 if line.startswith('NODE\t'):
  _,text,bounds=line.split('\t');nodes.append((text,bounds))
label=sys.argv[1] if len(sys.argv)>1 else None
for text,bounds in nodes:
 if label is None or text==label:print(text,bounds)
if '--tap' in sys.argv:
 text,bounds=next(n for n in nodes if n[0]==label)
 a,b,c,d=map(int,bounds.split(','));subprocess.run(adb+['shell','input','tap',str((a+c)//2),str((b+d)//2)],check=True)
