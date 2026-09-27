import { useEffect, useRef } from 'react'

export interface TerminalLine {
  text: string
  tone?: 'normal' | 'dim' | 'warn' | 'error'
}

const toneClass: Record<NonNullable<TerminalLine['tone']>, string> = {
  normal: 'text-slate-100',
  dim: 'text-slate-400',
  warn: 'text-amber-300',
  error: 'text-red-400',
}

/** How close to the bottom (in px) the user must be for new lines to keep scrolling into view. */
const STICK_THRESHOLD = 24

/**
 * A dark, monospace log viewer. It follows the end of the output like `tail -f`, until the user scrolls up to read;
 * scrolling back to the bottom resumes following.
 */
export function LogTerminal({ lines, emptyText, label }: { lines: TerminalLine[]; emptyText: string; label: string }) {
  const container = useRef<HTMLDivElement>(null)
  const followEnd = useRef(true)

  useEffect(() => {
    const element = container.current
    if (element && followEnd.current) element.scrollTop = element.scrollHeight
  }, [lines])

  const onScroll = () => {
    const element = container.current
    if (element) {
      followEnd.current = element.scrollHeight - element.scrollTop - element.clientHeight < STICK_THRESHOLD
    }
  }

  return (
    <div
      ref={container}
      onScroll={onScroll}
      role="log"
      aria-label={label}
      tabIndex={0}
      className="max-h-96 min-h-32 overflow-y-auto rounded-lg bg-slate-950 p-3 font-mono text-xs leading-5"
    >
      {lines.length === 0 ? (
        <p className="text-slate-500">{emptyText}</p>
      ) : (
        lines.map((line, index) => (
          <div key={index} className={`whitespace-pre-wrap break-all ${toneClass[line.tone ?? 'normal']}`}>
            {line.text || ' '}
          </div>
        ))
      )}
    </div>
  )
}
