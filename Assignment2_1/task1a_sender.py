import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(23, GPIO.OUT)

BIT_TIME = 0.1
MESSAGE  = "Hello World! "

def send_char(char):

    GPIO.output(23, GPIO.HIGH)
    time.sleep(BIT_TIME)

    # 2. Send Data Bits
    bits = format(ord(char), '08b')
    print(f"Sending '{char}' -> {bits}")
    for bit in bits:
        if bit == '1':
            GPIO.output(23, GPIO.HIGH)
        else:
            GPIO.output(23, GPIO.LOW)
        time.sleep(BIT_TIME)

    # 3. Send STOP Bit / Return to Idle (LOW / Light OFF)
    GPIO.output(23, GPIO.LOW)
    time.sleep(BIT_TIME)

try:
    while True:
        for char in MESSAGE:
            send_char(char)

        print("Message complete. Waiting 5 seconds...")
        # Just sleep for 5 seconds between messages
        time.sleep(5.0) 

except KeyboardInterrupt:
    GPIO.output(23, GPIO.LOW)
    GPIO.cleanup()
