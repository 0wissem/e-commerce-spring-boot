# RDS PostgreSQL — ONE db.t3.micro (free-tier), hosting 3 databases for the
# services. Moving the DBs OUT of the cluster frees pod slots on the tiny EKS
# nodes so the 4 app pods fit. RDS uses a SEPARATE quota (not the EC2 vCPU limit
# that blocked node scaling). Shared-server trade-off vs per-service DBs — free.

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

resource "aws_db_instance" "main" {
  identifier        = var.cluster_name
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = "db.t3.micro" # free-tier-eligible
  allocated_storage = 20            # free-tier (20 GB)

  db_name  = "monolith" # initial DB; products + orders created by the init Job
  username = "postgres"
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  skip_final_snapshot    = true
}

output "rds_address" {
  description = "RDS endpoint host (used in the app DB URLs)."
  value       = aws_db_instance.main.address
}
