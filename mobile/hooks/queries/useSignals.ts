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
