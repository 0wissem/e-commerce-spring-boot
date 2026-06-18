# ─────────────────────────────────────────────────────────────────────────────
# Terraform + provider + remote state config.
# This is the equivalent of "package.json + the framework setup" for your infra.
# ─────────────────────────────────────────────────────────────────────────────

terraform {
  # use_lockfile (native S3 state locking) needs Terraform 1.10+.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state in S3. The bucket was created ONCE during bootstrap (outside
  # Terraform — the chicken-and-egg "seed"). Storing state remotely is what lets
  # Terraform run in CI: the ephemeral GitHub runner reads/writes shared state here
  # instead of a local file that would vanish after each run.
  #
  # use_lockfile = S3-native locking (no DynamoDB table needed) — prevents two
  # runs from corrupting state by writing at once.
  backend "s3" {
    bucket       = "wissem-tfstate-110911131381"
    key          = "ecommerce/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  # default_tags stamps every resource Terraform creates — so you can always tell,
  # in the AWS console, what was made by Terraform vs clicked by hand.
  default_tags {
    tags = {
      Project   = "ecommerce"
      ManagedBy = "terraform"
    }
  }
}
