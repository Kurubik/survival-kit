#!/usr/bin/env python3
"""Exercise the real RU Vosk model with labelled synthetic speech, never user audio.

Requires Python vosk, espeak-ng and ffmpeg. This is not an Android microphone,
human-speech, accent, wind or field-quality evaluation.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
import wave
import zipfile

parser=argparse.ArgumentParser()
parser.add_argument("archive",type=pathlib.Path)
parser.add_argument("output",type=pathlib.Path)
args=parser.parse_args()
assert hashlib.sha256(args.archive.read_bytes()).hexdigest()=="961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"
args.output.mkdir(parents=True,exist_ok=True)
root=args.output.resolve()
with zipfile.ZipFile(args.archive) as z:
    for item in z.infolist():
        if not (root/item.filename).resolve().is_relative_to(root):raise ValueError("Unsafe model archive")
    z.extractall(root)
from vosk import Model,KaldiRecognizer,SetLogLevel
SetLogLevel(-1)
model=Model(str(root/"vosk-model-small-ru-0.22"))
cases=[("Как разжечь огонь после дождя?","огонь"),("У меня нет зажигалки","нет"),("Покажи памятку про воду","воду"),("Дальше","дальше")]
results=[]
for index,(text,keyword) in enumerate(cases):
    source=root/f"synthetic-{index}.wav";audio=root/f"synthetic-{index}-16k.wav"
    subprocess.run(["espeak-ng","-v","ru","-s","140","-w",str(source),text],check=True)
    subprocess.run(["ffmpeg","-loglevel","error","-y","-i",str(source),"-ar","16000","-ac","1",str(audio)],check=True)
    recognizer=KaldiRecognizer(model,16000)
    pieces=[]
    with wave.open(str(audio)) as data:
        while chunk:=data.readframes(4000):
            if recognizer.AcceptWaveform(chunk):pieces.append(json.loads(recognizer.Result()).get("text",""))
        pieces.append(json.loads(recognizer.FinalResult()).get("text",""))
    actual=" ".join(pieces).strip()
    results.append({"synthetic_input":text,"recognized":actual,"expected_keyword":keyword,"keyword_present":keyword in actual})
report={"kind":"synthetic speech smoke check, Linux arm64; not device quality", "model_sha256":"961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4","cases":results}
(root/"result.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
print(json.dumps(report,ensure_ascii=False,indent=2))
raise SystemExit(0 if all(x["keyword_present"] for x in results) else 1)
