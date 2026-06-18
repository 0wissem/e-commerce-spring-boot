# Outputs are Terraform's "return values" — printed after apply and queryable later.
# You'll paste these repo URLs into the k8s manifests (the image: field) when you
# deploy onto EKS.

output "ecr_repository_urls" {
  description = "Push/pull URL for each ECR repo, keyed by service name."
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}
