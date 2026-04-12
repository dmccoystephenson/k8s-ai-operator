# ── IRSA role for the operator pod ────────────────────────────────────────────
# Pods annotated with this role ARN can call Bedrock, DynamoDB, and CloudWatch
# without any node-level credentials (recommended over node role permissions).
#
# This module can be used with any EKS cluster — whether provisioned by
# infra/terraform/main.tf or an external configuration — as long as the cluster
# has an IAM OIDC provider registered.

locals {
  # Strip the https:// prefix so we can use the bare host in IAM condition keys.
  oidc_host = replace(var.oidc_provider_url, "https://", "")
}

resource "aws_iam_role" "irsa_operator" {
  name = "${var.cluster_name}-irsa-operator-${var.environment}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = var.oidc_provider_arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_host}:sub" = "system:serviceaccount:${var.operator_namespace}:${var.operator_service_account_name}"
          "${local.oidc_host}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_policy" "irsa_operator_policy" {
  name        = "${var.cluster_name}-irsa-operator-policy-${var.environment}"
  description = "Least-privilege policy for k8s-ai-operator pods (IRSA)"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "BedrockInvokeModel"
        Effect = "Allow"
        Action = ["bedrock:InvokeModel"]
        Resource = [
          "arn:aws:bedrock:${var.aws_region}::foundation-model/${var.bedrock_model_id}"
        ]
      },
      {
        Sid    = "DynamoDbAudit"
        Effect = "Allow"
        Action = [
          "dynamodb:PutItem",
          "dynamodb:GetItem"
        ]
        Resource = [var.dynamodb_table_arn]
      },
      {
        Sid      = "CloudWatchMetrics"
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = ["*"]
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = var.cloudwatch_namespace
          }
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "irsa_operator" {
  role       = aws_iam_role.irsa_operator.name
  policy_arn = aws_iam_policy.irsa_operator_policy.arn
}
