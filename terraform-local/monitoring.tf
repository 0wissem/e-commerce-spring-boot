# The kube-prometheus-stack (Prometheus + Grafana + exporters), now declared as a
# Terraform helm_release instead of a manual `helm install`. This is the genuinely
# professional Terraform-on-Kubernetes pattern: using the helm provider to manage
# platform add-ons. `terraform destroy` cleanly uninstalls the whole stack.
#
# Reuses the exact same lean values file we wrote for the manual install.

resource "helm_release" "monitoring" {
  name             = "monitoring"
  repository       = "https://prometheus-community.github.io/helm-charts"
  chart            = "kube-prometheus-stack"
  namespace        = "monitoring"
  create_namespace = true

  # Pin the chart version so `apply` is reproducible (matches the manual install).
  version = "86.2.3"

  # The same capped-resources / short-retention / Alertmanager-off values.
  values = [file("${path.module}/../k8s/06-monitoring-values.yaml")]
}

output "monitoring_status" {
  description = "Helm release status — should be 'deployed' after apply."
  value       = helm_release.monitoring.status
}
