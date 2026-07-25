variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "account_id" {
  description = "AWS account ID"
  type        = string
}

variable "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table"
  type        = string
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace"
  type        = string
}

variable "bedrock_model_id" {
  description = "Bedrock model ID (used to scope the InvokeModel permission)"
  type        = string
}
