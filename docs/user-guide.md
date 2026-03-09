# User Guide — k8s-ai-operator

This guide walks you through everything from zero to a running deployment. No prior Kubernetes or AWS experience assumed.

---

## What Is This?

**k8s-ai-operator** is a web service that lets you control a Kubernetes cluster using plain English. Instead of writing `kubectl` commands, you send a sentence like _"Show me the pods in the production namespace"_ and the service figures out what to do.

Under the hood it:
1. Sends your sentence to **AWS Bedrock (Claude)** to interpret it
2. Checks the command against a **safety allowlist** (only `get` and `apply` are permitted)
3. Runs the command against the cluster
4. Logs everything to **DynamoDB** and emits metrics to **CloudWatch**

---

## What You Need Before Starting

### Tools on your local machine
| Tool | How to get it | Check it's working |
|---|---|---|
| AWS CLI v2 | https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html | `aws --version` |
| An SSH client | Built into macOS/Linux terminal. Windows: use Git Bash or WSL | `ssh` |
| `jq` (optional, for pretty output) | https://jqlang.github.io/jq/download/ | `jq --version` |

### AWS account requirements
- An IAM user or role with permissions to create EC2, EKS, DynamoDB, ECR, CloudWatch, IAM, and Bedrock resources
- **Amazon Bedrock model access enabled** for `anthropic.claude-3-sonnet-20240229-v1:0` in `us-east-2`
  - In the AWS Console → Bedrock → Model access → Request access for Claude 3 Sonnet
  - This can take a few minutes to activate

> ⚠️ **If Bedrock model access is not enabled the app will start but every request will fail.**

### Configure the AWS CLI
Run this once on your local machine and follow the prompts:
```bash
aws configure
```
Enter your AWS Access Key ID, Secret Access Key, default region (`us-east-2`), and output format (`json`).

---

## Step 1 — Launch the EC2 Instance

The EC2 instance is the machine that will build and push the Docker image during deployment.

```bash
chmod +x setup-ec2.sh
./setup-ec2.sh
```

This takes about **2 minutes**. When done you'll see:
```
Instance ready — ssh -i k8s-agent-key.pem ubuntu@<PUBLIC_IP>
```

It also saves the instance details to `.k8s-agent-state` in the project folder.

---

## Step 2 — Launch the Kubernetes Cluster

```bash
chmod +x setup-eks.sh
./setup-eks.sh
```

> ⏳ **This takes 15–20 minutes.** You will see repeated lines like:
> ```
> [ℹ]  waiting for CloudFormation stack "eksctl-k8s-ai-operator-test-cluster"
> ```
> This is normal — AWS is building the full cluster infrastructure. **Just let it run.**

When done you'll see:
```
Cluster ready — context set to: <context-name>
Endpoint: https://<hash>.gr7.us-east-2.eks.amazonaws.com
```

---

## Step 3 — Copy the Project to EC2 and Deploy

SSH into the EC2 instance and clone or copy the project files there. Then run the deploy script.

### 3a — SSH into EC2
```bash
source .k8s-agent-state
ssh -i k8s-agent-key.pem ubuntu@"$PUBLIC_IP"
```

> If you get a permissions warning on the `.pem` file:
> ```bash
> chmod 400 k8s-agent-key.pem
> ```

### 3b — Copy the project to EC2
From your **local machine** (open a new terminal, keep the SSH session open):
```bash
source .k8s-agent-state
scp -i k8s-agent-key.pem -r \
  $(pwd) \
  ubuntu@"$PUBLIC_IP":~/k8s-ai-operator
```

### 3c — Run the deploy script on EC2
Back in your SSH session:
```bash
cd ~/k8s-ai-operator
chmod +x deploy.sh
./deploy.sh
```

`deploy.sh` will automatically:
1. Build the application JAR
2. Create an ECR container registry
3. Build and push the Docker image
4. Create the DynamoDB audit table
5. Attach the required IAM permissions to the cluster
6. Deploy the app to Kubernetes
7. Print the live endpoint URL

When complete you'll see:
```
✅  Deployment complete!
    Endpoint: http://<hostname>/k8s/execute

    Test with:
    curl -s -X POST http://<hostname>/k8s/execute \
      -H 'Content-Type: application/json' \
      -d '{"request_id":"test-001","user_prompt":"Show me the pods in namespace production"}' | jq .
```

---

## Step 4 — Send Your First Request

Copy the `curl` command printed by `deploy.sh` and run it. Or use your own prompt:

### Ask for pods in a namespace
```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-001",
    "user_prompt": "Show me the pods in namespace production"
  }' | jq .
```

**Response:**
```json
{
  "requestId": "req-001",
  "allowed": true,
  "command": {
    "verb": "get",
    "resource": "pods",
    "namespace": "production"
  },
  "result": "NAME                          READY   STATUS    RESTARTS   AGE\napp-deployment-7d4f9b-xk2p9   1/1     Running   0          2d\n..."
}
```

### Ask for deployments
```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-002",
    "user_prompt": "List all deployments in staging"
  }' | jq .
```

### Ask for services
```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-003",
    "user_prompt": "What services are running in production?"
  }' | jq .
```

---

## What Happens When a Command Is Blocked

If you ask the service to do something dangerous (delete, scale, exec, patch), it will refuse:

```bash
curl -s -X POST http://<ENDPOINT>/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "req-004",
    "user_prompt": "Delete all pods in production"
  }' | jq .
```

**Response (`400 Bad Request`):**
```json
{
  "requestId": "req-004",
  "allowed": false,
  "reason": "Verb 'delete' is not permitted"
}
```

This block is enforced in code — it cannot be bypassed by rewording the prompt.

### Allowed vs blocked commands

| ✅ Allowed | ❌ Blocked |
|---|---|
| Show me the pods in production | Delete all pods in production |
| List deployments in staging | Scale the deployment to zero |
| What services are in default? | Exec into the nginx container |
| Apply this manifest to staging | Patch the deployment |

---

## Checking Logs and Metrics

### Application logs (from EC2)
```bash
kubectl logs -f deployment/k8s-ai-operator -n k8s-ai-operator
```

### Audit records in DynamoDB
In the AWS Console → DynamoDB → Tables → `K8sAgentExecutions` → Explore items.

Every request (allowed and blocked) is recorded with:
- `request_id` — the ID you sent
- `timestamp` — when it happened
- `parsed_verb` / `parsed_resource` / `parsed_namespace` — what Bedrock understood
- `allowed` — true or false
- `execution_latency_ms` — how long it took

> **Note:** Your raw prompt text is never stored.

### CloudWatch metrics
In the AWS Console → CloudWatch → Metrics → Custom namespaces → `K8sAiOperator/Execution`.

Available metrics:
- `AllowedCommands` — count of permitted requests
- `BlockedCommands` — count of rejected requests
- `ExecutionLatencyMs` — end-to-end response time

---

## Troubleshooting

| Problem | What to check |
|---|---|
| `deploy.sh` fails at the JAR build step | Make sure Java 21 is installed on EC2: `java -version`. Install with `sudo apt-get install -y openjdk-21-jdk` |
| Pod stuck in `ImagePullBackOff` | ECR auth issue — re-run `deploy.sh`, it will re-authenticate |
| Pod stuck in `CrashLoopBackOff` | Check logs: `kubectl logs deployment/k8s-ai-operator -n k8s-ai-operator` |
| `500` error on every request | Bedrock model access not enabled — check the AWS Console → Bedrock → Model access |
| LoadBalancer hostname never appears | Run `kubectl get svc -n k8s-ai-operator` and wait a few more minutes |
| `ssh: Permission denied` | Run `chmod 400 k8s-agent-key.pem` first |

---

## Teardown (Avoid Ongoing Charges)

When you're done, delete everything to stop AWS charges (~$0.10/hr for EKS control plane + EC2 + node costs).

```bash
# 1. Remove the app from Kubernetes
kubectl delete -f k8s-operator-manifest.yaml

# 2. Delete the EKS cluster (~10 min)
source .k8s-cluster-state
eksctl delete cluster --name "$CLUSTER_NAME" --region "$REGION"

# 3. Terminate the EC2 instance
source .k8s-agent-state
aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" --region "$REGION"
aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID" --region "$REGION"
aws ec2 delete-security-group --group-id "$SG_ID" --region "$REGION"

# 4. Delete AWS resources
aws dynamodb delete-table --table-name K8sAgentExecutions --region us-east-2
aws ecr delete-repository --repository-name k8s-ai-operator --region us-east-2 --force

# 5. Clean up local files
rm -f k8s-agent-key.pem .k8s-agent-state .k8s-cluster-state
```

