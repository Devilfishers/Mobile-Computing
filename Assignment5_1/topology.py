import socket
import json
import uuid
import threading
import time

PORT = 5001
BROADCAST_IP = '192.168.210.255'
HOSTNAME = socket.gethostname()

# Topology: { node_hostname: { neighbor_hostname: latency_ms } }
topology = {}
lock = threading.Lock()

# For RTT measurement: { msg_id: timestamp_sent }
pending_pings = {}
seen_messages = set()


def log_event(event_type, details):
    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] [{HOSTNAME}] [{event_type}] {details}")


def receiver_loop():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind(('', PORT))
    sock.settimeout(1.0)

    log_event("LISTENING", f"on port {PORT}")

    while True:
        try:
            data, addr = sock.recvfrom(4096)
            message = json.loads(data.decode('utf-8'))
            msg_type = message.get('type')
            source = message.get('source')
            msg_id = message.get('msg_id')

            if source == HOSTNAME:
                continue

            if msg_id in seen_messages:
                continue
            seen_messages.add(msg_id)

            if msg_type == 'PING':
                log_event("PING", f"from {source} ({addr[0]})")
                # Reply with PONG
                pong = {
                    'type': 'PONG',
                    'msg_id': str(uuid.uuid4())[:8],
                    'source': HOSTNAME,
                    'ping_id': msg_id,
                    'timestamp': time.time()
                }
                sock2 = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                sock2.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                sock2.sendto(json.dumps(pong).encode('utf-8'), (BROADCAST_IP, PORT))
                sock2.close()

                # Record that source is a neighbor (we can hear them)
                with lock:
                    if HOSTNAME not in topology:
                        topology[HOSTNAME] = {}
                    if source not in topology[HOSTNAME]:
                        topology[HOSTNAME][source] = 0.0  # placeholder, will update on PONG

            elif msg_type == 'PONG':
                ping_id = message.get('ping_id')
                if ping_id in pending_pings:
                    sent_ts = pending_pings.pop(ping_id)
                    rtt = (time.time() - sent_ts) * 1000  # ms
                    latency = rtt / 2  # one-way latency estimate
                    log_event("PONG", f"from {source} ({addr[0]}), RTT={rtt:.1f}ms, latency={latency:.1f}ms")

                    with lock:
                        if HOSTNAME not in topology:
                            topology[HOSTNAME] = {}
                        topology[HOSTNAME][source] = round(latency, 1)

                        if source not in topology:
                            topology[source] = {}
                        topology[source][HOSTNAME] = round(latency, 1)

        except socket.timeout:
            pass
        except json.JSONDecodeError:
            pass
        except Exception as e:
            log_event("ERROR", str(e))


def send_ping():
    """Broadcast a PING to discover neighbors."""
    msg_id = str(uuid.uuid4())[:8]
    ping = {
        'type': 'PING',
        'msg_id': msg_id,
        'source': HOSTNAME,
        'timestamp': time.time()
    }
    pending_pings[msg_id] = time.time()
    seen_messages.add(msg_id)

    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.sendto(json.dumps(ping).encode('utf-8'), (BROADCAST_IP, PORT))
    sock.close()


def discovery_loop():
    """Periodically send PINGs to discover the network."""
    while True:
        send_ping()
        time.sleep(3)


def print_topology():
    """Print the discovered network topology."""
    with lock:
        if not topology:
            print("\nNo topology data yet. Waiting for discoveries...")
            return

        print("\n" + "=" * 60)
        print(f"NETWORK TOPOLOGY (from {HOSTNAME})")
        print("=" * 60)
        for node, neighbors in sorted(topology.items()):
            if neighbors:
                neighbor_str = ", ".join(
                    f"{n} ({lat}ms)" for n, lat in sorted(neighbors.items()) if lat > 0
                )
            else:
                neighbor_str = "(no neighbors)"
            print(f"  {node}: {neighbor_str}")
        print("=" * 60)


if __name__ == "__main__":
    listener_thread = threading.Thread(target=receiver_loop, daemon=True)
    listener_thread.start()

    discoverer_thread = threading.Thread(target=discovery_loop, daemon=True)
    discoverer_thread.start()

    time.sleep(1)

    print(f"\n--- Network Topology Discovery ({HOSTNAME}) ---")
    print("Commands:")
    print("  topology   - Print current network topology")
    print("  ping       - Send a single discovery ping now")
    print("  exit       - Exit")
    print("---------------------------------------------\n")

    while True:
        try:
            user_input = input(">> ").strip().lower()
            if not user_input:
                continue
            if user_input == 'exit':
                break
            elif user_input == 'topology':
                print_topology()
            elif user_input == 'ping':
                send_ping()
                log_event("MANUAL_PING", "Sent discovery ping")
            else:
                print("Unknown command. Available: topology, ping, exit")
        except KeyboardInterrupt:
            break
