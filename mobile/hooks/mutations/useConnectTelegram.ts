import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';

export function useConnectTelegram() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (botToken: string) => api.post('/api/users/me/telegram/bot', { botToken }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['user-config'] }),
  });
}
