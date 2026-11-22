#!/usr/bin/env bash
# Runs the built operator JAR against a local AWS stub (tools/stub_aws.py) and records a
# transcript of real requests and responses to docs/screenshots/transcripts/. No AWS account,
# cluster or API key is used: Bedrock replies are canned by the stub (see its docstring).
# Usage (from the repo root, after ./mvnw package): docs/screenshots/tools/demo.sh
set -euo pipefail
cd "$(dirname "$0")/../../.."
OUT=docs/screenshots/transcripts
mkdir -p "$OUT"
free_port() { python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1])'; }
STUB_PORT=$(free_port); APP_PORT=$(free_port)
WORK=$(mktemp -d); STUB_LOG="$WORK/stub.log"; : > "$STUB_LOG"
# Run the JAR exploded, with a screenshot-only config (tools/overlay) that points the AWS clients at the stub
JAR=$(ls "$PWD"/target/k8s-ai-operator-*.jar); mkdir "$WORK/app"; (cd "$WORK/app" && unzip -q "$JAR")
mkdir "$WORK/overlay"
javac -d "$WORK/overlay" -cp "$WORK/app/BOOT-INF/classes:$WORK/app/BOOT-INF/lib/*" \
  docs/screenshots/tools/overlay/com/stephenson/k8saioperator/screenshots/StubEndpointConfig.java
python3 docs/screenshots/tools/stub_aws.py "$STUB_PORT" "$STUB_LOG" & STUB_PID=$!
AWS_ACCESS_KEY_ID=demo AWS_SECRET_ACCESS_KEY=demo AWS_REGION=us-east-1 \
USAGE_REPORTING_ENABLED=false LLM_PROVIDER=bedrock \
  java -cp "$WORK/app/BOOT-INF/classes:$WORK/app/BOOT-INF/lib/*:$WORK/overlay" \
  com.stephenson.k8saioperator.K8sAiOperatorApplication \
  --server.port="$APP_PORT" --screenshots.stub-endpoint="http://127.0.0.1:$STUB_PORT" > "$WORK/app.log" 2>&1 & APP_PID=$!
trap 'kill $APP_PID $STUB_PID 2>/dev/null || true; rm -rf "$WORK"' EXIT
until curl -sf "http://127.0.0.1:$APP_PORT/actuator/health" > /dev/null; do sleep 1; done

ask() {  # ask REQUEST_ID PROMPT
  local body; body=$(printf '{"request_id": "%s", "user_prompt": "%s"}' "$1" "$2")
  echo "\$ curl -s -X POST http://127.0.0.1:$APP_PORT/k8s/execute -H 'Content-Type: application/json' \\"
  echo "    -d '$body'"
  curl -s -w '\nHTTP %{http_code}\n' -X POST "http://127.0.0.1:$APP_PORT/k8s/execute" \
    -H 'Content-Type: application/json' -d "$body" | python3 -c '
import json, sys
lines = sys.stdin.read().strip().splitlines()
resp = json.loads("\n".join(lines[:-1])); r = resp.get("result")
if r: resp["result"] = "<printed unescaped below>"
print(lines[-1]); print(json.dumps(resp, indent=2))
if r: print("result:\n" + r)'
  echo
}

{ ask req-001 "Show me the pods in namespace production"
  ask req-002 "Which deployments are running in staging?"; } > "$OUT/allowed.txt"
{ ask req-003 "Delete all pods in production"
  ask "" "Show me the pods"; } > "$OUT/blocked.txt"
sed -E 's/"execution_latency_ms": \{"N": "[0-9]+"\}/"execution_latency_ms": {"N": "<ms>"}/; s/"timestamp": \{"S": "[^"]+"\}/"timestamp": {"S": "<iso-8601>"}/' \
  "$STUB_LOG" | python3 -c '
import json, sys
for line in sys.stdin:
    kind, _, rest = line.partition(" ")
    if kind == "dynamodb":
        op, _, item = rest.partition(" ")
        print("DynamoDB " + op + " (K8sAgentExecutions)")
        for k, v in sorted(json.loads(item).items()): print(f"  {k:<22} {json.dumps(v)}")
    else:
        print(line.rstrip().replace("cloudwatch ", "CloudWatch "))' > "$OUT/audit.txt"
echo "transcripts written to $OUT"
