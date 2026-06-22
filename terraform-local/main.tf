# ─────────────────────────────────────────────────────────────────────────────
# LOCAL Terraform — manages workloads INSIDE the local minikube cluster.
#
# This is completely separate from the AWS config in ../terraform:
#   • Different providers (helm + kubernetes, not aws).
#   • LOCAL state (no S3) — it manages a cluster on your laptop, so nothing here
#     touches the cloud and nothing costs money.
#   • Runs LOCALLY (`terraform -chdir=terraform-local ...`) — GitHub Actions can't
#     reach your laptop's minikube, so this never goes through CI.
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  required_version = ">= 1.5"

  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.0"
    }
  }
  # No backend block → state lives in a local terraform.tfstate file here.
}

# Both providers talk to minikube via its kubeconfig context.
provider "kubernetes" {
  config_path    = pathexpand("~/.kube/config")
  config_context = "minikube"
}

provider "helm" {
  kubernetes {
    config_path    = pathexpand("~/.kube/config")
    config_context = "minikube"
  }
}
