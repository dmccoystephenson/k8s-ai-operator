"""Local stand-in for Bedrock, DynamoDB and CloudWatch, used only to capture the README screenshots.

The operator is pointed at it with AWS_ENDPOINT_URL. It is NOT a model: for Bedrock InvokeModel it
returns a fixed command chosen by a keyword in the prompt, so the screenshots show the operator's
own parsing, verb guard, mock Kubernetes client, audit item and metrics calls, not an LLM's output.
DynamoDB PutItem items and CloudWatch metric names are appended to the log file given as argv[2].

Usage: python3 stub_aws.py PORT LOGFILE
"""
import json
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1])
LOG = sys.argv[2]


def canned_command(prompt: str) -> dict:
    p = prompt.lower()
    namespace = "production" if "production" in p else "staging" if "staging" in p else "default"
    if "delete" in p:
        return {"verb": "delete", "resource": "pods", "namespace": namespace}
    if "apply" in p:
        return {"verb": "apply", "resource": "deployments", "namespace": namespace}
    if "deployment" in p:
        return {"verb": "get", "resource": "deployments", "namespace": namespace}
    if "service" in p:
        return {"verb": "get", "resource": "services", "namespace": namespace}
    return {"verb": "get", "resource": "pods", "namespace": namespace}


def log(line: str) -> None:
    with open(LOG, "a", encoding="utf-8") as f:
        f.write(line + "\n")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, status: int, body: bytes, ctype: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        target = self.headers.get("X-Amz-Target", "")
        if self.path.startswith("/model/") and self.path.endswith("/invoke"):
            req = json.loads(body)
            prompt = req["messages"][0]["content"][0]["text"]
            reply = {"content": [{"type": "text", "text": json.dumps(canned_command(prompt))}]}
            self.send(200, json.dumps(reply).encode(), "application/json")
        elif target.endswith(".PutItem"):
            item = json.loads(body)["Item"]
            log("dynamodb PutItem " + json.dumps(item, sort_keys=True))
            self.send(200, b"{}", "application/x-amz-json-1.0")
        elif b"Action=PutMetricData" in body:
            form = urllib.parse.parse_qs(body.decode())
            names = [v[0] for k, v in sorted(form.items()) if k.endswith(".MetricName")]
            log("cloudwatch PutMetricData " + ", ".join(names))
            xml = ('<PutMetricDataResponse xmlns="http://monitoring.amazonaws.com/doc/2010-08-01/">'
                   "<ResponseMetadata><RequestId>stub</RequestId></ResponseMetadata>"
                   "</PutMetricDataResponse>")
            self.send(200, xml.encode(), "text/xml")
        else:
            log(f"unhandled {self.path} {target} {body[:200]!r}")
            self.send(400, b"{}", "application/json")


HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
