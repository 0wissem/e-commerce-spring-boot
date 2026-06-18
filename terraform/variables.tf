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
