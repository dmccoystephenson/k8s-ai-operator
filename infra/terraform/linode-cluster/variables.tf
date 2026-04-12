variable "linode_token" {
  description = "Linode API personal access token with read/write LKE permissions"
  type        = string
  sensitive   = true
}

variable "linode_region" {
  description = "Linode region in which to create the LKE cluster (e.g. us-east)"
  type        = string
  default     = "us-east"
}

variable "cluster_name" {
  description = "Base name for the LKE cluster and associated IAM resources"
  type        = string
  default     = "k8s-ai-operator"
}

variable "environment" {
  description = "Deployment environment label (dev, prod, etc.) — appended to resource names"
  type        = string
  default     = "dev"
}

variable "k8s_version" {
  description = "Kubernetes version for the LKE cluster (e.g. 1.29)"
  type        = string
  default     = "1.29"
}

variable "node_type" {
  description = "Linode instance type for worker nodes (e.g. g6-standard-2)"
  type        = string
  default     = "g6-standard-2"
}

variable "node_count" {
  description = "Number of worker nodes in the LKE node pool"
  type        = number
  default     = 2
}

# ── AWS variables ──────────────────────────────────────────────────────────────

variable "aws_region" {
  description = "AWS region where DynamoDB, Bedrock, and CloudWatch resources reside"
  type        = string
  default     = "us-east-2"
}

variable "bedrock_model_id" {
  description = "Amazon Bedrock foundation model ID the operator is allowed to invoke"
  type        = string
  default     = "anthropic.claude-3-sonnet-20240229-v1:0"
}

variable "dynamodb_table_name" {
  description = "Name of the DynamoDB audit table (created if it does not yet exist)"
  type        = string
  default     = "K8sAgentExecutions"
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace the operator publishes to"
  type        = string
  default     = "K8sAiOperator/Execution"
}
