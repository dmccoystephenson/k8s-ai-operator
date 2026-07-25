output "function_arn" {
  description = "ARN of the Lambda function"
  value       = aws_lambda_function.operator.arn
}

output "function_name" {
  description = "Name of the Lambda function"
  value       = aws_lambda_function.operator.function_name
}

output "invoke_arn" {
  description = "Invoke ARN used by API Gateway to call the Lambda function"
  value       = aws_lambda_function.operator.invoke_arn
}

output "log_group_name" {
  description = "Name of the CloudWatch log group for the Lambda function"
  value       = aws_cloudwatch_log_group.lambda.name
}
