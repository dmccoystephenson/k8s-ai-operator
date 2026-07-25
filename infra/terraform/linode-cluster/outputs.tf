output "kubeconfig" {
  description = "Base64-encoded kubeconfig for the LKE cluster — decode and write to ~/.kube/config or use with KUBECONFIG"
  value       = linode_lke_cluster.main.kubeconfig
  sensitive   = true
}

output "cluster_id" {
  description = "Linode ID of the LKE cluster"
  value       = linode_lke_cluster.main.id
}

output "cluster_label" {
  description = "Label of the LKE cluster"
  value       = linode_lke_cluster.main.label
}

output "dynamodb_table_arn" {
  description = "ARN of the DynamoDB audit table"
  value       = module.dynamodb.table_arn
}

output "dynamodb_table_name" {
  description = "Name of the DynamoDB audit table"
  value       = module.dynamodb.table_name
}

output "aws_access_key_id" {
  description = "AWS access key ID for the operator IAM user — store as AWS_ACCESS_KEY_ID in the k8s-ai-operator-aws-credentials Secret"
  value       = module.iam_user.access_key_id
  sensitive   = true
}

output "aws_secret_access_key" {
  description = "AWS secret access key for the operator IAM user — store as AWS_SECRET_ACCESS_KEY in the k8s-ai-operator-aws-credentials Secret"
  value       = module.iam_user.secret_access_key
  sensitive   = true
}

output "iam_user_name" {
  description = "Name of the IAM user created for the operator"
  value       = module.iam_user.iam_user_name
}
