### Task 7: Mobile — Query Hooks + Mutation Hooks

**Files:**
- Create: `mobile/hooks/queries/useAccountSummary.ts`
- Create: `mobile/hooks/queries/useSignals.ts`
- Create: `mobile/hooks/queries/useSignal.ts`
- Create: `mobile/hooks/queries/usePortfolio.ts`
- Create: `mobile/hooks/queries/useHistory.ts`
- Create: `mobile/hooks/queries/useUserConfig.ts`
- Create: `mobile/hooks/mutations/useSyncSignals.ts`
- Create: `mobile/hooks/mutations/useAddSignal.ts`
- Create: `mobile/hooks/mutations/useCancelPending.ts`
- Create: `mobile/hooks/mutations/useClosePosition.ts`
- Create: `mobile/hooks/mutations/useUpdateConfig.ts`
- Create: `mobile/hooks/mutations/useConnectTelegram.ts`
- Create: `mobile/hooks/__tests__/useSignals.test.ts`

**Interfaces:**
- All hooks consume `api` from `mobile/lib/api.ts` and types from `mobile/lib/types.ts`
- All queries return TanStack Query `UseQueryResult`

- [ ] **Step 1: Create query hooks**

```typescript
// mobile/hooks/queries/useAccountSummary.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { AccountSummary, ApiResponse } from '../../lib/types';

export function useAccountSummary() {
  return useQuery({
    queryKey: ['account-summary'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<AccountSummary>>('/api/users/me/account-summary');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useSignals.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Signal, ApiResponse } from '../../lib/types';

export function useSignals(status?: string) {
  return useQuery({
    queryKey: ['signals', status],
    queryFn: async () => {
      const params = status ? { status } : {};
      const { data } = await api.get<ApiResponse<Signal[]>>('/api/signals', { params });
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useSignal.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Signal, ApiResponse } from '../../lib/types';

export function useSignal(id: number) {
  return useQuery({
    queryKey: ['signal', id],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Signal>>(`/api/signals/${id}`);
      return data.data;
    },
    enabled: !!id,
  });
}
```

```typescript
// mobile/hooks/queries/usePortfolio.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { Position, ApiResponse } from '../../lib/types';

export function usePortfolio() {
  return useQuery({
    queryKey: ['portfolio'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<Position[]>>('/api/portfolio');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useHistory.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { ClosedTrade, ApiResponse } from '../../lib/types';

export function useHistory() {
  return useQuery({
    queryKey: ['history'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<ClosedTrade[]>>('/api/portfolio/history');
      return data.data;
    },
  });
}
```

```typescript
// mobile/hooks/queries/useUserConfig.ts
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { UserConfig, ApiResponse } from '../../lib/types';

export function useUserConfig() {
  return useQuery({
    queryKey: ['user-config'],
    queryFn: async () => {
      const { data } = await api.get<ApiResponse<UserConfig>>('/api/users/me/config');
      return data.data;
    },
  });
}
```

- [ ] **Step 2: Create mutation hooks**

```typescript
// mobile/hooks/mutations/useSyncSignals.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useSyncSignals() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post('/api/signals/sync'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useCancelPending.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useCancelPending() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (signalId: number) => api.post(`/api/signals/${signalId}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] });
      qc.invalidateQueries({ queryKey: ['portfolio'] });
    },
  });
}
```

```typescript
// mobile/hooks/mutations/useClosePosition.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useClosePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (signalId: number) => api.post(`/api/signals/${signalId}/close`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['portfolio'] });
      qc.invalidateQueries({ queryKey: ['signals'] });
    },
  });
}
```

```typescript
// mobile/hooks/mutations/useUpdateConfig.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { UserConfig } from '../../lib/types';

export function useUpdateConfig() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (config: Partial<UserConfig>) => api.put('/api/users/me/config', config),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['user-config'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useConnectTelegram.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useConnectTelegram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (botToken: string) => api.post('/api/users/me/telegram/bot', { botToken }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['user-config'] }),
  });
}
```

```typescript
// mobile/hooks/mutations/useAddSignal.ts
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useAddSignal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: {
      symbol: string;
      entryPrice: number;
      targetPrice: number;
      stopLossPrice: number;
    }) => api.post('/api/signals', payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
```

- [ ] **Step 3: Write test for useSignals**

```typescript
// mobile/hooks/__tests__/useSignals.test.ts
import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useSignals } from '../queries/useSignals';
import { api } from '../../lib/api';

jest.mock('../../lib/api', () => ({ api: { get: jest.fn() } }));

const wrapper = ({ children }: { children: React.ReactNode }) => (
  React.createElement(QueryClientProvider, {
    client: new QueryClient({ defaultOptions: { queries: { retry: false } } }),
  }, children)
);

describe('useSignals', () => {
  it('returns signal list from API', async () => {
    const signals = [{ id: 1, symbol: 'RELIANCE', status: 'PENDING' }];
    (api.get as jest.Mock).mockResolvedValue({ data: { data: signals } });

    const { result } = renderHook(() => useSignals(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(signals);
  });
});
```

- [ ] **Step 4: Run tests**

```bash
cd mobile && npx jest hooks/__tests__/useSignals.test.ts --no-coverage 2>&1 | tail -8
```

Expected: test passes.

- [ ] **Step 5: Commit**

```bash
git add mobile/hooks/
git commit -m "feat: add TanStack Query data hooks for all mobile screens"
```

---

