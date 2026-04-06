# dev.tfvars — Development environment overrides
# Usage: terraform apply -var-file=dev.tfvars

aws_region    = "us-east-2"
environment   = "dev"

# Lambda
lambda_memory_mb       = 512
lambda_timeout_seconds = 30

# Bedrock
bedrock_model_id   = "anthropic.claude-3-sonnet-20240229-v1:0"
bedrock_max_tokens = 512

# DynamoDB
dynamodb_table_name = "K8sAgentExecutions"

# CloudWatch
cloudwatch_namespace = "K8sAiOperator/Execution"
log_retention_days   = 7

# EKS
eks_cluster_name       = "k8s-ai-operator"
eks_kubernetes_version = "1.29"
eks_node_instance_type = "t3.medium"
eks_node_desired_count = 1
eks_node_min_count     = 1
eks_node_max_count     = 2

# Networking
vpc_cidr             = "10.0.0.0/16"
public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
private_subnet_cidrs = ["10.0.3.0/24", "10.0.4.0/24"]
