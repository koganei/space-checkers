#!/usr/bin/env python3
"""Disposable emulator only: verify alien setup and automatic replies with real touches."""
import argparse, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5558');args=parser.parse_args()
base=['adb','-s',args.serial];out=pathlib.Path(__file__).resolve().parents[1]/'artifacts'/'alien-touch';out.mkdir(parents=True,exist_ok=True)
def adb(*args):return subprocess.check_output(base+list(args),stderr=subprocess.STDOUT)
def tree():
    adb('shell','uiautomator','dump','/sdcard/alien-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/alien-ui.xml'))
def labels():return [n.get('content-desc','') for n in tree().iter('node')]
def tap(label):
    for _ in range(8):
        for n in tree().iter('node'):
            if n.get('content-desc')==label:
                b=list(map(int,re.findall(r'\d+',n.get('bounds',''))))
                if len(b)==4 and b[3]-b[1]>=28:
                    adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2));time.sleep(.25);return
        adb('shell','input','swipe','360','1080','360','350','250')
    raise AssertionError('Cannot tap '+label)
def shot(name):(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def wait_turn():
    for _ in range(12):
        if any(x.startswith('Space Checkers. Your turn!') for x in labels()):return
        time.sleep(.25)
    raise AssertionError('Computer did not return the turn')
try:
    adb('shell','wm','size','720x1280');adb('shell','wm','density','320')
    adb('shell','pm','clear','com.spacecheckers');adb('shell','am','start','-W','-n','com.spacecheckers/.MainActivity')
    tap('New Game');tap('Play an alien')
    for name,level in [('Bloop','Medium'),('Ziggy','Tricky'),('Luma','Hard')]:
        tap('Choose '+name+', '+level);assert 'Choose Player 2' in labels()
        if name != 'Luma':tap('Back to welcome');tap('New Game');tap('Play an alien')
    shot('small-player-choice');tap('Choose Player 2');wait_turn()
    assert 'Row 6, column 1, Player 2 piece' in labels()
    assert any(x.startswith('Row 4,') and 'Player 1 piece' in x for x in labels()),'Opening AI move missing'
    shot('small-alien-game');tap('Row 6, column 7, Player 2 piece');tap('Row 5, column 8, empty');wait_turn()
    assert 'Row 5, column 8, Player 2 piece' in labels()
    tap('Undo last turn')
    adb('shell','input','swipe','360','350','360','1050','250')
    assert 'Row 6, column 7, Player 2 piece' in labels(),'Paired undo did not restore player piece'
    print('PASS: 360dp alien picker, all four choices, Player 2 orientation, computer opening/reply, paired Undo.')
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
