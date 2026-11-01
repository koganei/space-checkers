#!/usr/bin/env python3
"""Original soft space chimes. Deterministic PCM, no external assets or libraries."""
import math, pathlib, struct, wave
OUT = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
RATE = 22050
SCORES = {
    'move': [(0, .14, 523.25), (.07, .15, 783.99)],
    'capture': [(0, .12, 783.99), (.08, .16, 1046.5), (.17, .16, 1318.51)],
    'king': [(0, .19, 523.25), (.11, .19, 659.25), (.22, .19, 783.99), (.33, .30, 1046.5)],
    'win': [(0, .22, 523.25), (.16, .22, 659.25), (.32, .22, 783.99), (.5, .42, 1046.5), (.5, .42, 659.25), (.5, .42, 783.99)],
}
for name, notes in SCORES.items():
    samples = [0.] * int((max(t+d for t,d,f in notes)+.02)*RATE)
    for start, duration, frequency in notes:
        for i in range(int(duration*RATE)):
            t=i/RATE
            envelope=min(1,t/.012)*max(0,1-t/duration)**1.7
            tone=math.sin(2*math.pi*frequency*t)+.15*math.sin(2*math.pi*frequency*2*t)
            samples[int(start*RATE)+i] += .16*envelope*tone
    assert max(map(abs,samples)) < .8
    with wave.open(str(OUT/('sfx_'+name+'.wav')), 'wb') as f:
        f.setparams((1,2,RATE,0,'NONE','not compressed'))
        f.writeframes(b''.join(struct.pack('<h',round(x*32767)) for x in samples))
