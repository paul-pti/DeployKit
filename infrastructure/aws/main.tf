locals {
  cluster_name = "${var.environment}-eks"
  tags = {
    Project     = "deploykit"
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

module "vpc" {
  source = "./modules/vpc"

  name                 = var.environment
  cidr_block           = var.vpc_cidr
  azs                  = var.azs
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
  single_nat_gateway   = var.single_nat_gateway
  cluster_name         = local.cluster_name
  tags                 = local.tags
}

module "eks" {
  source = "./modules/eks"

  cluster_name        = local.cluster_name
  kubernetes_version  = var.kubernetes_version
  subnet_ids          = concat(module.vpc.public_subnet_ids, module.vpc.private_subnet_ids)
  node_subnet_ids     = module.vpc.private_subnet_ids
  node_instance_types = var.node_instance_types
  node_desired_size   = var.node_desired_size
  node_min_size       = var.node_min_size
  node_max_size       = var.node_max_size
  tags                = local.tags
}

module "rds" {
  source = "./modules/rds"

  identifier                 = "${var.environment}-db"
  instance_class             = var.db_instance_class
  allocated_storage          = var.db_allocated_storage
  password                   = var.db_password
  multi_az                   = var.db_multi_az
  vpc_id                     = module.vpc.vpc_id
  subnet_ids                 = module.vpc.private_subnet_ids
  allowed_security_group_ids = [module.eks.cluster_security_group_id]
  tags                       = local.tags
}

module "ecr" {
  source = "./modules/ecr"

  repository_name = "${var.environment}-backend"
  tags            = local.tags
}
