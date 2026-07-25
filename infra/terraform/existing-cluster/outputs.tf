output "irsa_role_arn" {
  description = "ARN of the IRSA role — set as the eks.amazonaws.com/role-arn ServiceAccount annotation when installing the Helm chart"
  value       = module.irsa.irsa_role_arn
}

output "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table"
  value       = module.dynamodb.table_arn
}

output "dynamodb_table_name" {
  description = "Name of the DynamoDB audit table"
  value       = module.dynamodb.table_name
}

output "oidc_provider_arn" {
  description = "ARN of the IAM OIDC provider registered for the cluster"
  value       = aws_iam_openid_connect_provider.oidc.arn
}
