# AWS (Terraform)

`infrastructure/aws` provisions what running DeployKit for real needs, replacing the local `kind` cluster and the
Compose Postgres: a VPC, an EKS cluster, an RDS Postgres instance and an ECR repository for the backend's own image.
Design decisions: [ADR 0008](adr/0008-terraform-hand-written-modules.md).

**This creates real, billable AWS resources.** Nothing here is applied automatically, by me or by CI: you review the
plan and run `terraform apply` yourself, with your own AWS credentials, when you are ready.

## Layout

```
infrastructure/aws/
  versions.tf, providers.tf, variables.tf, main.tf, outputs.tf   # the root module: wires the four below together
  terraform.tfvars.example                                       # copy to terraform.tfvars, or use TF_VAR_*
  modules/
    vpc/    2 AZs, public + private subnets, 1 NAT gateway by default
    eks/    cluster + managed node group, IAM roles, OIDC provider (ready for IRSA)
    rds/    Postgres, private subnets only, security group scoped to the EKS cluster
    ecr/    one repository (backend image), lifecycle policy expiring untagged images
```

## Using it

```bash
cd infrastructure/aws
terraform init
cp terraform.tfvars.example terraform.tfvars   # then edit it — or skip this and export TF_VAR_* instead
export TF_VAR_db_password=$(openssl rand -base64 24)
terraform plan    # read it
terraform apply   # only once you're happy with the plan
```

Then, to actually run the backend there:

```bash
$(terraform output -raw configure_kubectl)                 # points kubectl at the new cluster
docker build -t "$(terraform output -raw backend_ecr_repository_url):latest" ../../backend
aws ecr get-login-password | docker login --username AWS --password-stdin "$(terraform output -raw backend_ecr_repository_url)"
docker push "$(terraform output -raw backend_ecr_repository_url):latest"
```

The backend still needs `DB_URL` (built from `terraform output database_endpoint` and the `db_name`/`username` you
chose), `DEPLOYKIT_JWT_SECRET` and the other variables from [configuration.md](configuration.md) — as Kubernetes
`Secret`/`ConfigMap` values this time, the same way the generic Helm chart passes them to a deployed application
(see [local-kubernetes.md](local-kubernetes.md)). Deploying DeployKit's own backend to its own cluster with its own
Helm chart is not automated yet; the pieces (image in ECR, cluster, database) are there.

## What each default trades off

| Choice | Default | Trade-off |
|---|---|---|
| NAT gateways | 1 (shared) | Cheaper (~2/3 less); a NAT gateway failure takes down all private subnets' internet egress instead of just one AZ's. Set `single_nat_gateway = false` for one per AZ. |
| EKS API endpoint | Public, open to `0.0.0.0/0` | Simplest to get `kubectl` working from anywhere. Set `endpoint_public_access_cidrs` to your own IP range, or `endpoint_public_access = false` behind a VPN/bastion, for anything beyond a first try. |
| RDS | Single-AZ, `db.t3.micro`, deletion protection off | Cheap and disposable, matching a getting-started setup. Turn on `db_multi_az` and the RDS module's `deletion_protection` before this holds data you'd miss. |
| Node group | 1–3 `t3.medium`, on-demand | Predictable cost and availability. Spot instances would cut cost further at the price of nodes disappearing on short notice — a reasonable trade for stateless deployments, less so while the platform has no pod disruption budgets yet. |

## Not covered

- **TLS / a load balancer in front of the API** — see [security.md](security.md). Needs an ALB (or an ingress
  controller) plus an ACM certificate, neither provisioned here.
- **Terraform state storage** — local by default; `versions.tf` has a commented S3 + DynamoDB backend block and
  explains why it isn't provisioned automatically.
- **Deploying DeployKit's own Helm release to the new cluster** — the infrastructure exists; wiring it up is a
  manual step for now (see above).
