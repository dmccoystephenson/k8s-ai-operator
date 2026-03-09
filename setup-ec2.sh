#!/usr/bin/env bash
# setup-ec2.sh — Provision EC2 instance for k8s-ai-operator
set -euo pipefail

REGION="us-east-2"
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

