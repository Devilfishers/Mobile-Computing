#!/usr/bin/env python3
import time

import RPi.GPIO as GPIO


PIN1 = 23
PIN2 = 24

TEXT1 = b"HELLO FROM SENDER 1"
TEXT2 = b"hello from sender 2"
PREAMBLE = b"\x55\x33\x0f\xf0\x7e" * 3
FRAME1 = PREAMBLE + TEXT1 + b"\n"
FRAME2 = PREAMBLE + TEXT2 + b"\n"

CI = 0.02
CODE1 = (1, 1)
CODE2 = (1, -1)


def bit_at(frame, bit_index):
    byte = frame[(bit_index // 8) % len(frame)]
    shift = 7 - (bit_index % 8)
    return (byte >> shift) & 1


def chip_for(code, bit, chip_index):
    chip = code[chip_index % len(code)]
    return chip if bit else -chip


GPIO.setmode(GPIO.BCM)
GPIO.setup(PIN1, GPIO.OUT)
GPIO.setup(PIN2, GPIO.OUT)

try:
    bit_index = 0
    chip_index = 0
    next_chip_time = time.monotonic()

    while True:
        bit1 = bit_at(FRAME1, bit_index)
        bit2 = bit_at(FRAME2, bit_index)

        chip1 = chip_for(CODE1, bit1, chip_index)
        chip2 = chip_for(CODE2, bit2, chip_index)

        GPIO.output(PIN1, GPIO.HIGH if chip1 == 1 else GPIO.LOW)
        GPIO.output(PIN2, GPIO.HIGH if chip2 == 1 else GPIO.LOW)

        chip_index += 1
        if chip_index == 2:
            chip_index = 0
            bit_index += 1

        next_chip_time += CI
        sleep_time = next_chip_time - time.monotonic()
        if sleep_time > 0:
            time.sleep(sleep_time)
        else:
            next_chip_time = time.monotonic()

except KeyboardInterrupt:
    GPIO.cleanup()
