#!/usr/bin/env bash
# setup-minikube.sh — Start a local Minikube cluster for k8s-ai-operator development
set -euo pipefail

# ── Verify required tools ─────────────────────────────────────────────────────
for tool in minikube kubectl; do
  if ! command -v "$tool" &>/dev/null; then
    echo "ERROR: '$tool' is not installed or not on PATH."
    echo "Install guide:"
    echo "  minikube: https://minikube.sigs.k8s.io/docs/start/"
    echo "  kubectl:  https://kubernetes.io/docs/tasks/tools/"
    exit 1
  fi
done

# ── Start Minikube ────────────────────────────────────────────────────────────
if minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
  echo "Minikube is already running."
else
  echo "Starting Minikube..."
  minikube start
fi

# ── Verify kubectl context ────────────────────────────────────────────────────
CURRENT_CONTEXT=$(kubectl config current-context)
echo "Current kubectl context: $CURRENT_CONTEXT"

if [[ "$CURRENT_CONTEXT" != "minikube" ]]; then
  echo "Switching kubectl context to minikube..."
  kubectl config use-context minikube
fi

echo "kubectl context is set to: $(kubectl config current-context)"

# ── Create test namespaces ────────────────────────────────────────────────────
for ns in production staging; do
  kubectl get namespace "$ns" &>/dev/null \
    || kubectl create namespace "$ns"
  echo "Namespace '$ns' is ready."
done

# ── Deploy sample workloads for operator testing ──────────────────────────────
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

echo ""
echo "Minikube cluster is ready."
echo "Context: $(kubectl config current-context)"
echo ""
echo "Next step — run the application with the local profile:"
echo "  ANTHROPIC_API_KEY=<your-key> ./mvnw spring-boot:run -Dspring-boot.run.profiles=local"
