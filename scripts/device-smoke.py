#!/usr/bin/env python3
"""Black-box UI checks via actual Android touch coordinates and accessibility labels.
Run only on a disposable emulator: changes its display and resets app data.
"""
import argparse, pathlib, re, subprocess, time, xml.etree.ElementTree as ET
parser=argparse.ArgumentParser();parser.add_argument('--serial',default='emulator-5554');args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'artifacts'/'device-smoke';out.mkdir(parents=True,exist_ok=True)
base=['adb','-s',args.serial]
def adb(*cmd):return subprocess.check_output(base+list(cmd),stderr=subprocess.STDOUT)
def tree():
    adb('shell','uiautomator','dump','/sdcard/space-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/space-ui.xml'))
def tap(label,scroll=False):
    for attempt in range(7 if scroll else 1):
        for node in tree().iter('node'):
            if node.get('content-desc')==label:
                bounds=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                if len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]:
                    adb('shell','input','tap',str((bounds[0]+bounds[2])//2),str((bounds[1]+bounds[3])//2));time.sleep(.25);return
        if scroll:adb('shell','input','swipe','360','1050','360','350','250');time.sleep(.3)
    raise AssertionError('Visible control missing: '+label)
def has(label):
    for _ in range(4):
        if any(n.get('content-desc')==label for n in tree().iter('node')):return True
        time.sleep(.4)
    return False
def shot(name):(out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def launch():
    adb('shell','am','start','-W','-n','com.spacecheckers/.MainActivity')
    for _ in range(5):
        if any('Space Checkers.' in n.get('content-desc','') or 'Welcome to Space Checkers.' in n.get('content-desc','') for n in tree().iter('node')):
            if any(n.get('content-desc')=='Continue game' for n in tree().iter('node')):tap('Continue game')
            elif any(n.get('content-desc')=='New Game' for n in tree().iter('node')):
                tap('New Game');tap('Two players',True);tap('Start playing',True)
            return
        time.sleep(.3)
    raise AssertionError('Game screen did not become ready')
try:
    adb('shell','wm','size','720x1280');adb('shell','wm','density','320')
    adb('shell','pm','clear','com.spacecheckers');launch();shot('small-game')
    tap('Row 6, column 1, Player 1 piece');tap('Row 5, column 2, empty')
    assert has('Row 5, column 2, Player 1 piece'),'touch move did not land'
    tap('Change piece colors',True);tap('Player 1 color Pink',True);shot('small-colors')
    assert has('Player 1 color Pink, selected')
    tap('Let’s play',True)
    adb('shell','am','force-stop','com.spacecheckers');launch()
    assert has('Row 5, column 2, Player 1 piece'),'process death lost game'
    tap('Change piece colors',True);assert has('Player 1 color Pink, selected'),'process death lost colors'
    tap('Back to game');tap('Game rules and settings');shot('small-rules')
    tap('Apply rules and start new game',True);shot('small-confirm')
    adb('shell','input','keyevent','4')
    # Rotate the live rules screen, preserving its draft state.
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1');time.sleep(.8);shot('landscape-rules')
    assert any('Game rules.' in n.get('content-desc','') for n in tree().iter('node'))
    print('PASS: real taps, 360dp screen, scrolling, colors, process death, confirmation, rotation.')
finally:
    adb('shell','settings','put','system','user_rotation','0')
    adb('shell','settings','put','system','accelerometer_rotation','1')
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
