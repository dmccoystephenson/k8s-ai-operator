# EKS Test Cluster Setup — k8s-ai-operator

This guide provisions a minimal Amazon EKS cluster in `us-east-2` with sample workloads that give the k8s-ai-operator realistic targets for `get` and `apply` commands during testing.

---

## Prerequisites

| Tool | Purpose | Install |
|---|---|---|
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | Credential verification, kubeconfig update | `aws configure` |
| [eksctl](https://eksctl.io/installation/) | EKS cluster provisioning (AWS-endorsed) | See link |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | Cluster interaction and manifest apply | See link |

All three must be on your `PATH` before running the script — it will exit early with a clear message if any are missing.

---

## What EKS Provides

Amazon EKS (Elastic Kubernetes Service) is AWS's managed Kubernetes offering. AWS owns and operates the control plane (API server, etcd, scheduler); you only manage the worker nodes. This makes it the natural fit for this project because:

- The existing AWS credentials chain (IAM roles, instance profiles, environment variables) works natively with EKS and `kubectl`
- No separate Kubernetes infrastructure to operate — the control plane is fully managed
- The same `us-east-2` region is used across EC2, EKS, and the Lambda deployment, keeping latency and IAM policy scope consistent

---

## 1 — Run the Provisioning Script

```bash
chmod +x setup-eks.sh
./setup-eks.sh
```

The script performs the following actions in order:

| Step | What happens |
|---|---|
| Verify credentials | Calls `aws sts get-caller-identity` to confirm the active IAM identity |
| Check tools | Exits early if `eksctl`, `kubectl`, or `aws` are not on `PATH` |
| Create cluster | Runs `eksctl create cluster` — provisions VPC, subnets, IAM roles, managed node group, and OIDC provider via CloudFormation |
| Update kubeconfig | Runs `aws eks update-kubeconfig` so `kubectl` points at the new cluster |
| Create namespaces | Ensures `production`, `staging`, and `default` namespaces exist |
| Deploy sample workloads | Applies `nginx` Deployments and a Service to `production` and `staging` |
| Verify | Prints node and workload status |
| Save state | Writes `CLUSTER_NAME`, `REGION`, `K8S_VERSION`, and `CLUSTER_ENDPOINT` to `.k8s-cluster-state` |

> **Note:** `eksctl create cluster` typically takes **10–15 minutes** — it is creating a full CloudFormation stack under the hood.

On success the script prints:

```
Cluster ready — context set to: <context-name>
Endpoint: https://<hash>.gr7.us-east-2.eks.amazonaws.com
```

---

## 2 — Cluster Configuration

| Parameter | Value |
|---|---|
| Region | `us-east-2` |
| Kubernetes version | `1.29` |
| Node type | `t3.medium` (2 vCPU, 4 GB RAM) |
| Node count | `2` (min 1, max 3) |
| Node group | Managed (AWS handles AMI updates and node replacement) |
| OIDC provider | Enabled (`--with-oidc`) — required for IAM Roles for Service Accounts |

---

## 3 — Sample Workloads

The script deploys the following resources to give the operator realistic query targets:

| Resource | Namespace | Purpose |
|---|---|---|
| `Deployment/app-deployment` | `production` | 2-replica nginx — target for `get deployments` |
| `Service/app-svc` | `production` | ClusterIP — target for `get services` |
| `Deployment/app-deployment` | `staging` | 1-replica nginx — tests namespace-scoped queries |

These match the mock output already returned by `K8sClientAdapter` in the application, so operator responses will be consistent whether running against the mock or a real cluster.

---

## 4 — Test Operator Commands Against the Cluster

With the cluster running and `kubectl` configured, send requests to the operator:

```bash
# If running locally via SAM
curl -s -X POST http://localhost:3000/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-001","user_prompt":"Show me the pods in namespace production"}' | jq .

# If running on EC2 (source EC2 state first)
source .k8s-agent-state
curl -s -X POST "http://${PUBLIC_IP}:8080/k8s/execute" \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-001","user_prompt":"Show me the pods in namespace production"}' | jq .
```

You can also query the cluster directly with `kubectl` to compare:

```bash
kubectl get pods -n production
kubectl get deployments -n production
kubectl get services -n production
```

### Verify blocked verbs are rejected

```bash
curl -s -X POST http://localhost:3000/k8s/execute \
  -H "Content-Type: application/json" \
  -d '{"request_id":"test-002","user_prompt":"Delete all pods in production"}' | jq .
# Expected: 400 with allowed=false
```

---

## 5 — Teardown

To avoid ongoing EKS charges (~$0.10/hr for the control plane plus EC2 node costs), delete the cluster when finished:

```bash
source .k8s-cluster-state

eksctl delete cluster \
  --name "$CLUSTER_NAME" \
  --region "$REGION"

rm -f .k8s-cluster-state
```

`eksctl` will delete the full CloudFormation stack including the VPC, subnets, node group, and IAM roles.

---

## IAM Requirements

The IAM identity running the script needs the following permissions:

```
eks:CreateCluster
eks:DescribeCluster
eks:DeleteCluster
ec2:CreateVpc / DescribeVpcs / DeleteVpc (and related subnet/IGW actions)
iam:CreateRole / AttachRolePolicy / PassRole
cloudformation:CreateStack / DescribeStacks / DeleteStack
```

The simplest approach for a test environment is to use an IAM user or role with `AdministratorAccess`. For a restricted setup, use the [eksctl minimum IAM policies](https://eksctl.io/usage/minimum-iam-policies/).

---

## Security Notes

- The cluster is created in a **new dedicated VPC** — it does not share a VPC with the EC2 instance from `setup-ec2.sh`
- Worker nodes are in **private subnets** by default with `eksctl`; the Kubernetes API endpoint is public but authenticated via IAM
- The OIDC provider (`--with-oidc`) is enabled so that pods can assume IAM roles directly via service accounts — useful if you later move from mock to a real Kubernetes client in `K8sClientAdapter`
- The `.k8s-cluster-state` file contains the cluster endpoint — keep it out of version control (add to `.gitignore`)

