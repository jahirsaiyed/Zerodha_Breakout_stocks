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
