variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "kubernetes_version" {
  description = "EKS Kubernetes version"
  type        = string
  default     = "1.31"
}

variable "subnet_ids" {
  description = "Subnets for the cluster's own ENIs (private and public: the API server needs both to be reachable and to route to private node subnets)"
  type        = list(string)
}

variable "node_subnet_ids" {
  description = "Private subnets the worker nodes launch into"
  type        = list(string)
}

variable "endpoint_public_access" {
  description = "Whether the Kubernetes API is reachable from outside the VPC. Fine for a getting-started setup; restrict via endpoint_public_access_cidrs or turn off in favour of a VPN/bastion for a real deployment."
  type        = bool
  default     = true
}

variable "endpoint_public_access_cidrs" {
  description = "CIDRs allowed to reach the public API endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "Instance types for the managed node group"
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_desired_size" {
  type    = number
  default = 2
}

variable "node_min_size" {
  type    = number
  default = 1
}

variable "node_max_size" {
  type    = number
  default = 3
}

variable "tags" {
  type    = map(string)
  default = {}
}
