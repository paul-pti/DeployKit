variable "name" {
  description = "Prefix for resource names and the Name tag"
  type        = string
}

variable "cidr_block" {
  description = "CIDR block of the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Availability zones to spread subnets across (at least 2, required by EKS)"
  type        = list(string)
}

variable "public_subnet_cidrs" {
  description = "One CIDR per AZ, for the public (NAT gateway, load balancers) subnets"
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "One CIDR per AZ, for the private (EKS nodes, RDS) subnets"
  type        = list(string)
}

variable "single_nat_gateway" {
  description = "One NAT gateway for all private subnets (cheaper) instead of one per AZ (highly available)"
  type        = bool
  default     = true
}

variable "cluster_name" {
  description = "EKS cluster name that will use these subnets; tags them so the AWS Load Balancer Controller and the cluster autoscaler can discover them. Leave empty if not using EKS."
  type        = string
  default     = ""
}

variable "tags" {
  description = "Tags applied to every resource"
  type        = map(string)
  default     = {}
}
