import { useEffect, useRef } from 'react';
import { AppState, AppStateStatus } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { usePortfolioStore } from '../store/portfolioStore';

const WS_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:9006')
  .replace(/^http/, 'ws') + '/ws/ltp';

export function usePortfolioLtp() {
  const wsRef = useRef<WebSocket | null>(null);
  const setLtp = usePortfolioStore((s) => s.setLtp);
  const clearLtp = usePortfolioStore((s) => s.clearLtp);

  const connect = async () => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;
    const token = await SecureStore.getItemAsync('accessToken');
    if (!token) return;

    const ws = new WebSocket(`${WS_URL}?token=${token}`);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      try {
        const payload: { signalId: number; ltp: number } = JSON.parse(event.data);
        setLtp(payload.signalId, payload.ltp);
      } catch { /* ignore malformed messages */ }
    };

    ws.onerror = () => ws.close();
    ws.onclose = () => { wsRef.current = null; };
  };

  const disconnect = () => {
    wsRef.current?.close();
    wsRef.current = null;
    clearLtp();
  };

  useEffect(() => {
    connect();

    const sub = AppState.addEventListener('change', (state: AppStateStatus) => {
      if (state === 'active') connect();
      else disconnect();
    });

    return () => {
      sub.remove();
      disconnect();
    };
  }, []);
}
