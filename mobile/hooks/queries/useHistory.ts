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
