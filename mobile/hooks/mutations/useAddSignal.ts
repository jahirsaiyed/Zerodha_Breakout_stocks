import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useAddSignal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: {
      symbol: string;
      entryPrice: number;
      targetPrice: number;
      stopLossPrice: number;
    }) => api.post('/api/signals', payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  });
}
