import { loadVisibleKeys, saveVisibleKeys } from './columnPrefs'

export { loadVisibleKeys, saveVisibleKeys }

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

