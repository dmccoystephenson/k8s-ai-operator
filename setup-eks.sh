#!/usr/bin/env bash
# setup-eks.sh — Provision an EKS test cluster for k8s-ai-operator
set -euo pipefail

REGION="us-east-2"
CLUSTER_NAME="k8s-ai-operator-test"
NODE_TYPE="t3.medium"
NODE_COUNT=2
K8S_VERSION="1.29"

# ── Verify credentials ────────────────────────────────────────────────────────
aws sts get-caller-identity --region "$REGION"

# ── Install missing tools ─────────────────────────────────────────────────────
if ! command -v eksctl &>/dev/null; then
  echo "eksctl not found — installing..."
  EKSCTL_VERSION=$(curl -s https://api.github.com/repos/eksctl-io/eksctl/releases/latest \
    | grep '"tag_name"' | cut -d'"' -f4)
  curl -sL "https://github.com/eksctl-io/eksctl/releases/download/${EKSCTL_VERSION}/eksctl_Linux_amd64.tar.gz" \
    | tar -xz -C /tmp
  sudo mv /tmp/eksctl /usr/local/bin/eksctl
  echo "eksctl ${EKSCTL_VERSION} installed."
fi

if ! command -v kubectl &>/dev/null; then
  echo "kubectl not found — installing..."
  KUBECTL_VERSION=$(curl -sL https://dl.k8s.io/release/stable.txt)
  curl -sLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
  sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
  rm -f kubectl
  echo "kubectl ${KUBECTL_VERSION} installed."
fi

# ── Verify required tools ─────────────────────────────────────────────────────
for tool in eksctl kubectl aws; do
  if ! command -v "$tool" &>/dev/null; then
    echo "ERROR: '$tool' is not installed or not on PATH after auto-install attempt"
    exit 1
  fi
done

# ── Create the EKS cluster ────────────────────────────────────────────────────
# eksctl creates the VPC, subnets, IAM roles, managed node group, and
# kubeconfig entry in a single CloudFormation stack.
eksctl create cluster \
  --name "$CLUSTER_NAME" \
  --region "$REGION" \
  --version "$K8S_VERSION" \
  --nodegroup-name standard-workers \
  --node-type "$NODE_TYPE" \
  --nodes "$NODE_COUNT" \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed \
  --with-oidc \
  --tags "Project=k8s-ai-operator,Environment=test"

# ── Update local kubeconfig ───────────────────────────────────────────────────
aws eks update-kubeconfig \
  --name "$CLUSTER_NAME" \
  --region "$REGION"

# ── Create test namespaces ────────────────────────────────────────────────────
for ns in production staging default; do
  kubectl get namespace "$ns" &>/dev/null \
    || kubectl create namespace "$ns"
done

# ── Deploy sample workloads for operator testing ──────────────────────────────
# These give the k8s-ai-operator realistic targets for get/apply commands.

kubectl apply -f - <<'MANIFEST'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-deployment
  namespace: production
spec:
  replicas: 2
  selector:
    matchLabels:
      app: sample-app
  template:
    metadata:
      labels:
        app: sample-app
    spec:
      containers:
        - name: nginx
          image: nginx:1.25-alpine
          ports:
            - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: app-svc
  namespace: production
spec:
  selector:
    app: sample-app
  ports:
    - port: 80
      targetPort: 80
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app-deployment
  namespace: staging
spec:
  replicas: 1
  selector:
    matchLabels:
      app: sample-app
  template:
    metadata:
      labels:
        app: sample-app
    spec:
      containers:
        - name: nginx
          image: nginx:1.25-alpine
          ports:
            - containerPort: 80
MANIFEST

# ── Verify cluster is healthy ─────────────────────────────────────────────────
echo ""
echo "Cluster nodes:"
kubectl get nodes -o wide

echo ""
echo "Workloads in production:"
kubectl get pods,deployments,services -n production

# ── Save state ────────────────────────────────────────────────────────────────
CLUSTER_ENDPOINT=$(aws eks describe-cluster \
  --name "$CLUSTER_NAME" \
  --region "$REGION" \
  --query 'cluster.endpoint' \
  --output text)

cat > .k8s-cluster-state <<EOF
CLUSTER_NAME=$CLUSTER_NAME
REGION=$REGION
K8S_VERSION=$K8S_VERSION
CLUSTER_ENDPOINT=$CLUSTER_ENDPOINT
EOF

echo ""
echo "Cluster ready — context set to: $(kubectl config current-context)"
echo "Endpoint: $CLUSTER_ENDPOINT"

