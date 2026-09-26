#!/usr/bin/env python3
import time
import serial

ser = serial.Serial("/dev/ttyUSB0", 115200)

def read_adc(s):
    while True:
        try:
            return int(s.readline().rstrip())
        except:
            continue

#find chip timing through a voltage transition
prev = read_adc(ser)
while True:
    v = read_adc(ser)
    if abs(v - prev) > 100:
        chip_time = time.monotonic()
        samples = [v]
        break
    prev = v

def bits_to_str(bits, boff):
    out = []
    for i in range(bit_off, len(bits) - 7, 8):
        byte = 0
        for b in bits[i:i+8]:
            byte = (byte << 1) | b
        if 32 <= byte < 127:
            out.append(chr(byte))
        else:
            out.append(".")
    return "".join(out)

chips = []
MSG1 = "HELLO FROM SENDER 1"
MSG2 = "hello from sender 2"
CHECK_INTERVAL = 40  # only check every 40 chips (~0.8s) to avoid falling behind on ADC reads

try:
    while True:
        v = read_adc(ser)
        now = time.monotonic()
        if now - chip_time < 0.02:
            samples.append(v)
            continue

        avg = sum(samples) / len(samples)
        c = 2 if avg <= 400 else (-2 if avg >= 800 else 0)
        chips.append(c)
        samples = [v]
        chip_time += 0.02

        # Only check periodically to avoid falling behind
        if len(chips) >= 500 and len(chips) % CHECK_INTERVAL == 0:
            for chip_off in (0, 1):
                b1, b2 = [], []
                for i in range(chip_off, len(chips) - 1, 2):
                    s0, s1 = chips[i], chips[i+1]
                    b1.append(1 if (s0 + s1) > 0 else 0)
                    b2.append(1 if (s0 - s1) > 0 else 0)

                for bit_off in range(8):
                    t1 = bits_to_str(b1, bit_off)
                    t2 = bits_to_str(b2, bit_off)
                    if MSG1 in t1 and MSG2 in t2:
                        i1, i2 = t1.index(MSG1), t2.index(MSG2)
                        print(t1[i1:i1+len(MSG1)])
                        print(t2[i2:i2+len(MSG2)])
                        chips = []
                        break
                    if MSG1 in t2 and MSG2 in t1:
                        i1, i2 = t2.index(MSG1), t1.index(MSG2)
                        print(t2[i1:i1+len(MSG1)])
                        print(t1[i2:i2+len(MSG2)])
                        chips = []
                        break
                if len(chips) == 0:
                    break

except KeyboardInterrupt:
    pass
finally:
    ser.close()
