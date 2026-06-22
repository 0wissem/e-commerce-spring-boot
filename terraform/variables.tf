variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "eu-west-3"
}

variable "ecr_repositories" {
  description = <<-EOT
    One ECR repo per deployable service. These names MUST match the image names
    in the build pipelines (docker build -t .../<name>) and in the k8s manifests.
  EOT
  type        = list(string)
  default     = ["spring-boot-0", "product-service", "gateway", "order-service"]
}

# ─── EKS ─────────────────────────────────────────────────────────────────────

variable "cluster_name" {
  description = "Name of the EKS cluster (also used as a prefix for the VPC)."
  type        = string
  default     = "ecommerce"
}

variable "cluster_version" {
  description = <<-EOT
    Kubernetes version for EKS. IMPORTANT: pick a version in STANDARD support.
    Versions in *extended* support bill the control plane at a MUCH higher rate
    (~$0.60/hr vs ~$0.10/hr). Check the current supported list in the EKS docs.
  EOT
  type        = string
  default     = "1.32"
}

variable "node_instance_type" {
  description = "EC2 instance type for the worker nodes. t3.micro = free-tier-eligible (no node charge)."
  type        = string
  default     = "t3.micro"
}

variable "node_desired_size" {
  description = "Desired worker node count (max_size = this + 2). 3x t3.micro to give the stack a chance to fit (nodes are free-tier)."
  type        = number
  default     = 3
}

variable "cluster_admin_principal_arn" {
  description = <<-EOT
    IAM principal (your console user) granted cluster-admin via an EKS access
    entry, so you can run kubectl (e.g. from CloudShell).
    ⚠️ CONFIRM this matches the IAM username you actually created — change if different.
  EOT
  type        = string
  default     = "arn:aws:iam::110911131381:user/wissem-admin"
}
