export type PositionActiveColumnKey =
  | 'qty' | 'avgEntry' | 'ltp' | 'unrealisedPnl' | 'stopLoss' | 'target' | 'gtt' | 'status'

export type PositionPendingColumnKey =
  | 'qty' | 'entryPrice' | 'stopLoss' | 'target' | 'status'

export const POSITION_ACTIVE_COLUMN_LABELS: Record<PositionActiveColumnKey, string> = {
  qty: 'Qty',
  avgEntry: 'Avg Entry',
  ltp: 'LTP',
  unrealisedPnl: 'Unrealised P&L',
  stopLoss: 'Stop Loss',
  target: 'Target',
  gtt: 'GTT',
  status: 'Status',
}

export const POSITION_PENDING_COLUMN_LABELS: Record<PositionPendingColumnKey, string> = {
  qty: 'Qty',
  entryPrice: 'Entry Price',
  stopLoss: 'Stop Loss',
  target: 'Target',
  status: 'Status',
}

export const DEFAULT_POSITION_ACTIVE_COLUMNS: PositionActiveColumnKey[] =
  ['qty', 'avgEntry', 'ltp', 'unrealisedPnl', 'stopLoss', 'target', 'gtt', 'status']

export const DEFAULT_POSITION_PENDING_COLUMNS: PositionPendingColumnKey[] =
  ['qty', 'entryPrice', 'stopLoss', 'target', 'status']

export const POSITION_ACTIVE_COLUMNS_STORAGE_KEY = 'zbs.positions.columns.active'
export const POSITION_PENDING_COLUMNS_STORAGE_KEY = 'zbs.positions.columns.pending'
