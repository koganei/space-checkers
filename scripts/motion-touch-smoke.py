#!/usr/bin/env python3
"""Real touch smoke test; clears app data on a disposable QA emulator only."""
import argparse, re, subprocess, time, xml.etree.ElementTree as ET
p = argparse.ArgumentParser()
p.add_argument('--serial', default='emulator-5558')
a = p.parse_args()
def adb(*args):
    return subprocess.check_output(['adb', '-s', a.serial, *args])
def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/motion-ui.xml')
    return list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/motion-ui.xml')).iter('node'))
def center(label):
    n = next(n for n in nodes() if n.get('content-desc') == label)
    b = list(map(int, re.findall(r'\d+', n.get('bounds'))))
    return ((b[0]+b[2])//2, (b[1]+b[3])//2)
def tap(label):
    x, y = center(label)
    adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(.35)
def swipe(start, end):
    adb('shell', 'input', 'swipe', *map(str, (*start, *end)), '650')
    time.sleep(.35)
def has(label):
    return any(n.get('content-desc') == label for n in nodes())
adb('shell', 'pm', 'clear', 'com.spacecheckers')
adb('shell', 'am', 'start', '-W', '-n', 'com.spacecheckers/.MainActivity')
tap('New Game'); tap('Two players'); tap('Choose Player 1')
source = 'Row 6, column 1, Player 1 piece'
target = 'Row 5, column 2, empty'
start, end = center(source), center(target)
swipe(start, (start[0], 300))
assert has(source) and has(target), 'Off-board drag changed the position'
swipe(start, end)
assert has('Row 5, column 2, Player 1 piece') and has('Row 6, column 1, empty'), 'Drag failed through actual scroll/touch hierarchy'
tap('Undo last turn')
assert has(source), 'Undo did not restore dragged piece'
tap(source); tap(target)
assert has('Row 5, column 2, Player 1 piece'), 'Tap move failed'
print('PASS: actual touch dispatch, off-board drop, legal drag, Undo, and tap move.')
