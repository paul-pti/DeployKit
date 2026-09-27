variable "identifier" {
  description = "RDS instance identifier"
  type        = string
}

variable "engine_version" {
  type    = string
  default = "16.4"
}

variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "allocated_storage" {
  description = "Storage in GB"
  type        = number
  default     = 20
}

variable "db_name" {
  type    = string
  default = "deploykit"
}

variable "username" {
  type    = string
  default = "deploykit"
}

variable "password" {
  description = "No default on purpose: pass it via TF_VAR_db_password or a gitignored .tfvars file, never commit it"
  type        = string
  sensitive   = true
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  description = "Private subnets to place the database in"
  type        = list(string)
}

variable "allowed_security_group_ids" {
  description = "Security groups allowed to reach Postgres on 5432 (the EKS cluster's security group)"
  type        = list(string)
}

variable "multi_az" {
  description = "Standby replica in a second AZ; doubles the cost, halves the downtime on an AZ failure"
  type        = bool
  default     = false
}

variable "backup_retention_period" {
  description = "Days of automated backups to keep"
  type        = number
  default     = 7
}

variable "deletion_protection" {
  description = "Refuse `terraform destroy`/console deletion. Off by default for a getting-started setup; turn on once this holds real data."
  type        = bool
  default     = false
}

variable "skip_final_snapshot" {
  description = "Skip the final snapshot on deletion. Matches deletion_protection's default; the two should usually move together."
  type        = bool
  default     = true
}

variable "tags" {
  type    = map(string)
  default = {}
}
