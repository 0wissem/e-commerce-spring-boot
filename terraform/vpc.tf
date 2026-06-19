# Networking for the cluster, via the official AWS VPC module.
#
# Public subnets  → where the internet-facing load balancer lives.
# Private subnets → where the worker nodes live (not directly reachable).
# A single NAT gateway lets the private nodes reach OUT (pull images from ECR,
# talk to the EKS API). single_nat_gateway = one shared NAT instead of one per AZ
# = cheaper (~$0.045/hr while it exists; gone the moment you `terraform destroy`).

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.cluster_name}-vpc"
  cidr = "10.0.0.0/16"

  azs             = slice(data.aws_availability_zones.available.names, 0, 2)
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24"]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true

  # These tags let AWS load balancers auto-discover which subnets to use
  # (public for internet-facing, private for internal). The ALB Ingress
  # controller relies on them later.
  public_subnet_tags = {
    "kubernetes.io/role/elb" = "1"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = "1"
  }
}
