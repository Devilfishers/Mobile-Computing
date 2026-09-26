import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(17, GPIO.IN)
BIT_TIME = 0.1

def receive_char():
    bits = ""
    for i in range(8):
        raw = GPIO.input(17)
        if raw == 0:
            bits += '1'
        else:
            bits += '0'
        time.sleep(BIT_TIME)
    char = chr(int(bits, 2))
    print(f"Received {bits} -> '{char}'", flush=True)
    return char

# Wait until 10-15 seconds from now, aligned to a 5-second mark
'''
START = (int(time.time() / 5) + 1) * 5
print(f"Starts at unix time {START} ({START - time.time():.1f}s from now)", flush=True)
while time.time() < START:
    time.sleep(0.01)
print("start", flush=True)
'''
# Sample in the middle of each bit
time.sleep(BIT_TIME / 2)

try:
    while True:
        receive_char()
except KeyboardInterrupt:
    GPIO.cleanup()




