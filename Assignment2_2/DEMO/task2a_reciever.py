import serial
import time

THRESHOLD_BOTH_ON = 450
THRESHOLD_ONE_ON = 850
try:
    ser = serial.Serial(port='/dev/ttyUSB0', baudrate=115200, timeout=1)
    time.sleep(1) # Give the port time to initialize

    while True:
        line = ser.readline()

        raw_value = int(line.decode('utf-8').strip())

            # Identify State
        if raw_value < THRESHOLD_BOTH_ON:
                state = "State: BOTH LEDs ON"
        elif raw_value < THRESHOLD_ONE_ON:
                state = "State: 1 LED ON / 1 LED OFF"
        else:
                state = "State: BOTH LEDs OFF"

        print(f"ADC Value: {raw_value:4} | {state}")

except KeyboardInterrupt:
    print("\nStopping receiver...")
finally:
    if 'ser' in locals():
        ser.close()

