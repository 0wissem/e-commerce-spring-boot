# RDS PostgreSQL — ONE db.t3.micro per service (per-service DB server = the
# project's design: independent failure domains). Moving the DBs OUT of the
# cluster frees pod slots on the tiny EKS nodes so the 4 app pods fit. RDS uses a
# SEPARATE quota (not the EC2 vCPU limit). For short tear-down-after sessions this
# stays within the RDS free-tier hours; any overage is on credit, not real money.

locals {
  # service name -> database name (each gets its own RDS instance)
  service_databases = {
    monolith = "monolith"
    products = "products"
    orders   = "orders"
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.cluster_name}-db"
  subnet_ids = module.vpc.private_subnets
}

resource "aws_security_group" "rds" {
  name        = "${var.cluster_name}-rds"
  description = "Allow Postgres from within the VPC (EKS nodes)"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "Postgres from inside the VPC"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [module.vpc.vpc_cidr_block]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_db_instance" "service" {
  for_each = local.service_databases

  identifier        = "${var.cluster_name}-${each.key}"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = "db.t3.micro" # free-tier-eligible
  allocated_storage = 20

  db_name  = each.value # this instance's own database
  username = "postgres"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  skip_final_snapshot    = true
}

output "rds_addresses" {
  description = "Per-service RDS endpoint hosts (used in each app's DB URL)."
  value       = { for svc, db in aws_db_instance.service : svc => db.address }
}
