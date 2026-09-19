import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createProject, deleteProject, getProject, listProjects } from '../services/projectService'

const projectsKey = ['projects'] as const

export function useProjects() {
  return useQuery({ queryKey: projectsKey, queryFn: listProjects })
}

export function useProject(id: string | undefined) {
  return useQuery({
    queryKey: [...projectsKey, id],
    queryFn: () => getProject(id as string),
    enabled: Boolean(id),
    retry: false,
  })
}

export function useCreateProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createProject,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: projectsKey }),
  })
}

export function useDeleteProject() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteProject,
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: [...projectsKey, id] })
      return queryClient.invalidateQueries({ queryKey: projectsKey })
    },
  })
}
