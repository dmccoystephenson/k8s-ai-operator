# ── IAM user for non-IRSA clusters ───────────────────────────────────────────
# Use this module when deploying the operator to a cluster that does not support
# IAM Roles for Service Accounts (IRSA), such as a Linode Kubernetes Engine
# (LKE) cluster. The module creates a dedicated IAM user with a least-privilege
# policy, and an access key whose credentials can be stored in a Kubernetes
# Secret and injected into the pod as environment variables.
#
# For AWS EKS clusters, prefer the irsa/ module instead.

resource "aws_iam_user" "operator" {
  name = "${var.cluster_name}-operator-${var.environment}"

  tags = {
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_iam_policy" "operator_policy" {
  name        = "${var.cluster_name}-operator-policy-${var.environment}"
  description = "Least-privilege policy for k8s-ai-operator (non-IRSA clusters)"

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

resource "aws_iam_user_policy_attachment" "operator" {
  user       = aws_iam_user.operator.name
  policy_arn = aws_iam_policy.operator_policy.arn
}

resource "aws_iam_access_key" "operator" {
  user = aws_iam_user.operator.name
}
