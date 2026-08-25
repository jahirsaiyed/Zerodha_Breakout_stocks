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
