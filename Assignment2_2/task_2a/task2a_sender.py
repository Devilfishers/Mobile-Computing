import RPi.GPIO as GPIO
import time

pin1 = 23
pin2 = 24
GPIO.setmode(GPIO.BCM)
GPIO.setup(pin1, GPIO.OUT)
GPIO.setup(pin2, GPIO.OUT)

def switchLED():
    while (True):
        GPIO.output(pin1, GPIO.LOW)
        GPIO.output(pin2, GPIO.LOW)
        time.sleep(1.5)
        GPIO.output(pin1, GPIO.HIGH)
        GPIO.output(pin2, GPIO.LOW)
        time.sleep(1)
        GPIO.output(pin1, GPIO.LOW)
        GPIO.output(pin2, GPIO.HIGH)
        time.sleep(1)
        GPIO.output(pin1, GPIO.HIGH)
        GPIO.output(pin2, GPIO.HIGH)
        time.sleep(1)

try:
    switchLED()

except KeyboardInterrupt:
    GPIO.cleanup()

