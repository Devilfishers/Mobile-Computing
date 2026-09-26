import json 
import random 
import socket 
import sys 
import threading 
import time 
import uuid 

PORT = 5001 
BROADCAST_IP = "192.168.210.255" 

HOSTNAME = sys.argv[1] if len(sys.argv) > 1 else socket.gethostname() 

seen_rreqs = set() 
seen_rrep = set() 
seen_data = set() 

pending_routes = [] 
PENDING_LOCK = threading.Lock() 

FORWARD_JITTER_SECONDS = (0.02, 0.08) 

MAX_RREQ_RETRIES = 4 
RREQ_RETRY_INTERVAL = 1.0 


def log_event(event_type, details): 
    print( 
        f"[{time.strftime('%H:%M:%S')}] [{HOSTNAME}] [{event_type}] {details}", 
        flush=True, 
    ) 


def node_matches(name): 
    if name is None: 
        return False 
    name = str(name) 
    return HOSTNAME == name or HOSTNAME.startswith(name) or HOSTNAME.endswith(name) 


def jitter(): 
    time.sleep(random.uniform(*FORWARD_JITTER_SECONDS)) 


def broadcast(msg_dict): 
    try: 
        data = json.dumps(msg_dict).encode() 
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) 
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1) 
        sock.sendto(data, (BROADCAST_IP, PORT)) 
        sock.close() 
    except Exception as e: 
        log_event("BROADCAST_ERROR", str(e)) 


def handle_rreq(msg): 
    msg_id = msg["msg_id"] 
    source = msg["source"] 
    dest = msg["dest"] 
    route = list(msg.get("route", [])) 

    if not route or route[-1] != HOSTNAME: 
        new_route = route + [HOSTNAME] 
    else: 
        new_route = route 

    log_event( 
        "RREQ_RECV", 
        f"msg_id={msg_id}, source={source}, dest={dest}, " 
        f"route={' -> '.join(new_route)}", 
    ) 

    if node_matches(dest): 
        log_event( 
            "RREQ_HIT", 
            f"I am destination {dest}. Discovered route: {' -> '.join(new_route)}", 
        ) 

        if len(new_route) < 2: 
            log_event("RREP_DROP", "Route too short, no previous hop") 
            return 

        rrep = { 
            "type": "RREP", 
            "msg_id": str(uuid.uuid4())[:8], 
            "source": HOSTNAME, 
            "dest": dest, 
            "rrep_dest": source, 
            "route": new_route, 
            "target": new_route[-2], 
        } 

        log_event( 
            "RREP_SEND", 
            f"target={rrep['target']}, rrep_dest={rrep['rrep_dest']}, " 
            f"route={' -> '.join(new_route)}", 
        ) 
        jitter() 
        broadcast(rrep) 
        return 

    msg["route"] = new_route 
    log_event("RREQ_FWD", f"Forwarding RREQ. route={' -> '.join(new_route)}") 
    jitter() 
    broadcast(msg) 


def handle_rrep(msg): 
    msg_id = msg["msg_id"] 
    route = msg["route"] 
    target = msg.get("target") 
    rrep_dest = msg.get("rrep_dest") 
    dest_node = msg.get("dest") 

    log_event( 
        "RREP_RECV", 
        f"msg_id={msg_id}, target={target}, rrep_dest={rrep_dest}, " 
        f"route={' -> '.join(route)}", 
    ) 

    if HOSTNAME == rrep_dest: 
        log_event( 
            "ROUTE_DISCOVERED", 
            f"Route to {dest_node} established: {' -> '.join(route)}", 
        ) 

        to_send = [] 
        remaining = [] 
        with PENDING_LOCK: 
            for entry in pending_routes: 
                if entry["dest"] == dest_node: 
                    to_send.append(entry) 
                else: 
                    remaining.append(entry) 
            pending_routes[:] = remaining 

        for entry in to_send: 
            payload = entry["payload"] 
            if len(route) > 1: 
                first_target = route[1] 
            else: 
                first_target = dest_node 

            data_msg = { 
                "type": "DATA", 
                "msg_id": str(uuid.uuid4())[:8], 
                "source": HOSTNAME, 
                "dest": dest_node, 
                "route": route, 
                "target": first_target, 
                "payload": payload, 
            } 
            seen_data.add((data_msg["msg_id"], HOSTNAME)) 
            log_event( 
                "DATA_SEND", 
                f"Sending payload='{payload}' to {dest_node}, target={first_target}", 
            ) 
            jitter() 
            broadcast(data_msg) 

            entry["done"].set() 
        return 

    try: 
        idx = route.index(HOSTNAME) 
    except ValueError: 
        log_event("RREP_DROP", "I am not inside the route") 
        return 

    if idx <= 0: 
        log_event("RREP_DROP", "No previous hop available") 
        return 

    next_target = route[idx - 1] 
    msg["target"] = next_target 
    log_event("RREP_FWD", f"Forwarding RREP to {next_target}") 
    jitter() 
    broadcast(msg) 


def handle_data(msg): 
    msg_id = msg["msg_id"] 
    route = msg["route"] 
    source = msg["source"] 
    dest = msg["dest"] 
    payload = msg.get("payload", "") 

    log_event( 
        "DATA_RECV", 
        f"msg_id={msg_id}, source={source}, dest={dest}, target={msg.get('target')}, " 
        f"route={' -> '.join(route)}", 
    ) 

    if node_matches(dest): 
        log_event("DATA_DELIVERED", f"From {source}: '{payload}'") 
        return 

    try: 
        idx = route.index(HOSTNAME) 
    except ValueError: 
        log_event("DATA_DROP", "I am not inside the route") 
        return 

    if idx >= len(route) - 1: 
        log_event("DATA_DROP", "No next hop available") 
        return 

    next_target = route[idx + 1] 
    msg["target"] = next_target 
    log_event("DATA_FWD", f"Forwarding DATA to {next_target}") 
    jitter() 
    broadcast(msg) 


def _broadcast_rreq(dest, rreq_id): 
    rreq = { 
        "type": "RREQ", 
        "msg_id": rreq_id, 
        "source": HOSTNAME, 
        "dest": dest, 
        "route": [HOSTNAME], 
    } 
    log_event("RREQ_SEND", f"Route discovery to {dest}, msg_id={rreq_id}") 
    broadcast(rreq) 


def _discover(dest, payload, done): 
    for attempt in range(1, MAX_RREQ_RETRIES + 1): 
        if done.is_set(): 
            return 

        rreq_id = str(uuid.uuid4())[:8] 
        seen_rreqs.add((HOSTNAME, rreq_id)) 
        _broadcast_rreq(dest, rreq_id) 

        if done.wait(RREQ_RETRY_INTERVAL): 
            return 

    if not done.is_set(): 
        done.set() 
        with PENDING_LOCK: 
            pending_routes[:] = [ 
                e for e in pending_routes if e["done"] is not done 
            ] 
        log_event( 
            "ROUTE_TIMEOUT", 
            f"No route to {dest} after {MAX_RREQ_RETRIES} attempts. " 
            f"Payload '{payload}' dropped.", 
        ) 


def send_message(dest, payload): 
    done = threading.Event() 
    entry = {"dest": dest, "payload": payload, "done": done} 
    with PENDING_LOCK: 
        pending_routes.append(entry) 
    threading.Thread( 
        target=_discover, args=(dest, payload, done), daemon=True 
    ).start() 


def receiver_loop(): 
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM) 
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1) 
    try: 
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1) 
    except Exception: 
        pass 
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1) 
    sock.bind(("", PORT)) 

    log_event("LISTENING", f"UDP port {PORT}, broadcast={BROADCAST_IP}") 

    while True: 
        try: 
            data, addr = sock.recvfrom(4096) 
            msg = json.loads(data.decode()) 

            msg_type = msg.get("type") 
            msg_id = msg.get("msg_id") 
            source = msg.get("source") 
            target = msg.get("target") 

            if msg_type is None or msg_id is None or source is None: 
                log_event("DROP", "Missing type/msg_id/source") 
                continue 

            if msg_type == "RREQ": 
                if source == HOSTNAME: 
                    continue 
                rreq_key = (source, msg_id) 
                if rreq_key in seen_rreqs: 
                    log_event("RREQ_DROP", f"Duplicate {rreq_key}") 
                    continue 
                seen_rreqs.add(rreq_key) 
                handle_rreq(msg) 

            elif msg_type == "RREP": 
                if target != HOSTNAME: 
                    continue 
                rrep_key = (msg_id, HOSTNAME) 
                if rrep_key in seen_rrep: 
                    log_event("RREP_DROP", f"Duplicate {rrep_key}") 
                    continue 
                seen_rrep.add(rrep_key) 
                handle_rrep(msg) 

            elif msg_type == "DATA": 
                if target != HOSTNAME: 
                    continue 
                data_key = (msg_id, HOSTNAME) 
                if data_key in seen_data: 
                    log_event("DATA_DROP", f"Duplicate {data_key}") 
                    continue 
                seen_data.add(data_key) 
                handle_data(msg) 

            else: 
                log_event("DROP", f"Unknown msg_type={msg_type}") 

        except json.JSONDecodeError: 
            log_event("ERROR", "Received invalid JSON") 
        except Exception as e: 
            log_event("ERROR", str(e)) 


if __name__ == "__main__": 
    threading.Thread(target=receiver_loop, daemon=True).start() 
    time.sleep(0.5) 

    print() 
    print(f"--- DSR Node ({HOSTNAME}) ---") 
    print("Commands:") 
    print("  send <dest_hostname_or_number> <message>") 
    print("  exit") 
    print() 
    print("Examples:") 
    print("  send mcladhoc-01 hello") 
    print("  send Pi1 hello") 
    print("  send 1 hello") 
    print("------------------------------") 
    print() 

    while True: 
        try: 
            cmd = input(">> ").strip() 
            if not cmd: 
                continue 
            if cmd.lower() == "exit": 
                break 
            if cmd.lower().startswith("send "): 
                parts = cmd.split(" ", 2) 
                if len(parts) < 3: 
                    print("Usage: send <dest_hostname_or_number> <message>") 
                    continue 
                send_message(parts[1], parts[2]) 
                continue 
            print
        except KeyboardInterrupt: 
            break
