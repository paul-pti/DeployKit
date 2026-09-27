terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # State is local by default (fine alone, wrong for a team: two people applying at once can corrupt it). Before
  # sharing this with anyone else, create an S3 bucket (versioned, encrypted) and a DynamoDB table for locking, then
  # uncomment this block. Terraform cannot create its own backend before it exists, so that first bucket and table
  # are the one piece of this infrastructure meant to be created by hand or a separate, one-off apply.
  #
  # backend "s3" {
  #   bucket         = "deploykit-terraform-state"
  #   key            = "deploykit/terraform.tfstate"
  #   region         = "eu-west-1"
  #   dynamodb_table = "deploykit-terraform-locks"
  #   encrypt        = true
  # }
}
