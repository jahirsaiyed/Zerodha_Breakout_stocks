export interface User {
  id: number;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
  active: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
}

export interface Signal {
  id: number;
  symbol: string;
  entryPrice: number;
  targetPrice: number;
  stopLossPrice: number;
  status: 'PENDING' | 'ACTIVE' | 'CLOSED' | 'CANCELLED';
  source: string;
  createdAt: string;
}

export interface Position {
  id: number;
  signal: Signal;
  quantity: number;
  entryPrice: number;
  ltp?: number;
  unrealisedPnl?: number;
}

export interface ClosedTrade {
  id: number;
  symbol: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  realisedPnl: number;
  closedAt: string;
}

export interface UserConfig {
  maxPositions: number;
  positionSizingMethod: 'FIXED' | 'EQUAL' | 'RISK_BASED';
  positionSizingValue: number;
  marginUsagePercent: number;
  marginUsageFixedLimit: number | null;
  tradingPaused: boolean;
  syncPaused: boolean;
  zerodhaConnected: boolean;
  telegramChatId: string | null;
}

export interface AccountSummary {
  availableMargin: number | null;
  activePositions: number;
  maxPositions: number | null;
  positionSizingValue: number | null;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error: string | null;
}
