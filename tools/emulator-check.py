#!/usr/bin/env python3
"""Boot an isolated CI emulator, verify offline UI, and retain real failure output."""
import os
import pathlib
import shutil
import subprocess
import sys
import time
import json
import xml.etree.ElementTree as ET

root=pathlib.Path.cwd()
evidence=root/"evidence"
evidence.mkdir(exist_ok=True)
sdk=pathlib.Path(os.environ["ANDROID_HOME"])
android_user=root/"ci-android-home"
avd_home=android_user/"avd"
avd_home.mkdir(parents=True,exist_ok=True)
os.environ["ANDROID_USER_HOME"]=str(android_user)
os.environ["ANDROID_EMULATOR_HOME"]=str(android_user)
os.environ["ANDROID_AVD_HOME"]=str(avd_home)
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
screens_collected=False
try:
    print("Disk free GiB",round(shutil.disk_usage(root).free/2**30,2),"KVM",pathlib.Path("/dev/kvm").exists(),flush=True)
    run(tools/"sdkmanager","platform-tools","emulator","system-images;android-35;google_apis;x86_64")
    avd_path=avd_home/"putnik-ci.avd"
    run(tools/"avdmanager","create","avd","--force","--name","putnik-ci","--path",avd_path,"--package","system-images;android-35;google_apis;x86_64","--device","pixel_6",input="no\n")
    if not (avd_path/"config.ini").is_file():raise RuntimeError("avdmanager did not create the requested AVD config")
    (avd_home/"putnik-ci.ini").write_text(f"avd.ini.encoding=UTF-8\npath={avd_path}\ntarget=android-35\n")
    found=run(sdk/"emulator/emulator","-list-avds",timeout=30)
    if "putnik-ci" not in found.stdout:raise RuntimeError("Created AVD is not visible to emulator")
    log=(evidence/"emulator.log").open("w")
    emulator=subprocess.Popen([str(sdk/"emulator/emulator"),"-avd","putnik-ci","-no-window","-no-audio","-no-boot-anim","-no-snapshot","-gpu","swiftshader","-memory","3072","-accel","on"],stdout=log,stderr=subprocess.STDOUT)
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
    run(adb,"shell","run-as","lv.putnik.survivalkit","mkdir","-p","files/packs")
    # Stream through stdin: application SELinux domains cannot read shell_data_file.
    with pathlib.Path("/tmp/latvia.map").open("rb") as source:
        result=subprocess.run([adb,"shell","-T","run-as lv.putnik.survivalkit sh -c 'cat > files/packs/latvia.map'"],stdin=source,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=90)
        print(result.stdout,flush=True)
        if result.returncode:raise RuntimeError("Could not install private map fixture: "+result.stdout)
    run(adb,"shell","svc","wifi","disable")
    run(adb,"shell","svc","data","disable")
    run(sys.executable,"tools/ci-run.py","./gradlew",":app:connectedDebugAndroidTest","--stacktrace",timeout=900)
    def suites(directory):
        results=[]
        for path in pathlib.Path(directory).rglob("TEST-*.xml"):
            suite=ET.parse(path).getroot()
            if suite.tag!="testsuite":continue
            results.append({"name":suite.get("name"),"tests":int(suite.get("tests","0")),"failures":int(suite.get("failures","0")),"errors":int(suite.get("errors","0")),"skipped":int(suite.get("skipped","0")),"cases":[c.get("name")for c in suite.findall("testcase")]})
        return results
    unit=suites("core/build/test-results/test")
    android=suites("app/build/outputs/androidTest-results")
    if sum(s["tests"]for s in unit)!=7 or sum(s["tests"]for s in android)!=5:raise RuntimeError("Expected seven core and five Android cases, not an empty successful invocation")
    if any(s["failures"]+s["errors"]+s["skipped"] for s in unit+android):raise RuntimeError("A required test failed or was skipped")
    run(adb,"pull","/sdcard/Pictures/PutnikCI",evidence/"screenshots",timeout=30)
    pictures=list((evidence/"screenshots").glob("*.png"))
    if len(pictures)!=6:raise RuntimeError(f"Expected six real Android screenshots; found {len(pictures)}")
    screens_collected=True
    (evidence/"verification.json").write_text(json.dumps({"commit":os.environ.get("GITHUB_SHA"),"unit":unit,"android_emulator":android,"screenshots":[p.name for p in pictures],"network":"emulator Wi-Fi and mobile data disabled","device_quality_verified":False},ensure_ascii=False,indent=2))
except Exception:
    if log:log.flush()
    if (evidence/"emulator.log").exists():print((evidence/"emulator.log").read_text(errors="replace")[-7000:],flush=True)
    raise
finally:
    if emulator:
        try:
            if not screens_collected:run(adb,"pull","/sdcard/Pictures/PutnikCI",evidence/"screenshots",timeout=30,check=False)
            result=run(adb,"logcat","-d","-t","600",timeout=30,check=False)
            (evidence/"logcat.txt").write_text(result.stdout)
        except (subprocess.SubprocessError,OSError) as error:print("Evidence collection:",error,flush=True)
        try:run(adb,"emu","kill",timeout=20,check=False)
        except (subprocess.SubprocessError,OSError):pass
        emulator.terminate()
    if log:log.close()
