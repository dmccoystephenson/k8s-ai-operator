resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/k8s-ai-operator-${var.environment}"
  retention_in_days = var.log_retention_days
}

resource "aws_lambda_function" "operator" {
  function_name = "k8s-ai-operator-${var.environment}"
  description   = "k8s-ai-operator — AI-powered Kubernetes operator (${var.environment})"
  role          = var.execution_role_arn

  filename         = var.jar_path
  source_code_hash = filebase64sha256(var.jar_path)
  handler          = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
  runtime          = "java21"
  architectures    = ["x86_64"]

  memory_size = var.memory_mb
  timeout     = var.timeout_seconds

  environment {
    variables = {
      AWS_REGION           = var.aws_region
      BEDROCK_MODEL_ID     = var.bedrock_model_id
      BEDROCK_MAX_TOKENS   = tostring(var.bedrock_max_tokens)
      DYNAMODB_TABLE       = var.dynamodb_table_name
      CLOUDWATCH_NAMESPACE = var.cloudwatch_namespace
      SPRING_PROFILES_ACTIVE = "lambda"
    }
  }

  depends_on = [aws_cloudwatch_log_group.lambda]
}
