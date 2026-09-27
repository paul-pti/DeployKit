variable "aws_region" {
  type    = string
  default = "eu-west-1"
}

variable "environment" {
  description = "Short name used as a prefix everywhere (cluster, database, repository, tags)"
  type        = string
  default     = "deploykit"
}

variable "azs" {
  description = "At least 2 availability zones; EKS requires it"
  type        = list(string)
  default     = ["eu-west-1a", "eu-west-1b"]
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "single_nat_gateway" {
  description = "See modules/vpc: one NAT gateway (cheaper) or one per AZ (highly available)"
  type        = bool
  default     = true
}

variable "kubernetes_version" {
  type    = string
  default = "1.31"
}

variable "node_instance_types" {
  type    = list(string)
  default = ["t3.medium"]
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

variable "db_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

variable "db_password" {
  description = "No default: set with TF_VAR_db_password or a gitignored terraform.tfvars, never committed"
  type        = string
  sensitive   = true
}

variable "db_multi_az" {
  type    = bool
  default = false
}
