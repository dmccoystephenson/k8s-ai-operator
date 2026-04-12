terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state backend — configure for your environment.
  # Note: Terraform does not support variable interpolation in backend blocks.
  # Replace the key path manually with your desired environment name.
  # backend "s3" {
  #   bucket         = "my-terraform-state-bucket"
  #   key            = "k8s-ai-operator-existing-cluster/dev/terraform.tfstate"
  #   region         = "us-east-2"
  #   dynamodb_table = "terraform-state-lock"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "k8s-ai-operator"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

# ── Data sources ───────────────────────────────────────────────────────────────

data "aws_caller_identity" "current" {}

# Look up the existing cluster to retrieve its OIDC issuer URL.
data "aws_eks_cluster" "target" {
  name = var.cluster_name
}

# Fetch the TLS certificate from the cluster's OIDC issuer so we can register
# the OIDC provider (skip if one already exists for this issuer).
data "tls_certificate" "oidc" {
  url = data.aws_eks_cluster.target.identity[0].oidc[0].issuer
}

# ── OIDC provider ─────────────────────────────────────────────────────────────
# Registers the existing cluster's OIDC issuer as an IAM identity provider.
# If the cluster already has a registered OIDC provider (common when using the
# eks module from infra/terraform/), import it instead of recreating:
#   terraform import aws_iam_openid_connect_provider.oidc <oidc-provider-arn>

resource "aws_iam_openid_connect_provider" "oidc" {
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]
  url             = data.aws_eks_cluster.target.identity[0].oidc[0].issuer
}

# ── DynamoDB audit table ───────────────────────────────────────────────────────

module "dynamodb" {
  source = "../modules/dynamodb"

  table_name  = var.dynamodb_table_name
  environment = var.environment
}

# ── IRSA role for the operator pod ────────────────────────────────────────────

module "irsa" {
  source = "../modules/irsa"

  environment                   = var.environment
  cluster_name                  = var.cluster_name
  oidc_provider_arn             = aws_iam_openid_connect_provider.oidc.arn
  oidc_provider_url             = aws_iam_openid_connect_provider.oidc.url
  aws_region                    = var.aws_region
  bedrock_model_id              = var.bedrock_model_id
  dynamodb_table_arn            = module.dynamodb.table_arn
  cloudwatch_namespace          = var.cloudwatch_namespace
  operator_namespace            = var.operator_namespace
  operator_service_account_name = var.operator_service_account_name
}
