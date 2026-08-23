export interface Signal {
  id: number
  symbol: string
  entryPrice: number
  stopLoss: number
  target: number
  riskRewardRatio: number
  source: 'GOOGLE_SHEET' | 'MANUAL'
  sourceRef: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED'
  notes: string | null
  addedAt: string
  updatedAt: string
}

export interface Position {
  id: number
  symbol: string
  quantity: number
  avgEntryPrice: number | null
  entryOrderId: string | null
  gttOrderId: string | null
  status: 'PENDING_ENTRY' | 'ACTIVE' | 'CANCELLED' | 'CLOSED_TARGET' | 'CLOSED_SL' | 'CLOSED_MANUAL'
  openedAt: string | null
  closedAt: string | null
  realisedPnl: number | null
  signalId: number | null
  signalEntryPrice: number | null
  signalStopLoss: number | null
  signalTarget: number | null
}

export interface UserConfig {
  maxPositions: number
  positionSizingMethod: 'EQUAL' | 'FIXED' | 'RISK_BASED'
  positionSizingValue: number
  orderExpiryDays: number
  telegramChatId: string | null
  zerodhaConnected: boolean
  hasTotpSecret: boolean
}

export interface AccountSummary {
  availableMargin: number | null
  activePositions: number
  maxPositions: number
  positionSizingValue: number
}

export interface AdminUser {
  id: number
  name: string
  email: string
  role: 'USER' | 'ADMIN'
  active: boolean
  createdAt: string
}

export interface LivePosition {
  id: number
  symbol: string
  quantity: number
  avgEntryPrice: number | null
  signalStopLoss: number | null
  signalTarget: number | null
  status: string
  ltp: number | null
  unrealisedPnl: number | null
}

export interface Order {
  id: number
  symbol: string
  type: 'ENTRY' | 'EXIT_TARGET' | 'EXIT_SL' | 'EXIT_MANUAL'
  orderKind: 'LIMIT' | 'GTT' | string
  quantity: number
  price: number | null
  status: 'PENDING' | 'FILLED' | 'CANCELLED' | 'REJECTED'
  zerodhaOrderId: string | null
  positionId: number | null
  placedAt: string
  updatedAt: string
}

export interface TelegramChat {
  chatId: string
  chatTitle: string
  chatType: string
}

export interface HealthResponse {
  instrumentCacheSize: number
  instrumentCacheLoaded: boolean
  lastSyncAt: string | null
  lastSyncAdded: number
  lastSyncModified: number
  zerodhaStatuses: { userId: number; email: string; connected: boolean }[]
}
