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
  # Uncomment and fill in the bucket/key/region below, or use a different backend.
  # backend "s3" {
  #   bucket         = "my-terraform-state-bucket"
  #   key            = "k8s-ai-operator/${var.environment}/terraform.tfstate"
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

data "aws_availability_zones" "available" {
  state = "available"
}

# ── Networking (VPC + subnets for EKS) ────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "k8s-ai-operator-${var.environment}"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "k8s-ai-operator-igw-${var.environment}"
  }
}

resource "aws_subnet" "public" {
  count             = length(var.public_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.public_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index % length(data.aws_availability_zones.available.names)]

  map_public_ip_on_launch = true

  tags = {
    Name                                                        = "k8s-ai-operator-public-${count.index}-${var.environment}"
    "kubernetes.io/cluster/${var.eks_cluster_name}-${var.environment}" = "shared"
    "kubernetes.io/role/elb"                                    = "1"
  }
}

resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index % length(data.aws_availability_zones.available.names)]

  tags = {
    Name                                                        = "k8s-ai-operator-private-${count.index}-${var.environment}"
    "kubernetes.io/cluster/${var.eks_cluster_name}-${var.environment}" = "shared"
    "kubernetes.io/role/internal-elb"                           = "1"
  }
}

resource "aws_eip" "nat" {
  count  = length(var.public_subnet_cidrs)
  domain = "vpc"

  tags = {
    Name = "k8s-ai-operator-nat-eip-${count.index}-${var.environment}"
  }
}

resource "aws_nat_gateway" "main" {
  count         = length(var.public_subnet_cidrs)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "k8s-ai-operator-nat-${count.index}-${var.environment}"
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name = "k8s-ai-operator-public-rt-${var.environment}"
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count  = length(aws_subnet.private)
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index % length(aws_nat_gateway.main)].id
  }

  tags = {
    Name = "k8s-ai-operator-private-rt-${count.index}-${var.environment}"
  }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

# ── Modules ────────────────────────────────────────────────────────────────────

module "dynamodb" {
  source = "./modules/dynamodb"

  table_name  = var.dynamodb_table_name
  environment = var.environment
}

module "iam" {
  source = "./modules/iam"

  environment          = var.environment
  aws_region           = var.aws_region
  account_id           = data.aws_caller_identity.current.account_id
  dynamodb_table_arn   = module.dynamodb.table_arn
  cloudwatch_namespace = var.cloudwatch_namespace
  bedrock_model_id     = var.bedrock_model_id
}

module "lambda" {
  source = "./modules/lambda"

  environment            = var.environment
  aws_region             = var.aws_region
  jar_path               = var.lambda_jar_path
  memory_mb              = var.lambda_memory_mb
  timeout_seconds        = var.lambda_timeout_seconds
  execution_role_arn     = module.iam.execution_role_arn
  bedrock_model_id       = var.bedrock_model_id
  bedrock_max_tokens     = var.bedrock_max_tokens
  dynamodb_table_name    = module.dynamodb.table_name
  cloudwatch_namespace   = var.cloudwatch_namespace
  log_retention_days     = var.log_retention_days
}

module "api_gateway" {
  source = "./modules/api-gateway"

  environment          = var.environment
  aws_region           = var.aws_region
  account_id           = data.aws_caller_identity.current.account_id
  lambda_function_name = module.lambda.function_name
  lambda_function_arn  = module.lambda.function_arn
}

module "eks" {
  source = "./modules/eks"

  environment                    = var.environment
  cluster_name                   = "${var.eks_cluster_name}-${var.environment}"
  kubernetes_version             = var.eks_kubernetes_version
  node_instance_type             = var.eks_node_instance_type
  node_desired_count             = var.eks_node_desired_count
  node_min_count                 = var.eks_node_min_count
  node_max_count                 = var.eks_node_max_count
  private_subnet_ids             = aws_subnet.private[*].id
  public_subnet_ids              = aws_subnet.public[*].id
  vpc_id                         = aws_vpc.main.id
  account_id                     = data.aws_caller_identity.current.account_id
  aws_region                     = var.aws_region
  dynamodb_table_arn             = module.dynamodb.table_arn
  cloudwatch_namespace           = var.cloudwatch_namespace
  bedrock_model_id               = var.bedrock_model_id
  endpoint_public_access         = var.eks_endpoint_public_access
}
