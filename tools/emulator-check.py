#!/usr/bin/env python3
"""Boot an isolated CI emulator, verify offline UI, and retain real failure output."""
import os
import pathlib
import shutil
import subprocess
import sys
import time

root=pathlib.Path.cwd()
evidence=root/"evidence"
evidence.mkdir(exist_ok=True)
sdk=pathlib.Path(os.environ["ANDROID_HOME"])
adb=str(sdk/"platform-tools/adb")
tools=sdk/"cmdline-tools/latest/bin"
def run(*args,input=None,timeout=600,check=True):
    print("RUN", " ".join(map(str,args)),flush=True)
    result=subprocess.run(list(map(str,args)),input=input,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout)
    print(result.stdout,flush=True)
    if check and result.returncode:raise RuntimeError(f"Command exited {result.returncode}: {args[0]}\n{result.stdout[-9000:]}")
    return result

emulator=None
log=None
try:
    print("Disk free GiB",round(shutil.disk_usage(root).free/2**30,2),"KVM",pathlib.Path("/dev/kvm").exists(),flush=True)
    run(tools/"sdkmanager","platform-tools","emulator","system-images;android-35;google_apis;x86_64")
    run(tools/"avdmanager","create","avd","--force","--name","putnik-ci","--package","system-images;android-35;google_apis;x86_64","--device","pixel_6",input="no\n")
    log=(evidence/"emulator.log").open("w")
    emulator=subprocess.Popen([str(sdk/"emulator/emulator"),"-avd","putnik-ci","-no-window","-no-audio","-no-boot-anim","-no-snapshot","-gpu","swiftshader_indirect","-memory","3072","-accel","on"],stdout=log,stderr=subprocess.STDOUT)
    run(adb,"wait-for-device",timeout=150)
    deadline=time.monotonic()+300
    while time.monotonic()<deadline:
        result=run(adb,"shell","getprop","sys.boot_completed",timeout=15,check=False)
        if result.returncode==0 and result.stdout.strip()=="1":break
        if emulator.poll() is not None:raise RuntimeError("Emulator exited before boot")
        time.sleep(5)
    else:raise RuntimeError("Emulator did not finish boot within five minutes")
    for setting in ["window_animation_scale","transition_animation_scale","animator_duration_scale"]:run(adb,"shell","settings","put","global",setting,"0")
    run(adb,"shell","input","keyevent","82")
    run(adb,"install","-r",root/"app/build/outputs/apk/debug/app-debug.apk")
    run(adb,"push","/tmp/latvia.map","/data/local/tmp/latvia.map")
    run(adb,"shell","run-as","lv.putnik.survivalkit","mkdir","-p","files/packs")
    run(adb,"shell","run-as lv.putnik.survivalkit sh -c 'cat /data/local/tmp/latvia.map > files/packs/latvia.map'")
    run(adb,"shell","svc","wifi","disable")
    run(adb,"shell","svc","data","disable")
    run(sys.executable,"tools/ci-run.py","./gradlew",":app:connectedDebugAndroidTest","--stacktrace",timeout=900)
except Exception:
    if log:log.flush()
    if (evidence/"emulator.log").exists():print((evidence/"emulator.log").read_text(errors="replace")[-7000:],flush=True)
    raise
finally:
    if emulator:
        run(adb,"pull","/sdcard/Android/data/lv.putnik.survivalkit/files/screenshots",evidence/"screenshots",timeout=30,check=False)
        result=run(adb,"logcat","-d","-t","600",timeout=30,check=False)
        (evidence/"logcat.txt").write_text(result.stdout)
        run(adb,"emu","kill",timeout=20,check=False)
        emulator.terminate()
    if log:log.close()
