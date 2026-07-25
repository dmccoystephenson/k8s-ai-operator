variable "environment" {
  description = "Deployment environment label (dev, prod, etc.) — used in resource names"
  type        = string
}

variable "cluster_name" {
  description = "Name of the Kubernetes cluster — used as a prefix for IAM resource names"
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
