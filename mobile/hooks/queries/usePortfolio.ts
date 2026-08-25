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
