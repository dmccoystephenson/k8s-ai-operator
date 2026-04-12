variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
}

variable "node_instance_type" {
  description = "EC2 instance type for worker nodes"
  type        = string
}

variable "node_desired_count" {
  description = "Desired number of worker nodes"
  type        = number
}

variable "node_min_count" {
  description = "Minimum number of worker nodes"
  type        = number
}

variable "node_max_count" {
  description = "Maximum number of worker nodes"
  type        = number
}

variable "private_subnet_ids" {
  description = "IDs of the private subnets for worker nodes"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "IDs of the public subnets for load balancers"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID where the cluster resides"
  type        = string
}

variable "account_id" {
  description = "AWS account ID (used for IRSA trust policy)"
  type        = string
}

variable "aws_region" {
  description = "AWS region (used for IRSA policy resources)"
  type        = string
}

variable "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table (granted to the operator IRSA role)"
  type        = string
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace (scopes the PutMetricData permission)"
  type        = string
}

variable "bedrock_model_id" {
  description = "Bedrock model ID granted to the operator IRSA role"
  type        = string
}

variable "operator_namespace" {
  description = "Kubernetes namespace the operator is deployed into"
  type        = string
  default     = "k8s-ai-operator"
}

variable "operator_service_account_name" {
  description = "Kubernetes ServiceAccount name used by the operator pod"
  type        = string
  default     = "k8s-ai-operator"
}

variable "endpoint_public_access" {
  description = "Whether the EKS cluster API server endpoint is publicly accessible. Set to false in production to restrict access to within the VPC."
  type        = bool
  default     = true
}
