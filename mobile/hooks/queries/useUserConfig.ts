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
