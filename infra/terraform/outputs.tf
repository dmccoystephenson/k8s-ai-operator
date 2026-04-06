output "api_endpoint" {
  description = "API Gateway endpoint URL for POST /k8s/execute"
  value       = module.api_gateway.api_endpoint
}

output "lambda_function_arn" {
  description = "ARN of the k8s-ai-operator Lambda function"
  value       = module.lambda.function_arn
}

output "lambda_function_name" {
  description = "Name of the k8s-ai-operator Lambda function"
  value       = module.lambda.function_name
}

output "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table"
  value       = module.dynamodb.table_arn
}

output "dynamodb_table_name" {
  description = "Name of the DynamoDB audit table"
  value       = module.dynamodb.table_name
}

output "cloudwatch_log_group_name" {
  description = "Name of the CloudWatch log group for the Lambda function"
  value       = module.lambda.log_group_name
}

output "lambda_execution_role_arn" {
  description = "ARN of the IAM execution role attached to the Lambda function"
  value       = module.iam.execution_role_arn
}

output "eks_cluster_name" {
  description = "Name of the EKS cluster"
  value       = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  description = "Endpoint URL of the EKS cluster API server"
  value       = module.eks.cluster_endpoint
}

output "eks_cluster_certificate_authority" {
  description = "Base64-encoded certificate authority data for the EKS cluster"
  value       = module.eks.cluster_certificate_authority
  sensitive   = true
}

output "eks_irsa_role_arn" {
  description = "ARN of the IRSA role — set as the eks.amazonaws.com/role-arn ServiceAccount annotation when installing the Helm chart"
  value       = module.eks.irsa_role_arn
}
