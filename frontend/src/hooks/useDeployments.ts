import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { deployProject, getDeployment, listDeployments } from '../services/deploymentService'
import { isActive, type DeployRequest, type DeploymentStatus } from '../types/deployment'

const PAGE_SIZE = 10

export function useDeployProject(projectId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request?: DeployRequest) => deployProject(projectId, request),
    // The new deployment must show up in the history straight away.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['projects', projectId, 'deployments'] }),
  })
}

/** A project's deployment history; refreshes every 3 seconds while a deployment is still in flight. */
export function useDeploymentHistory(projectId: string, statuses: DeploymentStatus[], page: number) {
  return useQuery({
    queryKey: ['projects', projectId, 'deployments', { statuses, page }],
    queryFn: () => listDeployments(projectId, { statuses, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
    refetchInterval: (query) =>
      query.state.data?.content.some((deployment) => isActive(deployment.status)) ? 3000 : false,
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
