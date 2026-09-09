export type StatCardKey = 'active' | 'pending' | 'realisedPnl' | 'winRate' | 'unrealisedPnl'

export type PositionColumnKey =
  | 'qty'
  | 'avgEntry'
  | 'ltp'
  | 'invested'
  | 'unrealisedPnl'
  | 'sl'
  | 'target'
  | 'status'

export const STAT_CARD_LABELS: Record<StatCardKey, string> = {
  active: 'Active Positions',
  pending: 'Pending Orders',
  realisedPnl: 'Total Realised P&L',
  winRate: 'Win Rate',
  unrealisedPnl: 'Total Unrealised P&L',
}

export const POSITION_COLUMN_LABELS: Record<PositionColumnKey, string> = {
  qty: 'Qty',
  avgEntry: 'Avg Entry',
  ltp: 'LTP',
  invested: 'Invested Amount',
  unrealisedPnl: 'Unrealised P&L',
  sl: 'SL',
  target: 'Target',
  status: 'Status',
}

export const DEFAULT_STAT_CARDS: StatCardKey[] = ['active', 'pending', 'realisedPnl', 'winRate', 'unrealisedPnl']

export const DEFAULT_POSITION_COLUMNS: PositionColumnKey[] = [
  'qty', 'avgEntry', 'ltp', 'invested', 'unrealisedPnl', 'sl', 'target', 'status',
]

export const STAT_CARDS_STORAGE_KEY = 'zbs.dashboard.statCards'
export const POSITION_COLUMNS_STORAGE_KEY = 'zbs.dashboard.positionColumns'

/**
 * Reads a visible-keys list from localStorage, falling back to defaults when the value is
 * missing, unreadable, or malformed. A stored value that parses to a recognized (even empty)
 * array of keys is trusted as-is — including "the user hid everything" — rather than being
 * treated as equivalent to no stored value at all.
 */
export function loadVisibleKeys<T extends string>(storageKey: string, allKeys: readonly T[], defaults: readonly T[]): T[] {
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) return [...defaults]
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return [...defaults]
    // A stored empty array is a deliberate "hide everything" choice — trust it.
    // A non-empty stored array that filters down to zero valid keys is stale/corrupted
    // (e.g. an old app version's keys), not user intent — fall back to defaults instead.
    if (parsed.length === 0) return []
    const valid = parsed.filter((k): k is T => typeof k === 'string' && (allKeys as readonly string[]).includes(k))
    return valid.length > 0 ? [...new Set(valid)] : [...defaults]
  } catch {
    return [...defaults]
  }
}

export function saveVisibleKeys(storageKey: string, keys: readonly string[]): void {
  try {
    localStorage.setItem(storageKey, JSON.stringify(keys))
  } catch {
    // localStorage unavailable (private browsing, quota, etc.) — preference just won't persist
  }
}
