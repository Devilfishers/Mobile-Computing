import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(17, GPIO.IN)

BIT_TIME = 0.1

def wait_for_start_bit():
    
    while GPIO.input(17) == 1: #inverting the reciever logic
        pass 
    
    time.sleep(BIT_TIME * 1.5)

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
    print(f"Received {bits} -> '{char}'")
    return char

try:
    print("Listening for messages...")
    while True:

        wait_for_start_bit()

        receive_char()

except KeyboardInterrupt:
    GPIO.cleanup()
