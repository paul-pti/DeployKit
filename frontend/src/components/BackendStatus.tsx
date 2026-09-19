import { useHealth } from '../hooks/useHealth'

export function BackendStatus() {
  const { data, isPending, isError } = useHealth()

  let label = 'Checking backend…'
  let dot = 'bg-slate-400'

  if (isError) {
    label = 'Backend unreachable'
    dot = 'bg-red-500'
  } else if (!isPending) {
    const up = data?.status === 'UP'
    label = up ? 'Backend UP' : `Backend ${data?.status ?? 'UNKNOWN'}`
    dot = up ? 'bg-emerald-500' : 'bg-amber-500'
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-sm text-slate-700"
    >
      <span className={`h-2 w-2 rounded-full ${dot}`} aria-hidden="true" />
      {label}
    </div>
  )
}
