variable "aws_region" {
  description = "AWS region where the cluster and AWS resources reside"
  type        = string
  default     = "us-east-2"
}

variable "environment" {
  description = "Deployment environment label (dev, prod, etc.) — used in resource names"
  type        = string
  default     = "dev"
}

variable "cluster_name" {
  description = "Name of the existing EKS cluster to deploy the operator into"
  type        = string
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

variable "operator_namespace" {
  description = "Kubernetes namespace the operator pod runs in"
  type        = string
  default     = "k8s-ai-operator"
}

variable "operator_service_account_name" {
  description = "Kubernetes ServiceAccount name used by the operator pod"
  type        = string
  default     = "k8s-ai-operator"
}
