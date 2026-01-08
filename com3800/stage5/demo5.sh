#!/usr/bin/env bash
#Worked with eitan markovitz
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$SCRIPT_DIR/output.log"
mkdir -p "$SCRIPT_DIR"
: > "$LOG_FILE"
exec > >(tee -a "$LOG_FILE") 2>&1

echo "== Stage 5 demo script starting =="
echo "Script dir: $SCRIPT_DIR"
echo "Log file:   $LOG_FILE"
echo

echo "== Step 1: mvn test =="
cd "$SCRIPT_DIR"
#mvn clean compile
mvn -q test
echo "mvn test: OK"
echo

echo "== Building runtime classpath =="
CP_FILE="$SCRIPT_DIR/.demo5_classpath.txt"
mvn -q -DincludeScope=runtime -Dmdep.outputFile="$CP_FILE" dependency:build-classpath
RUNTIME_CP="$(cat "$CP_FILE")"
CP="$SCRIPT_DIR/target/classes:$SCRIPT_DIR/target/test-classes:$RUNTIME_CP"
echo "Classpath ready."
echo

echo
echo "== Step 2: Starting Gateway and Peer Servers =="

NUM_PEERS=7
PORT_SCALER=10
GATEWAY_ID=10000
GATEWAY_UDP_PORT=7999
GATEWAY_HTTP_PORT=8888
NUM_OBSERVERS=1
PEER_EPOCH=0

PIDS=()

echo "Starting Gateway..."
java -cp "$CP" \
  edu.yu.cs.com3800.stage5.GatewayMain \
  "$GATEWAY_HTTP_PORT" \
  "$GATEWAY_UDP_PORT" \
  "$PEER_EPOCH" \
  "$GATEWAY_ID" \
  "$NUM_OBSERVERS" \
  "$NUM_PEERS" \
  "$PORT_SCALER" \
  &

GATEWAY_PID=$!
PIDS+=("$GATEWAY_PID")
echo "Gateway started (PID=$GATEWAY_PID)"
echo

sleep 1

for ((i=1; i<=NUM_PEERS; i++)); do
  UDP_PORT=$((8000 + i * PORT_SCALER))

  echo "Starting Peer $i on UDP port $UDP_PORT..."

  java -cp "$CP" \
    edu.yu.cs.com3800.stage5.PeerServerMain \
    "$UDP_PORT" \
    "$PEER_EPOCH" \
    "$i" \
    "$GATEWAY_ID" \
    "$NUM_OBSERVERS" \
    "$NUM_PEERS" \
    "$PORT_SCALER" \
    &

  PID=$!
  PIDS+=("$PID")
  echo "Peer $i started (PID=$PID)"
done

echo
echo "All peers and gateway started."
echo

echo "== Step 3: Waiting for leader election =="
LEADER_JSON="null"

while true; do
  LEADER_JSON=$(curl -s http://localhost:8888/leader || echo "null")

  if [[ "$LEADER_JSON" != "null" ]]; then
    echo "Leader elected."
    echo "Cluster state:"
    echo "$LEADER_JSON" | jq .
    break
  fi

  #echo "No leader yet, retrying..."
  sleep 1
done

echo
echo "== Step 4: Sending 9 client requests =="
echo

for i in {1..9}; do
  REQUEST_BODY=$(cat <<EOF
package edu.yu.cs.com3800.stage5;

public class HelloWorld {
    public String run() {
        return "Hello from request $i";
    }
}
EOF
)

  echo "---- Request $i ----"
  echo "$REQUEST_BODY"
  echo

  RESPONSE=$(curl -s \
    -X POST \
    -H "Content-Type: text/x-java-source" \
    --data "$REQUEST_BODY" \
    http://localhost:8888/compileandrun)

  echo "Response $i:"
  echo "$RESPONSE"
  echo
done

echo "All 9 requests completed."
echo

echo "== Step 5: Killing a follower =="

# Extract leader ID from JSON
LEADER_ID=$(echo "$LEADER_JSON" | jq -r '.leader')

# Pick first follower that is NOT leader
KILLED_PEER_ID=""
KILLED_PID=""

for ((i=1; i<=NUM_PEERS; i++)); do
  if [[ "$i" != "$LEADER_ID" ]]; then
    KILLED_PEER_ID="$i"
    KILLED_PID="${PIDS[$i]}"
    break
  fi
done

echo "Killing follower serverId=$KILLED_PEER_ID (PID=$KILLED_PID)"
kill -9 "$KILLED_PID"

echo "Waiting for failure detection ..."
sleep 20

echo
echo "Cluster state after killing follower $KILLED_PEER_ID:"
UPDATED_JSON=$(curl -s http://localhost:8888/leader)
echo "$UPDATED_JSON" | jq .
echo

echo "== Step 6: Killing the leader =="

LEADER_PID="${PIDS[$LEADER_ID]}"

echo "Killing leader serverId=$LEADER_ID (PID=$LEADER_PID)"
kill -9 "$LEADER_PID"

echo "Waiting 1000ms after leader kill..."
sleep 1

echo
echo "== Step 6.5: Sending 9 client requests during re-election =="
echo

REQUEST_PIDS=()
RESPONSE_FILES=()

for i in {10..18}; do
  RESPONSE_FILE="$SCRIPT_DIR/response_$i.txt"
  RESPONSE_FILES+=("$RESPONSE_FILE")

  (
    REQUEST_BODY=$(cat <<EOF
package edu.yu.cs.com3800.stage5;

public class HelloWorld {
    public String run() {
        return "Hello from request $i";
    }
}
EOF
)

    echo "---- Background Request $i ----"
    echo "$REQUEST_BODY"
    echo

    RESPONSE=$(curl -s \
      -X POST \
      -H "Content-Type: text/x-java-source" \
      --data "$REQUEST_BODY" \
      http://localhost:8888/compileandrun)

    echo "$RESPONSE" > "$RESPONSE_FILE"
  ) &

  REQUEST_PIDS+=($!)
done


echo "== Step 7: Waiting for new leader =="

NEW_LEADER_ID="null"

while true; do
  NEW_LEADER_JSON=$(curl -s http://localhost:8888/leader || echo "null")
  if [[ "$NEW_LEADER_JSON" != "null" ]]; then
    NEW_LEADER_ID=$(echo "$NEW_LEADER_JSON" | jq -r '.leader')
    if [[ "$NEW_LEADER_ID" != "$LEADER_ID" ]]; then
      echo "New leader elected: $NEW_LEADER_ID"
      break
    fi
  fi
  sleep 1
done

echo
echo "== Responses after re-election =="

for pid in "${REQUEST_PIDS[@]}"; do
  wait "$pid"
done

for i in {10..18}; do
  echo "Response $i:"
  cat "$SCRIPT_DIR/response_$i.txt"
  echo
done

echo "All 9 requests during re-election completed."

echo
echo "== Step 8: Sending one last client request =="

LAST_REQUEST_BODY=$(cat <<EOF
package edu.yu.cs.com3800.stage5;

public class HelloWorld {
    public String run() {
        return "Hello from last request";
    }
}
EOF
)

echo "---- Final Request ----"
echo "$LAST_REQUEST_BODY"
echo

LAST_RESPONSE=$(curl -s \
  -X POST \
  -H "Content-Type: text/x-java-source" \
  --data "$LAST_REQUEST_BODY" \
  http://localhost:8888/compileandrun)

echo "Final response:"
echo "$LAST_RESPONSE"
echo

echo "== Step 9: Listing summary and verbose log files =="
LOG_DIRS=()
while IFS= read -r d; do
  LOG_DIRS+=("$d")
done < <(ls -td logs-* 2>/dev/null | head -n 2)
if [[ ${#LOG_DIRS[@]} -eq 0 ]]; then
  echo "No logs directory found."
else
  echo "Logs directory:"
  for d in "${LOG_DIRS[@]}"; do
    echo "  $d"
  done
  echo


  find_log() {
    local sid="$1"
    local kind="$2"
    local f
    for d in "${LOG_DIRS[@]}"; do
      f="$d/Server $sid $kind Logger-Log.txt"
      if [[ -f "$f" && "$f" != *.lck ]]; then
        echo "$f"
        return 0
      fi
    done
    echo "  (none)"
    return 1
  }

  echo "Gateway logs:"
  find_log 10000 Summary
  find_log 10000 Verbose
  echo

  for ((i=1; i<=NUM_PEERS; i++)); do
    echo "Peer $i logs:"
    find_log "$i" Summary
    find_log "$i" Verbose
    echo
  done

  LOCKS_FOUND=false
  for d in "${LOG_DIRS[@]}"; do
    if compgen -G "$d/*.txt.lck" > /dev/null; then
      LOCKS_FOUND=true
    fi
  done
  if [[ "$LOCKS_FOUND" == true ]]; then
    echo
  fi
fi

echo "== Step 10: Shutting down all nodes =="

for pid in "${PIDS[@]}"; do
  if kill -0 "$pid" 2>/dev/null; then
    echo "Shutting down PID $pid"
    kill "$pid" || true
  fi
done

sleep 1

echo "All nodes shut down."
echo "== Demo complete =="