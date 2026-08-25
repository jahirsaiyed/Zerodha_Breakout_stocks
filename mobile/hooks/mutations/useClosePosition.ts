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
