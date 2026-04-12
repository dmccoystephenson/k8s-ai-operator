output "access_key_id" {
  description = "AWS access key ID for the operator IAM user — store in a Kubernetes Secret"
  value       = aws_iam_access_key.operator.id
  sensitive   = true
}

output "secret_access_key" {
  description = "AWS secret access key for the operator IAM user — store in a Kubernetes Secret"
  value       = aws_iam_access_key.operator.secret
  sensitive   = true
}

output "iam_user_name" {
  description = "Name of the IAM user created for the operator"
  value       = aws_iam_user.operator.name
}

output "iam_user_arn" {
  description = "ARN of the IAM user created for the operator"
  value       = aws_iam_user.operator.arn
}
