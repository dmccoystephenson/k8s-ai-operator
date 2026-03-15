# EC2 Provisioning Guide — k8s-ai-operator

This guide walks through spinning up an EC2 instance in `us-east-1` and deploying the k8s-ai-operator Spring Boot application on it.

---

## Prerequisites

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) installed and configured (`aws configure`)
- Java 21 and Maven 3.9+ installed locally (to build the JAR)
- `curl` available in your shell (used to detect your public IP for SSH ingress)
- Sufficient IAM permissions to manage EC2, key pairs, and security groups

---

## 1 — Run the Provisioning Script

A shell script is provided to automate all EC2 setup steps.

```bash
chmod +x setup-ec2.sh
./setup-ec2.sh
```

The script performs the following actions in order:

| Step | What happens |
|---|---|
| Verify credentials | Calls `aws sts get-caller-identity` to confirm the active IAM identity |
| Create key pair | Generates `k8s-agent-key.pem` locally with `chmod 400` applied |
| Create security group | Creates `k8s-agent-sg` and opens port 22 (SSH, your IP only) and port 8080 (HTTP, world) |
| Resolve AMI | Queries the Canonical owner account for the latest Ubuntu 22.04 LTS HVM AMI in `us-east-1` |
| Launch instance | Starts a `t3.small` instance tagged `Name=k8s-agent` |
| Wait for running state | Blocks until the instance passes the `instance-running` check |
| Save state | Writes `INSTANCE_ID`, `SG_ID`, `REGION`, and `PUBLIC_IP` to `.k8s-agent-state` |

On success the script prints:

```
Instance ready — ssh -i k8s-agent-key.pem ubuntu@<PUBLIC_IP>
```

### Script reference — `setup-ec2.sh`

```bash
#!/usr/bin/env bash
# setup-ec2.sh — Provision EC2 instance for k8s-ai-operator
set -euo pipefail

REGION="us-east-1"
KEY_NAME="k8s-agent-key"
SG_NAME="k8s-agent-sg"

# Verify credentials
aws sts get-caller-identity --region "$REGION"

# Key pair
aws ec2 create-key-pair \
  --key-name "$KEY_NAME" \
  --region "$REGION" \
  --query 'KeyMaterial' \
  --output text > "${KEY_NAME}.pem"
chmod 400 "${KEY_NAME}.pem"

# Security group
SG_ID=$(aws ec2 create-security-group \
  --group-name "$SG_NAME" \
  --description "k8s-ai-operator security group" \
  --region "$REGION" \
  --query 'GroupId' \
  --output text)

LOCAL_IP=$(curl -s https://checkip.amazonaws.com)

aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --region "$REGION" \
  --protocol tcp --port 22 --cidr "${LOCAL_IP}/32"

aws ec2 authorize-security-group-ingress \
  --group-id "$SG_ID" --region "$REGION" \
  --protocol tcp --port 8080 --cidr 0.0.0.0/0

# Resolve latest Ubuntu 22.04 AMI
AMI_ID=$(aws ec2 describe-images \
  --owners 099720109477 \
  --region "$REGION" \
  --filters \
    "Name=name,Values=ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*" \
    "Name=state,Values=available" \
  --query 'sort_by(Images, &CreationDate)[-1].ImageId' \
  --output text)

# Launch instance
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.small \
  --key-name "$KEY_NAME" \
  --security-group-ids "$SG_ID" \
  --region "$REGION" \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=k8s-agent}]' \
  --query 'Instances[0].InstanceId' \
  --output text)

aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$REGION"

PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --region "$REGION" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

cat > .k8s-agent-state <<EOF
INSTANCE_ID=$INSTANCE_ID
SG_ID=$SG_ID
REGION=$REGION
PUBLIC_IP=$PUBLIC_IP
EOF

echo "Instance ready — ssh -i ${KEY_NAME}.pem ubuntu@${PUBLIC_IP}"
```

---

## 2 — SSH into the Instance

```bash
ssh -i k8s-agent-key.pem ubuntu@<PUBLIC_IP>
```

Replace `<PUBLIC_IP>` with the value printed by the script, or source it from the saved state file:

```bash
source .k8s-agent-state
ssh -i k8s-agent-key.pem ubuntu@"$PUBLIC_IP"
```

> **Note:** Allow 30–60 seconds after the script completes before SSHing — the instance's SSH daemon may still be initialising.

---

## 3 — Install Runtime Dependencies on the Instance

Once connected, install Java 21 and any other required tools:

```bash
sudo apt-get update -y
sudo apt-get install -y openjdk-21-jre-headless curl unzip

# Verify Java
java -version
```

---

## 4 — Build and Transfer the Application JAR

On your **local machine**, build the fat JAR:

```bash
mvn clean package -DskipTests
```

This produces `target/k8s-ai-operator-0.0.1-SNAPSHOT.jar`.

Copy it to the instance:

```bash
source .k8s-agent-state
scp -i k8s-agent-key.pem \
  target/k8s-ai-operator-0.0.1-SNAPSHOT.jar \
  ubuntu@"$PUBLIC_IP":~/app.jar
```

---

## 5 — Configure AWS Credentials on the Instance

The application needs access to Bedrock, DynamoDB, and CloudWatch. The recommended approach is to attach an **IAM instance profile** to the EC2 instance — the SDK will pick up credentials automatically via the instance metadata service (IMDS).

**Minimum IAM permissions required (see README for full policy):**

```
bedrock:InvokeModel         on anthropic.claude-3-sonnet
dynamodb:PutItem            on K8sAgentExecutions
dynamodb:GetItem            on K8sAgentExecutions
cloudwatch:PutMetricData    on K8sAiOperator/Execution
logs:CreateLogGroup
logs:CreateLogStream
logs:PutLogEvents
```

If an instance profile is not available, you can export credentials manually on the instance (not recommended for production):

```bash
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
export AWS_DEFAULT_REGION=us-east-1
```

---

## 6 — Run the Application

On the instance:

```bash
java -jar ~/app.jar \
  --server.port=8080 \
  --aws.region=us-east-1
```

The service will be reachable at:

```
http://<PUBLIC_IP>:8080/k8s/execute
```

To keep it running after your SSH session ends, use `nohup` or `screen`:

```bash
nohup java -jar ~/app.jar --server.port=8080 > ~/app.log 2>&1 &
echo $! > ~/app.pid
```

Check logs with:

```bash
tail -f ~/app.log
```

---

## 7 — Verify the Deployment

From your local machine, send a test request:

```bash
source .k8s-agent-state
curl -s -X POST "http://${PUBLIC_IP}:8080/k8s/execute" \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-001","user_prompt":"Show me the pods in namespace production"}' | jq .
```

Expected shape of a successful response:

```json
{
  "request_id": "test-001",
  "command": {
    "verb": "get",
    "resource": "pods",
    "namespace": "production"
  },
  "result": "...",
  "allowed": true
}
```

---

## 8 — Teardown

To avoid ongoing charges, terminate the instance and delete the security group when finished:

```bash
source .k8s-agent-state

# Terminate instance
aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" --region "$REGION"
aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID" --region "$REGION"

# Delete security group
aws ec2 delete-security-group --group-id "$SG_ID" --region "$REGION"

# Delete key pair (remote)
aws ec2 delete-key-pair --key-name k8s-agent-key --region "$REGION"

# Remove local artefacts
rm -f k8s-agent-key.pem .k8s-agent-state
```

---

## Security Reminders

- Port 22 is restricted to **your IP only** at provision time. If your IP changes, update the ingress rule:
  ```bash
  NEW_IP=$(curl -s https://checkip.amazonaws.com)
  aws ec2 authorize-security-group-ingress \
    --group-id "$SG_ID" --region us-east-1 \
    --protocol tcp --port 22 --cidr "${NEW_IP}/32"
  ```
- The `.pem` key file grants full SSH access — keep it out of version control (add `*.pem` to `.gitignore`).
- The `.k8s-agent-state` file contains the public IP and instance ID — also keep it out of version control.
- For production workloads, place the instance in a VPC private subnet behind a load balancer and remove the direct public-IP exposure.

