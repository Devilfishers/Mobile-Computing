import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(24, GPIO.OUT)
BIT_TIME = 0.1
MESSAGE  = "hello from sender 2"

def send_char(char):
    bits = format(ord(char), '08b')
#    print(f"Sender 2 sending '{char}' -> {bits}", flush=True)
    for bit in bits:
        if bit == '1':
            GPIO.output(24, GPIO.HIGH)
        else:
            GPIO.output(24, GPIO.LOW)
        time.sleep(BIT_TIME)

# Wait until 10-15 seconds from now, aligned to a 5-second mark
'''
START = (int(time.time() / 5) + 1) * 5
print(f"Starts at unix time {START} ({START - time.time():.1f}s from now)", flush=True)
while time.time() < START:
    time.sleep(0.01)
print("start", flush=True)
'''
try:
    while True:
        for char in MESSAGE:
            time.sleep(BIT_TIME * 8)      # sender 1's turn - I stay quiet
            send_char(char)               # my turn (0.8s)
            GPIO.output(24, GPIO.LOW)
except KeyboardInterrupt:
    GPIO.output(24, GPIO.LOW)
    GPIO.cleanup()



