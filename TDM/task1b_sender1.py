import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(23, GPIO.OUT)
BIT_TIME = 0.1
MESSAGE  = "HELLO FROM SENDER 1"

def send_char(char):
    bits = format(ord(char), '08b')
#    print(f"Sender 1 sending '{char}' -> {bits}", flush=True)
    for bit in bits:
        if bit == '1':
            GPIO.output(23, GPIO.HIGH)
        else:
            GPIO.output(23, GPIO.LOW)
        time.sleep(BIT_TIME)

# Wait until 10-15 seconds from now, aligned to a 5-second mark
'''
START = (int(time.time() / 5) + 1) * 5
print(f"Starts at unix time {START} ({START - time.time():.1f}s from now)", flush=True)
while time.time() < START:
   time.sleep(0.01)
'''
try:
    while True:
        for char in MESSAGE:
            send_char(char)               # my turn (0.8s)
            GPIO.output(23, GPIO.LOW)
            time.sleep(BIT_TIME * 8)      # sender 2's turn - I stay quiet
except KeyboardInterrupt:
    GPIO.output(23, GPIO.LOW)
    GPIO.cleanup()



