variable "environment" {
  description = "Deployment environment (dev, prod, etc.)"
  type        = string
}

variable "cluster_name" {
  description = "Name of the EKS cluster (used to name the IAM role and policy)"
  type        = string
}

variable "oidc_provider_arn" {
  description = "ARN of the IAM OIDC provider for the cluster (e.g. aws_iam_openid_connect_provider.main.arn)"
  type        = string
}

variable "oidc_provider_url" {
  description = "URL of the cluster's OIDC issuer without the https:// prefix (e.g. oidc.eks.us-east-2.amazonaws.com/id/EXAMPLED539D4633E53DE1B716D3041E)"
  type        = string
}

variable "aws_region" {
  description = "AWS region where Bedrock and DynamoDB resources reside"
  type        = string
}

variable "bedrock_model_id" {
  description = "Bedrock foundation model ID the operator is allowed to invoke"
  type        = string
}

variable "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table the operator is allowed to read/write"
  type        = string
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace the operator is allowed to publish to"
  type        = string
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
