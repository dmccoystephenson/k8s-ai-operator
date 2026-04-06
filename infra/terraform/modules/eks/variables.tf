variable "environment" {
  description = "Deployment environment"
  type        = string
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster"
  type        = string
}

variable "node_instance_type" {
  description = "EC2 instance type for worker nodes"
  type        = string
}

variable "node_desired_count" {
  description = "Desired number of worker nodes"
  type        = number
}

variable "node_min_count" {
  description = "Minimum number of worker nodes"
  type        = number
}

variable "node_max_count" {
  description = "Maximum number of worker nodes"
  type        = number
}

variable "private_subnet_ids" {
  description = "IDs of the private subnets for worker nodes"
  type        = list(string)
}

variable "public_subnet_ids" {
  description = "IDs of the public subnets for load balancers"
  type        = list(string)
}

variable "vpc_id" {
  description = "VPC ID where the cluster resides"
  type        = string
}
