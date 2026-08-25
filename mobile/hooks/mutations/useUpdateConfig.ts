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
