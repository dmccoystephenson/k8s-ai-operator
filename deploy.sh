#!/usr/bin/env bash
# deploy.sh — Build, push, and deploy k8s-ai-operator to EKS
# Prerequisites: AWS CLI, Docker, kubectl, Maven, jq
# Run from the project root after setup-eks.sh has completed.
set -euo pipefail

REGION="us-east-2"
CLUSTER_NAME="k8s-ai-operator-test"
REPO_NAME="k8s-ai-operator"
NAMESPACE="k8s-ai-operator"
DYNAMODB_TABLE="K8sAgentExecutions"

# ── 1. Resolve AWS account ID ─────────────────────────────────────────────────
echo "==> Verifying AWS credentials..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "    Account: $ACCOUNT_ID"
ECR_URI="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com/${REPO_NAME}"

# ── 2. Ensure Docker is installed and running ─────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo "==> Docker not found — installing..."
  if command -v dnf &>/dev/null; then
    sudo dnf install -y docker
  elif command -v yum &>/dev/null; then
    sudo yum install -y docker
  elif command -v apt-get &>/dev/null; then
    sudo apt-get update -y && sudo apt-get install -y docker.io
  else
    echo "ERROR: Cannot detect package manager. Install Docker manually: https://docs.docker.com/engine/install/"
    exit 1
  fi
fi

if ! sudo docker info &>/dev/null; then
  echo "==> Starting Docker daemon..."
  sudo systemctl start docker
fi
echo "==> Building application JAR..."
./mvnw clean package -DskipTests -q
echo "    JAR built: $(ls target/*.jar)"

# ── 3. Create ECR repository (idempotent) ─────────────────────────────────────
echo "==> Ensuring ECR repository exists..."
aws ecr describe-repositories --repository-names "$REPO_NAME" --region "$REGION" &>/dev/null \
  || aws ecr create-repository --repository-name "$REPO_NAME" --region "$REGION" \
       --image-scanning-configuration scanOnPush=true --output text --query 'repository.repositoryUri'
echo "    ECR URI: $ECR_URI"

# ── 4. Build and push Docker image ────────────────────────────────────────────
echo "==> Authenticating Docker with ECR..."
aws ecr get-login-password --region "$REGION" \
  | sudo docker login --username AWS --password-stdin "${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"

echo "==> Building Docker image..."
sudo docker build -t "${REPO_NAME}:latest" .

echo "==> Pushing image to ECR..."
sudo docker tag "${REPO_NAME}:latest" "${ECR_URI}:latest"
sudo docker push "${ECR_URI}:latest"
echo "    Image pushed: ${ECR_URI}:latest"

# ── 5. Create DynamoDB table (idempotent) ─────────────────────────────────────
echo "==> Ensuring DynamoDB table exists..."
aws dynamodb describe-table --table-name "$DYNAMODB_TABLE" --region "$REGION" &>/dev/null \
  || aws dynamodb create-table \
       --table-name "$DYNAMODB_TABLE" \
       --attribute-definitions AttributeName=request_id,AttributeType=S \
       --key-schema AttributeName=request_id,KeyType=HASH \
       --billing-mode PAY_PER_REQUEST \
       --region "$REGION" \
       --output text --query 'TableDescription.TableName'
echo "    DynamoDB table: $DYNAMODB_TABLE"

# ── 6. Attach Bedrock + DynamoDB + CloudWatch permissions to node role ─────────
echo "==> Attaching IAM policies to EKS node role..."
NODEGROUP=$(aws eks list-nodegroups \
  --cluster-name "$CLUSTER_NAME" \
  --region "$REGION" \
  --query 'nodegroups[0]' \
  --output text)

NODE_ROLE=$(aws eks describe-nodegroup \
  --cluster-name "$CLUSTER_NAME" \
  --nodegroup-name "$NODEGROUP" \
  --region "$REGION" \
  --query 'nodegroup.nodeRole' \
  --output text | awk -F'/' '{print $NF}')

echo "    Nodegroup: $NODEGROUP  →  Role: $NODE_ROLE"

for POLICY in \
  arn:aws:iam::aws:policy/AmazonBedrockFullAccess \
  arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess \
  arn:aws:iam::aws:policy/CloudWatchFullAccess; do
  aws iam attach-role-policy --role-name "$NODE_ROLE" --policy-arn "$POLICY" 2>/dev/null || true
done
echo "    IAM policies attached to role: $NODE_ROLE"

# ── 7. Point kubectl at the cluster ──────────────────────────────────────────
echo "==> Updating kubeconfig..."
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$REGION"

# ── 8. Patch the manifest with the real ECR URI and deploy ───────────────────
echo "==> Deploying to EKS..."
sed "s|ACCOUNT_ID.dkr.ecr.us-east-2.amazonaws.com|${ECR_URI%:*}|g" \
  k8s-operator-manifest.yaml | kubectl apply -f -

# ── 9. Wait for rollout ───────────────────────────────────────────────────────
echo "==> Waiting for rollout to complete..."
kubectl rollout status deployment/k8s-ai-operator -n "$NAMESPACE" --timeout=120s

# ── 10. Print the public endpoint ────────────────────────────────────────────
echo ""
echo "==> Fetching service endpoint (may take ~60s for LoadBalancer to provision)..."
for i in $(seq 1 12); do
  HOSTNAME=$(kubectl get svc k8s-ai-operator -n "$NAMESPACE" \
    -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)
  if [[ -n "$HOSTNAME" ]]; then
    echo ""
    echo "✅  Deployment complete!"
    echo "    Endpoint: http://${HOSTNAME}/k8s/execute"
    echo ""
    echo "    Test with:"
    echo "    curl -s -X POST http://${HOSTNAME}/k8s/execute \\"
    echo "      -H 'Content-Type: application/json' \\"
    echo "      -d '{\"request_id\":\"test-001\",\"user_prompt\":\"Show me the pods in namespace production\"}' | jq ."
    exit 0
  fi
  echo "    Waiting for LoadBalancer IP... (${i}/12)"
  sleep 10
done

echo "⚠️  LoadBalancer hostname not yet available. Check status with:"
echo "    kubectl get svc k8s-ai-operator -n $NAMESPACE"

