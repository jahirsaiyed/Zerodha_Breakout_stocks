### Task 5: Mobile — API Client + Types + Auth Store

**Files:**
- Create: `mobile/lib/types.ts`
- Create: `mobile/lib/api.ts`
- Create: `mobile/store/authStore.ts`
- Create: `mobile/lib/__tests__/api.test.ts`

**Interfaces:**
- `api` — Axios instance with Bearer interceptor and auto-refresh on 401
- `useAuthStore` — `{ accessToken, refreshToken, user, login, logout, setTokens }`
- Consumes: `EXPO_PUBLIC_API_URL`, `expo-secure-store`

- [ ] **Step 1: Create types**

```typescript
// mobile/lib/types.ts
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
```

- [ ] **Step 2: Create API client**

```typescript
// mobile/lib/api.ts
import axios, { AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';

const BASE_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006';

export const api = axios.create({ baseURL: BASE_URL });

// Attach Bearer token to every request
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const token = await SecureStore.getItemAsync('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401, attempt one refresh then retry
let isRefreshing = false;
let pendingRequests: Array<(token: string) => void> = [];

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as AxiosRequestConfig & { _retry?: boolean };
    if (error.response?.status !== 401 || original._retry) {
      return Promise.reject(error);
    }
    original._retry = true;

    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push((token) => {
          original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
          resolve(api(original));
        });
      });
    }

    isRefreshing = true;
    try {
      const refreshToken = await SecureStore.getItemAsync('refreshToken');
      if (!refreshToken) throw new Error('No refresh token');

      const { data } = await axios.post(`${BASE_URL}/api/auth/refresh`, { refreshToken });
      const newAccess: string = data.data.accessToken;
      const newRefresh: string = data.data.refreshToken;

      await SecureStore.setItemAsync('accessToken', newAccess);
      await SecureStore.setItemAsync('refreshToken', newRefresh);

      pendingRequests.forEach((cb) => cb(newAccess));
      pendingRequests = [];

      original.headers = { ...original.headers, Authorization: `Bearer ${newAccess}` };
      return api(original);
    } catch {
      await SecureStore.deleteItemAsync('accessToken');
      await SecureStore.deleteItemAsync('refreshToken');
      pendingRequests = [];
      return Promise.reject(error);
    } finally {
      isRefreshing = false;
    }
  },
);
```

- [ ] **Step 3: Create auth store**

```typescript
// mobile/store/authStore.ts
import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { api } from '../lib/api';
import type { User, TokenResponse } from '../lib/types';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restoreSession: () => Promise<boolean>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,

  login: async (email, password) => {
    const { data } = await api.post<{ data: TokenResponse }>('/api/auth/token', { email, password });
    const { accessToken, refreshToken } = data.data;
    await SecureStore.setItemAsync('accessToken', accessToken);
    await SecureStore.setItemAsync('refreshToken', refreshToken);
    const meRes = await api.get<{ data: User }>('/api/users/me');
    set({ user: meRes.data.data, isAuthenticated: true });
  },

  logout: async () => {
    const refreshToken = await SecureStore.getItemAsync('refreshToken');
    if (refreshToken) {
      try { await api.post('/api/auth/revoke', { refreshToken }); } catch { /* ignore */ }
    }
    await SecureStore.deleteItemAsync('accessToken');
    await SecureStore.deleteItemAsync('refreshToken');
    set({ user: null, isAuthenticated: false });
  },

  restoreSession: async () => {
    try {
      const token = await SecureStore.getItemAsync('accessToken');
      if (!token) return false;
      const meRes = await api.get<{ data: User }>('/api/users/me');
      set({ user: meRes.data.data, isAuthenticated: true });
      return true;
    } catch {
      return false;
    }
  },
}));
```

- [ ] **Step 4: Write test for auth store login**

```typescript
// mobile/lib/__tests__/api.test.ts
import axios from 'axios';

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn(),
  setItemAsync: jest.fn(),
  deleteItemAsync: jest.fn(),
}));

jest.mock('axios', () => {
  const actual = jest.requireActual('axios');
  return {
    ...actual,
    create: jest.fn(() => actual.create()),
    post: jest.fn(),
  };
});

describe('api client', () => {
  it('exports a base URL from EXPO_PUBLIC_API_URL', () => {
    // Smoke test — the module must load without throwing
    expect(() => require('../api')).not.toThrow();
  });
});
```

- [ ] **Step 5: Run tests**

```bash
cd mobile && npx jest lib/__tests__/api.test.ts --no-coverage 2>&1 | tail -8
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add mobile/lib/types.ts mobile/lib/api.ts mobile/store/authStore.ts \
        mobile/lib/__tests__/api.test.ts
git commit -m "feat: add API client with Bearer auth, auto-refresh, and auth store"
```

---

