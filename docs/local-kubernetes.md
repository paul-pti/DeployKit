# Running Kubernetes locally (kind)

Deploying needs a cluster, the `helm` binary and `kubectl`. [kind](https://kind.sigs.k8s.io/) runs a real Kubernetes
cluster in Docker.

```bash
brew install kind helm                  # or download the binaries; kubectl is also required
kind create cluster --config infrastructure/kind/kind-config.yaml   # context: kind-deploykit
kubectl get nodes
```

The backend uses your current kubeconfig context (or `deploykit.kubernetes.context` / `DEPLOYKIT_KUBERNETES_CONTEXT`
to pick one, e.g. `kind-deploykit`). The client connects lazily, so the backend still starts without a cluster.
Delete the cluster with `kind delete cluster --name deploykit`.

## The Helm chart

`helm/deploykit-app` renders a Deployment, Service, Ingress (off by default), ConfigMap and Secret:

```bash
helm lint helm/deploykit-app
helm upgrade --install demo helm/deploykit-app -n demo --create-namespace \
  --set image.repository=nginx --set image.tag=1.27-alpine --set containerPort=80 \
  --set config.GREETING=hi --set-string secretEnv.API_KEY=change-me --wait
helm -n demo uninstall demo
```

Sensitive values go in `secretEnv` (or an existing Secret via `existingSecret`); never commit real values. The
chart labels pods with `app.kubernetes.io/name=<app name>`, the same label `KubernetesService` selects on.
The kind config maps ports 8081/8443 for an ingress controller, which is not installed by default.

To reach an application from your machine, use a port-forward, for example
`kubectl -n demo port-forward svc/demo 8085:80`. If nothing answers, another process may hold the port:
`lsof -nP -iTCP:8085 -sTCP:LISTEN`.
