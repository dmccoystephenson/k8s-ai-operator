output "table_arn" {
  description = "ARN of the DynamoDB audit table"
  value       = aws_dynamodb_table.audit.arn
}

output "table_name" {
  description = "Name of the DynamoDB audit table"
  value       = aws_dynamodb_table.audit.name
}
