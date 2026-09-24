import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as projectService from '../../services/projectService'
import { ProjectForm } from './ProjectForm'

vi.mock('../../services/projectService')

function renderForm() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <ProjectForm />
    </QueryClientProvider>,
  )
}

describe('ProjectForm', () => {
  beforeEach(() => {
    vi.mocked(projectService.createProject).mockReset()
  })

  it('submits the values, leaving an empty branch as undefined, and resets on success', async () => {
    vi.mocked(projectService.createProject).mockResolvedValue({
      id: '1',
      name: 'demo',
      repositoryUrl: 'https://github.com/acme/demo',
      branch: 'main',
      port: 8080,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    })
    renderForm()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Name'), 'demo')
    await user.type(screen.getByLabelText('GitHub repository URL'), 'https://github.com/acme/demo')
    await user.click(screen.getByRole('button', { name: /create project/i }))

    // TanStack Query's mutationFn also receives a (client, mutationKey, meta) context as a 2nd argument;
    // only the payload we actually send to the API matters here.
    await waitFor(() => expect(projectService.createProject).toHaveBeenCalledTimes(1))
    expect(vi.mocked(projectService.createProject).mock.calls[0][0]).toEqual({
      name: 'demo',
      repositoryUrl: 'https://github.com/acme/demo',
      branch: undefined,
      port: 8080,
    })
    await waitFor(() => expect(screen.getByLabelText('Name')).toHaveValue(''))
  })

  it('shows the field error returned by the API next to the offending field', async () => {
    vi.mocked(projectService.createProject).mockRejectedValue({
      isAxiosError: true,
      response: { status: 409, data: { detail: 'Conflict', errors: { name: 'already used' } } },
    })
    renderForm()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Name'), 'demo')
    await user.type(screen.getByLabelText('GitHub repository URL'), 'https://github.com/acme/demo')
    await user.click(screen.getByRole('button', { name: /create project/i }))

    expect(await screen.findByText('already used')).toBeInTheDocument()
  })
})
