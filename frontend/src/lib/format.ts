/** "45s" or "2m 5s" between two ISO timestamps, or null when either one is missing. */
export function formatDuration(start: string | null, end: string | null): string | null {
  if (!start || !end) return null
  const seconds = Math.max(0, Math.round((new Date(end).getTime() - new Date(start).getTime()) / 1000))
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

/** First 7 characters of a commit SHA, or an em dash when the commit is unknown. */
export function shortSha(sha: string | null): string {
  return sha ? sha.slice(0, 7) : '—'
}
