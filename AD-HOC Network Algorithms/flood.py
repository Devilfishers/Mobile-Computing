import socket
import json
import uuid
import threading
import time


PORT = 5001 
BROADCAST_IP = '192.168.210.255'
HOSTNAME = socket.gethostname()

seen_messages = set()

def log_event(event_type, msg_id, details):

    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] [{HOSTNAME}] [{event_type}] MsgID: {msg_id} - {details}")

def receiver_loop():

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(('', PORT))
    
    print(f"[{HOSTNAME}] Listening for Flooding broadcasts on port {PORT}...")
    
    while True:
        try:
            data, addr = sock.recvfrom(4096)
            message = json.loads(data.decode('utf-8'))
            
            msg_id = message.get('msg_id')
            source = message.get('source')
            dest = message.get('dest')
            payload = message.get('payload')
            
            if source == HOSTNAME:
                continue

            if msg_id in seen_messages:
                log_event("DROPPED", msg_id, f"Already seen. Ignored packet from {addr[0]}")
                continue
            
            seen_messages.add(msg_id)
            log_event("RECEIVED", msg_id, f"Packet from {addr[0]} (Source: {source}, Target: {dest})")
            
            if dest == HOSTNAME or dest == HOSTNAME.split('.')[0]:
                log_event("DELIVERED", msg_id, f"I am the destination! Payload: '{payload}'")
                continue

            log_event("FORWARDING", msg_id, "Not destination. Broadcasting to neighbors...")
            forward_packet(message)
            
        except json.JSONDecodeError:
            pass
        except Exception as e:
            print(f"Error in receiver loop: {e}")

def forward_packet(message_dict):

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    data = json.dumps(message_dict).encode('utf-8')
    sock.sendto(data, (BROADCAST_IP, PORT))
    sock.close()

def originate_message(dest_hostname, payload):

    msg_id = str(uuid.uuid4())[:8] # Short unique identifier
    message = {
        'msg_id': msg_id,
        'source': HOSTNAME,
        'dest': dest_hostname,
        'payload': payload
    }
    
    seen_messages.add(msg_id)
    
    log_event("ORIGINATING", msg_id, f"Sending '{payload}' to {dest_hostname}")
    forward_packet(message)

if __name__ == "__main__":

    listener_thread = threading.Thread(target=receiver_loop, daemon=True)
    listener_thread.start()
    
    time.sleep(1) # Give the socket a moment to bind
    
    print("\n--- Flooding Protocol Node ---")
    print("Commands:")
    print("  send <destination_hostname> <message>")
    print("  exit")
    print("------------------------------\n")
    
    while True:
        try:
            user_input = input(">> ").strip()
            if not user_input:
                continue
            
            if user_input.lower() == 'exit':
                break
            
            user_input= user_input + " HELLO WORLD" 

            parts = user_input.split(" ", 2)
        
            if parts[0].lower() == 'send' and len(parts) >= 3:
                dest = parts[1]
                msg_payload = parts[2]
                originate_message(dest, msg_payload)
            else:
                print("Invalid command. Format: send <destination_hostname> Hello World")
        except KeyboardInterrupt:
            break
