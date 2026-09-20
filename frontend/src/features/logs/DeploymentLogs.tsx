import { useState } from 'react'
import { StatusBadge } from '../deployments/StatusBadge'
import { LOGS_REFRESH_MS, useDeploymentLogs } from '../../hooks/useDeploymentLogs'
import { parseApiError } from '../../lib/apiError'
import type { DeploymentEvent, DeploymentLogsResponse, LogLevel } from '../../types/logs'
import { LogTerminal, type TerminalLine } from './LogTerminal'

type Tab = 'application' | 'deployment'

const levelTone: Record<LogLevel, TerminalLine['tone']> = {
  DEBUG: 'dim',
  INFO: 'normal',
  WARN: 'warn',
  ERROR: 'error',
}

function splitLines(text: string): string[] {
  return text.replace(/\n$/, '').split('\n')
}

function applicationLines(data: DeploymentLogsResponse): TerminalLine[] {
  const lines: TerminalLine[] = []
  if (data.podsNote) lines.push({ text: data.podsNote, tone: 'dim' })
  for (const pod of data.pods) {
    const details = [
      pod.phase ?? 'unknown',
      pod.ready ? 'ready' : null,
      pod.restarts > 0 ? `${pod.restarts} restart${pod.restarts === 1 ? '' : 's'}` : null,
      pod.reason,
    ].filter(Boolean)
    lines.push({ text: `── ${pod.pod} · ${details.join(' · ')}`, tone: 'dim' })
    if (pod.error) lines.push({ text: pod.error, tone: 'warn' })
    else if (pod.log) splitLines(pod.log).forEach((text) => lines.push({ text }))
    else lines.push({ text: '(no output yet)', tone: 'dim' })
  }
  return lines
}

function eventLines(events: DeploymentEvent[]): TerminalLine[] {
  return events.flatMap((event) => {
    const time = new Date(event.timestamp).toLocaleTimeString([], { hour12: false })
    const [first, ...rest] = splitLines(event.message)
    const tone = levelTone[event.level]
    return [
      { text: `${time}  ${event.level.padEnd(5)}  ${first}`, tone },
      ...rest.map((text) => ({ text: `${' '.repeat(17)}${text}`, tone })),
    ]
  })
}

/** Terminal-style view of a deployment: application (pod) logs and the deployment workflow log. */
export function DeploymentLogs({ deploymentId, version }: { deploymentId: string; version?: number }) {
  const [tab, setTab] = useState<Tab>('application')
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [previous, setPrevious] = useState(false)
  const { data, isPending, isError, error, dataUpdatedAt } = useDeploymentLogs(deploymentId, {
    previous,
    refresh: autoRefresh,
  })

  const lines = data ? (tab === 'application' ? applicationLines(data) : eventLines(data.events)) : []
  const tabClass = (active: boolean) =>
    `rounded-md px-3 py-1 text-sm ${active ? 'bg-slate-900 text-white' : 'border border-slate-300 text-slate-700'}`

  return (
    <section className="mt-6 space-y-3" aria-label="Deployment logs">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="flex items-center gap-2 text-base font-semibold">
          Logs{version !== undefined && ` · #${version}`}
          {data && <StatusBadge status={data.status} />}
        </h3>
        <div className="flex flex-wrap items-center gap-4 text-sm text-slate-600">
          {tab === 'application' && (
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={previous} onChange={(event) => setPrevious(event.target.checked)} />
              Previous container
            </label>
          )}
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)} />
            Auto-refresh ({LOGS_REFRESH_MS / 1000}s)
          </label>
        </div>
      </div>

      <div className="flex gap-2">
        <button type="button" aria-pressed={tab === 'application'} onClick={() => setTab('application')} className={tabClass(tab === 'application')}>
          Application
        </button>
        <button type="button" aria-pressed={tab === 'deployment'} onClick={() => setTab('deployment')} className={tabClass(tab === 'deployment')}>
          Deployment
        </button>
      </div>

      {isPending && <p className="text-slate-500">Loading logs…</p>}
      {isError && (
        <p role="alert" className="text-red-600">
          {parseApiError(error).message}
        </p>
      )}
      {data && (
        <>
          <LogTerminal
            lines={lines}
            label={tab === 'application' ? 'Application logs' : 'Deployment log'}
            emptyText={tab === 'application' ? 'No application output.' : 'No deployment events.'}
          />
          <p className="text-xs text-slate-500">
            {autoRefresh ? 'Refreshing automatically. ' : 'Auto-refresh is off. '}
            Last updated {new Date(dataUpdatedAt).toLocaleTimeString()}.
          </p>
        </>
      )}
    </section>
  )
}
