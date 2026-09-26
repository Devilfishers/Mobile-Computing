import subprocess
import sys
import time
import os

def run_all():
    # 1. Start senders in the background
    # They will print to this same terminal
    s1 = subprocess.Popen([sys.executable, 'sender1.py'])
    s2 = subprocess.Popen([sys.executable, 'sender2.py'])
    
    print("Senders started in background")

    try:
     
   
        print("Starting Receiver")
        subprocess.run([sys.executable, 'receiverb.py'])

    except KeyboardInterrupt:
        print("\nStopping everything...")
    finally:
        s1.terminate()
        s2.terminate()
  
        print("Cleaned up processes.")

if __name__ == "__main__":
    run_all()

