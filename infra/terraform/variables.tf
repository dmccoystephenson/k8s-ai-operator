variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "us-east-2"
}

variable "environment" {
  description = "Deployment environment (dev, prod, etc.)"
  type        = string
  default     = "dev"
}

variable "lambda_jar_path" {
  description = "Path to the built application JAR for the Lambda function"
  type        = string
  default     = "../../target/k8s-ai-operator-0.0.1-SNAPSHOT.jar"
}

variable "lambda_memory_mb" {
  description = "Memory allocated to the Lambda function (MB)"
  type        = number
  default     = 512
}

variable "lambda_timeout_seconds" {
  description = "Lambda function timeout in seconds"
  type        = number
  default     = 30
}

variable "bedrock_model_id" {
  description = "Amazon Bedrock foundation model ID used for command parsing"
  type        = string
  default     = "anthropic.claude-3-sonnet-20240229-v1:0"
}

variable "bedrock_max_tokens" {
  description = "Maximum number of tokens for Bedrock responses"
  type        = number
  default     = 512
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB table used for audit logging"
  type        = string
  default     = "K8sAgentExecutions"
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace"
  type        = string
  default     = "K8sAiOperator/Execution"
}

variable "log_retention_days" {
  description = "Number of days to retain CloudWatch log group entries"
  type        = number
  default     = 14
}

# ── EKS variables ─────────────────────────────────────────────────────────────

variable "eks_cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "k8s-ai-operator"
}

variable "eks_kubernetes_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
  default     = "1.29"
}

variable "eks_node_instance_type" {
  description = "EC2 instance type for EKS worker nodes"
  type        = string
  default     = "t3.medium"
}

variable "eks_node_desired_count" {
  description = "Desired number of EKS worker nodes"
  type        = number
  default     = 2
}

variable "eks_node_min_count" {
  description = "Minimum number of EKS worker nodes"
  type        = number
  default     = 1
}

variable "eks_node_max_count" {
  description = "Maximum number of EKS worker nodes"
  type        = number
  default     = 3
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC created for the EKS cluster"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for the public subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for the private subnets (one per AZ)"
  type        = list(string)
  default     = ["10.0.3.0/24", "10.0.4.0/24"]
}
