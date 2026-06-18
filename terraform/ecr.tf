# One ECR repository per service.
#
# for_each over the list = adding a 5th service later is a ONE-LINE change to
# var.ecr_repositories; Terraform will plan to create only the new repo and leave
# the existing four untouched. (This is the declarative payoff: you describe the
# desired set, Terraform figures out the diff — same mental model as React state.)

resource "aws_ecr_repository" "service" {
  for_each = toset(var.ecr_repositories)

  name = each.value

  # MUTABLE lets the :latest tag be overwritten on each push (fine for learning).
  # The production-grade choice is IMMUTABLE + versioned tags (on your backlog).
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true # free vulnerability scan on every pushed image
  }

  # Allows `terraform destroy` to delete a repo even if it still holds images.
  # Convenient for a disposable learning environment; in prod you'd want the
  # safety of this being false so you can't nuke a repo full of images by accident.
  force_delete = true
}
