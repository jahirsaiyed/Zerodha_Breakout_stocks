export type SignalColumnKey =
  | 'entry' | 'stopLoss' | 'slBasis' | 'target' | 'riskReward' | 'ltp' | 'vsEntry' | 'source' | 'status'

export const SIGNAL_COLUMN_LABELS: Record<SignalColumnKey, string> = {
  entry: 'Entry',
  stopLoss: 'Stop Loss',
  slBasis: 'SL Basis',
  target: 'Target',
  riskReward: 'R:R',
  ltp: 'LTP',
  vsEntry: 'vs Entry',
  source: 'Source',
  status: 'Status',
}

export const DEFAULT_SIGNAL_COLUMNS: SignalColumnKey[] =
  ['entry', 'stopLoss', 'slBasis', 'target', 'riskReward', 'ltp', 'vsEntry', 'source', 'status']

export const SIGNAL_COLUMNS_STORAGE_KEY = 'zbs.signals.columns'
