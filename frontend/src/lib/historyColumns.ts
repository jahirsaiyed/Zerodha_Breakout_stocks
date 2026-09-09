export type HistoryColumnKey = 'qty' | 'avgEntry' | 'pnl' | 'outcome' | 'closed'

export const HISTORY_COLUMN_LABELS: Record<HistoryColumnKey, string> = {
  qty: 'Qty',
  avgEntry: 'Avg Entry',
  pnl: 'P&L',
  outcome: 'Outcome',
  closed: 'Closed',
}

export const DEFAULT_HISTORY_COLUMNS: HistoryColumnKey[] = ['qty', 'avgEntry', 'pnl', 'outcome', 'closed']

export const HISTORY_COLUMNS_STORAGE_KEY = 'zbs.history.columns'
