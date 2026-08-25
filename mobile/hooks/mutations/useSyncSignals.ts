import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useSyncSignals() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post('/api/signals/sync'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
