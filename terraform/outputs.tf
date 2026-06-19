output "ecr_repository_urls" {
  description = "Push/pull URL for each ECR repo, keyed by service name."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "EKS API server endpoint."
  value       = module.eks.cluster_endpoint
}

output "configure_kubectl" {
  description = "Run this (e.g. in CloudShell) to point kubectl at the new cluster."
  value       = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.aws_region}"
}
