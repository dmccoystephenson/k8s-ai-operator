variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "jar_path" {
  description = "Path to the application JAR file"
  type        = string
}

variable "memory_mb" {
  description = "Lambda memory allocation in MB"
  type        = number
}

variable "timeout_seconds" {
  description = "Lambda timeout in seconds"
  type        = number
}

variable "execution_role_arn" {
  description = "ARN of the IAM execution role for the Lambda function"
  type        = string
}

variable "bedrock_model_id" {
  description = "Bedrock model ID"
  type        = string
}

variable "bedrock_max_tokens" {
  description = "Maximum tokens for Bedrock responses"
  type        = number
}

variable "dynamodb_table_name" {
  description = "DynamoDB audit table name"
  type        = string
}

variable "cloudwatch_namespace" {
  description = "CloudWatch metrics namespace"
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
}
