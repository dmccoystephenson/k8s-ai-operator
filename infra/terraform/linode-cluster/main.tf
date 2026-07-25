terraform {
  required_version = ">= 1.6"

  required_providers {
    linode = {
      source  = "linode/linode"
      version = "~> 2.0"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state backend — configure for your environment.
  # Note: Terraform does not support variable interpolation in backend blocks.
  # Replace the key path manually with your desired environment name.
  # backend "s3" {
  #   bucket         = "my-terraform-state-bucket"
  #   key            = "k8s-ai-operator-linode/dev/terraform.tfstate"
  #   region         = "us-east-2"
  #   dynamodb_table = "terraform-state-lock"
  #   encrypt        = true
  # }
}

provider "linode" {
  token = var.linode_token
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

# ── Linode Kubernetes Engine (LKE) cluster ────────────────────────────────────

resource "linode_lke_cluster" "main" {
  label       = "${var.cluster_name}-${var.environment}"
  region      = var.linode_region
  k8s_version = var.k8s_version

  pool {
    type  = var.node_type
    count = var.node_count
  }

  tags = [var.environment, "k8s-ai-operator"]
}

# ── DynamoDB audit table ───────────────────────────────────────────────────────

module "dynamodb" {
  source = "../modules/dynamodb"

  table_name  = var.dynamodb_table_name
  environment = var.environment
}

# ── IAM user for operator pods ────────────────────────────────────────────────
# LKE clusters do not support AWS IRSA, so the operator authenticates via a
# dedicated IAM user whose access key is stored in a Kubernetes Secret.

module "iam_user" {
  source = "../modules/iam-user"

  environment          = var.environment
  cluster_name         = var.cluster_name
  aws_region           = var.aws_region
  bedrock_model_id     = var.bedrock_model_id
  dynamodb_table_arn   = module.dynamodb.table_arn
  cloudwatch_namespace = var.cloudwatch_namespace
}
