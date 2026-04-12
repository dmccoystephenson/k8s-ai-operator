resource "aws_api_gateway_rest_api" "operator" {
  name        = "k8s-ai-operator-api-${var.environment}"
  description = "API Gateway for k8s-ai-operator (${var.environment})"

  endpoint_configuration {
    types = ["REGIONAL"]
  }
}

# ── /k8s resource ─────────────────────────────────────────────────────────────

resource "aws_api_gateway_resource" "k8s" {
  rest_api_id = aws_api_gateway_rest_api.operator.id
  parent_id   = aws_api_gateway_rest_api.operator.root_resource_id
  path_part   = "k8s"
}

# ── /k8s/execute resource ─────────────────────────────────────────────────────

resource "aws_api_gateway_resource" "execute" {
  rest_api_id = aws_api_gateway_rest_api.operator.id
  parent_id   = aws_api_gateway_resource.k8s.id
  path_part   = "execute"
}

# ── POST /k8s/execute ─────────────────────────────────────────────────────────

resource "aws_api_gateway_method" "post_execute" {
  rest_api_id   = aws_api_gateway_rest_api.operator.id
  resource_id   = aws_api_gateway_resource.execute.id
  http_method   = "POST"
  authorization = "AWS_IAM"
}

resource "aws_api_gateway_integration" "post_execute_lambda" {
  rest_api_id             = aws_api_gateway_rest_api.operator.id
  resource_id             = aws_api_gateway_resource.execute.id
  http_method             = aws_api_gateway_method.post_execute.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = "arn:aws:apigateway:${var.aws_region}:lambda:path/2015-03-31/functions/${var.lambda_function_arn}/invocations"
}

# ── Deployment and stage ──────────────────────────────────────────────────────

resource "aws_api_gateway_deployment" "operator" {
  rest_api_id = aws_api_gateway_rest_api.operator.id

  triggers = {
    redeployment = sha1(jsonencode({
      rest_api = {
        name        = aws_api_gateway_rest_api.operator.name
        description = aws_api_gateway_rest_api.operator.description
      }
      resources = {
        k8s = {
          parent_id = aws_api_gateway_rest_api.operator.root_resource_id
          path_part = aws_api_gateway_resource.k8s.path_part
        }
        execute = {
          parent_id = aws_api_gateway_resource.k8s.id
          path_part = aws_api_gateway_resource.execute.path_part
        }
      }
      method = {
        http_method   = aws_api_gateway_method.post_execute.http_method
        authorization = aws_api_gateway_method.post_execute.authorization
      }
      integration = {
        http_method             = aws_api_gateway_integration.post_execute_lambda.http_method
        integration_http_method = aws_api_gateway_integration.post_execute_lambda.integration_http_method
        type                    = aws_api_gateway_integration.post_execute_lambda.type
        uri                     = aws_api_gateway_integration.post_execute_lambda.uri
      }
    }))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "environment" {
  deployment_id = aws_api_gateway_deployment.operator.id
  rest_api_id   = aws_api_gateway_rest_api.operator.id
  stage_name    = var.environment
}

# ── Lambda permission for API Gateway ────────────────────────────────────────

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowAPIGatewayInvoke-${var.environment}"
  action        = "lambda:InvokeFunction"
  function_name = var.lambda_function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.operator.execution_arn}/${aws_api_gateway_stage.environment.stage_name}/${aws_api_gateway_method.post_execute.http_method}/k8s/execute"
}
