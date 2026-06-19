# Look up the Availability Zones in the region. EKS needs at least 2; we spread
# the cluster across the first two AZs returned.
data "aws_availability_zones" "available" {
  state = "available"
}
