import { useMutation, useQuery } from '@tanstack/react-query'
import { deployProject, getDeployment } from '../services/deploymentService'
import { isActive, type DeployRequest } from '../types/deployment'

export function useDeployProject(projectId: string) {
  return useMutation({
    mutationFn: (request?: DeployRequest) => deployProject(projectId, request),
  })
}

/** Follows a deployment, polling every 2 seconds until it reaches a final status. */
export function useDeployment(id: string | undefined) {
  return useQuery({
    queryKey: ['deployments', id],
    queryFn: () => getDeployment(id as string),
    enabled: Boolean(id),
    refetchInterval: (query) => (isActive(query.state.data?.status) ? 2000 : false),
  })
}
