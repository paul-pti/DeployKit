output "configure_kubectl" {
  description = "Run this to point kubectl (and therefore DEPLOYKIT_KUBERNETES_CONTEXT) at the new cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "backend_ecr_repository_url" {
  description = "Push the backend image here instead of (or in addition to) GHCR"
  value       = module.ecr.repository_url
}

output "database_endpoint" {
  description = "Use as the host:port part of DB_URL; the port is always 5432"
  value       = module.rds.endpoint
  sensitive   = false
}

output "vpc_id" {
  value = module.vpc.vpc_id
}
