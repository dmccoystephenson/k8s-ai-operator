# prod.tfvars — Production environment overrides
# Usage: terraform apply -var-file=prod.tfvars

aws_region    = "us-east-2"
environment   = "prod"

# Lambda
lambda_memory_mb       = 1024
lambda_timeout_seconds = 30

# Bedrock
bedrock_model_id   = "anthropic.claude-3-sonnet-20240229-v1:0"
bedrock_max_tokens = 512

# DynamoDB
dynamodb_table_name = "K8sAgentExecutions"

# CloudWatch
cloudwatch_namespace = "K8sAiOperator/Execution"
log_retention_days   = 30

# EKS
eks_cluster_name       = "k8s-ai-operator"
eks_kubernetes_version = "1.29"
eks_node_instance_type = "t3.medium"
eks_node_desired_count = 2
eks_node_min_count     = 2
eks_node_max_count     = 5
eks_endpoint_public_access = false

# Networking
vpc_cidr             = "10.1.0.0/16"
public_subnet_cidrs  = ["10.1.1.0/24", "10.1.2.0/24"]
private_subnet_cidrs = ["10.1.3.0/24", "10.1.4.0/24"]
