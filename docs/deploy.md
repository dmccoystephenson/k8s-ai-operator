# Deployment Guide — k8s-ai-operator

> **Assumes:** `setup-ec2.sh` and `setup-eks.sh` have both been run successfully.

---

## What the Running App Connects To

Once deployed, the application is fully wired to live AWS services on every request:

| Service | What it does | Credential source |
|---|---|---|
| **Amazon Bedrock (Claude)** | Translates the natural-language prompt into a structured kubectl command | EKS node IAM role |
| **K8sClientAdapter (mock)** | Executes the parsed command and returns simulated cluster output | n/a (in-process) |
| **Amazon DynamoDB** | Writes an audit record for every request — allowed and blocked | EKS node IAM role |
| **Amazon CloudWatch** | Emits `AllowedCommands`, `BlockedCommands`, and `ExecutionLatencyMs` metrics | EKS node IAM role |

The IAM policies (`AmazonBedrockFullAccess`, `AmazonDynamoDBFullAccess`, `CloudWatchFullAccess`) are attached to the EKS node role automatically by `deploy.sh` — no credentials need to be configured manually in the app.

---

## Prerequisites

| Tool | Check | Notes |
|---|---|---|
| AWS CLI v2 | `aws --version` | Must be configured with your IAM identity |
| Docker | `docker --version` | **Auto-installed by `deploy.sh`** if missing |
| kubectl | `kubectl version --client` | Installed by `setup-eks.sh` |
| Maven wrapper | `./mvnw --version` | Included in the repo |
| jq | `jq --version` | Used to pretty-print test output |

---

## One-Command Deploy

```bash
chmod +x deploy.sh
./deploy.sh
```

That's it. The script handles every step below automatically. Read on if you want to understand what it does or run steps individually.

---

## What `deploy.sh` Does (Step by Step)

### Step 1 — Build the JAR
```bash
./mvnw clean package -DskipTests
```
Compiles the Spring Boot app and produces `target/edgescaleai-tech-interview-*.jar`.

---

### Step 2 — Create ECR Repository
```bash
aws ecr create-repository --repository-name k8s-ai-operator --region us-east-2
```
Creates a private container registry in your AWS account. **Idempotent** — skipped if it already exists.

---

### Step 3 — Build & Push Docker Image
```bash
# Authenticate
aws ecr get-login-password --region us-east-2 \
  | docker login --username AWS --password-stdin <ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com

# Build and push
docker build -t k8s-ai-operator:latest .
docker tag k8s-ai-operator:latest <ECR_URI>:latest
docker push <ECR_URI>:latest
```

The `Dockerfile` at the project root uses `eclipse-temurin:21-jre-alpine` — a minimal JRE image (~90 MB).

---

### Step 4 — Create DynamoDB Audit Table
```bash
aws dynamodb create-table \
  --table-name K8sAgentExecutions \
  --attribute-definitions AttributeName=request_id,AttributeType=S \
  --key-schema AttributeName=request_id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-2
```
The app writes an audit record to this table for every request (allowed and blocked). **Idempotent** — skipped if the table already exists.

---

### Step 5 — Attach IAM Policies to EKS Node Role
The app needs three AWS services at runtime. The script attaches these managed policies to the node group's IAM role:

| Policy | Used for |
|---|---|
| `AmazonBedrockFullAccess` | Calling Claude via Bedrock |
| `AmazonDynamoDBFullAccess` | Writing audit records |
| `CloudWatchFullAccess` | Emitting execution metrics |

---

### Step 6 — Deploy to EKS
```bash
kubectl apply -f k8s-operator-manifest.yaml
```

The manifest (`k8s-operator-manifest.yaml`) creates:
- A dedicated `k8s-ai-operator` **namespace**
- A **ConfigMap** with all environment variables (region, model ID, table name, etc.)
- A **Deployment** with 1 replica, health probes, and resource limits
- A **LoadBalancer Service** that exposes port 80 → 8080

---

### Step 7 — Verify

Check pod status:
```bash
kubectl get pods -n k8s-ai-operator
kubectl logs -f deployment/k8s-ai-operator -n k8s-ai-operator
```

Get the public endpoint:
```bash
kubectl get svc k8s-ai-operator -n k8s-ai-operator
# Copy the EXTERNAL-IP / hostname from the output
```

---

## Testing the Deployed API

### Allowed command
```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-001","user_prompt":"Show me the pods in namespace production"}' | jq .
```

Expected response:
```json
{
  "requestId": "test-001",
  "allowed": true,
  "command": { "verb": "get", "resource": "pods", "namespace": "production" },
  "result": "NAME   READY   STATUS ..."
}
```

### Blocked command (verb guard)
```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-002","user_prompt":"Delete all pods in production"}' | jq .
```

Expected response:
```json
{
  "requestId": "test-002",
  "allowed": false,
  "reason": "Verb 'delete' is not permitted"
}
```

---

## Configuration Reference

All config values are environment variables injected via the ConfigMap. Override them by editing `k8s-operator-manifest.yaml` before deploying.

| Variable | Default | Description |
|---|---|---|
| `AWS_REGION` | `us-east-2` | AWS region for all SDK clients |
| `BEDROCK_MODEL_ID` | `anthropic.claude-3-sonnet-20240229-v1:0` | Claude model used for NL parsing |
| `BEDROCK_MAX_TOKENS` | `512` | Max tokens in Bedrock response |
| `DYNAMODB_TABLE` | `K8sAgentExecutions` | Audit log table name |
| `CLOUDWATCH_NAMESPACE` | `K8sAiOperator/Execution` | CloudWatch metrics namespace |

---

## Teardown

```bash
# Remove the operator from EKS
kubectl delete -f k8s-operator-manifest.yaml

# Delete the EKS cluster (takes ~10 min)
source .k8s-cluster-state
eksctl delete cluster --name "$CLUSTER_NAME" --region "$REGION"

# Delete the DynamoDB table
aws dynamodb delete-table --table-name K8sAgentExecutions --region us-east-2

# Delete the ECR repository
aws ecr delete-repository --repository-name k8s-ai-operator --region us-east-2 --force
```

