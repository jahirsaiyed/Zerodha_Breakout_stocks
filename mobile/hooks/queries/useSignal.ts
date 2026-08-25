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
