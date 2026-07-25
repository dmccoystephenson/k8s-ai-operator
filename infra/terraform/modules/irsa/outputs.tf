output "irsa_role_arn" {
  description = "ARN of the IRSA role — annotate the operator ServiceAccount with eks.amazonaws.com/role-arn=<this value>"
  value       = aws_iam_role.irsa_operator.arn
}

output "irsa_role_name" {
  description = "Name of the IRSA IAM role"
  value       = aws_iam_role.irsa_operator.name
}
