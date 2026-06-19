# The EKS cluster, via the official AWS EKS module.
#
# This single module creates a LOT: the managed control plane, a managed node
# group (the EC2 workers), the core add-ons, and all the IAM/OIDC plumbing.
# This is the resource that starts the ~$0.10/hr control-plane meter — plus the
# nodes and the NAT gateway. `terraform destroy` removes all of it.

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version

  # Public API endpoint so kubectl (CloudShell / your laptop) can reach it.
  cluster_endpoint_public_access = true

  # Whoever CREATES the cluster (here: the Terraform CI role) automatically
  # becomes a Kubernetes admin. Without this you could build a cluster you
  # can't actually use — a classic first-timer trap.
  enable_cluster_creator_admin_permissions = true

  # IRSA = "IAM Roles for Service Accounts" — lets specific pods assume IAM
  # roles. Needed later for the ALB Ingress controller, EBS CSI driver, etc.
  enable_irsa = true

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets # nodes live in the private subnets

  # Core add-ons, managed by EKS itself.
  cluster_addons = {
    coredns    = {} # in-cluster DNS
    kube-proxy = {} # pod networking rules
    vpc-cni    = {} # the AWS CNI that gives pods VPC IPs
  }

  # One managed node group of on-demand workers.
  # min == desired here; HPA/cluster scaling can grow it up to max_size.
  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]
      min_size       = var.node_desired_size
      desired_size   = var.node_desired_size
      max_size       = var.node_desired_size + 2
    }
  }

  # Give your console user kubectl access via a native EKS *access entry*
  # (the modern replacement for the old, fiddly aws-auth ConfigMap).
  access_entries = {
    admin_user = {
      principal_arn = var.cluster_admin_principal_arn
      policy_associations = {
        admin = {
          policy_arn   = "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy"
          access_scope = { type = "cluster" }
        }
      }
    }
  }
}
