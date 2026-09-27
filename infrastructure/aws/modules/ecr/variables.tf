variable "repository_name" {
  type = string
}

variable "image_tag_mutability" {
  description = "IMMUTABLE prevents a tag (e.g. a commit SHA) from ever being overwritten"
  type        = string
  default     = "IMMUTABLE"
}

variable "scan_on_push" {
  description = "Basic vulnerability scanning on every pushed image"
  type        = bool
  default     = true
}

variable "expire_untagged_after_days" {
  description = "Untagged images (superseded manifests) older than this are deleted; 0 disables the rule"
  type        = number
  default     = 14
}

variable "tags" {
  type    = map(string)
  default = {}
}
