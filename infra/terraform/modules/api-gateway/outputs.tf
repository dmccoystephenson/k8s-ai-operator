output "api_endpoint" {
  description = "Base URL for the API Gateway stage"
  value       = "${aws_api_gateway_stage.prod.invoke_url}/k8s/execute"
}

output "rest_api_id" {
  description = "ID of the REST API"
  value       = aws_api_gateway_rest_api.operator.id
}
