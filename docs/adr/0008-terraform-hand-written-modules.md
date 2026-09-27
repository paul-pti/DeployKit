# ADR 0008: Hand-written Terraform modules, not a third-party registry module

**Status:** accepted

**Context:** Phase 12 needs a VPC, EKS cluster, RDS instance and ECR repository on AWS. Well-known community modules
exist for all of these (notably `terraform-aws-modules/*`).

**Decision**
- **Four small modules, written for this project** (`modules/vpc`, `eks`, `rds`, `ecr`), each a handful of resources,
  rather than the community modules. Every resource this project creates is visible in its own repository, in the
  same style as the rest of DeployKit's documentation-heavy, explain-every-decision approach — a third-party module's
  internals (feature flags, edge cases for setups DeployKit doesn't have) would work against that.
- **No remote Terraform backend is provisioned by this configuration.** State is local by default; a commented `s3`
  backend block in `versions.tf` is ready to uncomment once an S3 bucket and a DynamoDB lock table exist. Terraform
  cannot create the backend it is about to use in the same configuration that uses it, so that first bucket and table
  are the one part of this infrastructure meant to be created separately (console, CLI, or a one-off `apply` with a
  local backend that is then abandoned).
- **Nothing here is ever applied by an agent or by CI.** `terraform validate` (and `fmt`) can run without any AWS
  credentials and are safe to automate; `plan` and `apply` need real credentials and create or price out real
  resources, so they stay a manual, human step.
- **IAM roles carry only the AWS-managed policies each principal needs** (cluster: `AmazonEKSClusterPolicy`; nodes:
  worker, CNI, ECR read-only) — no inline "just in case" permissions.

**Consequences**
- Upgrading a provider or adding a resource (e.g. an ALB Ingress Controller) means editing HCL that already lives
  here, not tracking a third-party module's release notes for a feature it may or may not expose as a variable.
- The trade-off is more Terraform to maintain directly, and less of the polish (extensive input validation, every
  edge case) a mature community module accumulates over time. Acceptable for infrastructure of this size.
- The OIDC provider set up in the `eks` module is unused today (DeployKit does not call AWS APIs, only the
  Kubernetes API); it is there because IRSA needs it, and adding it later would otherwise force a cluster or add-on
  migration.
