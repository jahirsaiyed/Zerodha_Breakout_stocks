import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { api } from '../lib/api';
import type { User, TokenResponse } from '../lib/types';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restoreSession: () => Promise<boolean>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: false,

  login: async (email, password) => {
    const { data } = await api.post<{ data: TokenResponse }>('/api/auth/token', { email, password });
    const { accessToken, refreshToken } = data.data;
    await SecureStore.setItemAsync('accessToken', accessToken);
    await SecureStore.setItemAsync('refreshToken', refreshToken);
    const meRes = await api.get<{ data: User }>('/api/users/me');
    set({ user: meRes.data.data, isAuthenticated: true });
  },

  logout: async () => {
    const refreshToken = await SecureStore.getItemAsync('refreshToken');
    if (refreshToken) {
      try {
        await api.post('/api/auth/revoke', { refreshToken });
      } catch {
        // ignore revoke errors — tokens are cleared locally regardless
      }
    }
    await SecureStore.deleteItemAsync('accessToken');
    await SecureStore.deleteItemAsync('refreshToken');
    set({ user: null, isAuthenticated: false });
  },

  restoreSession: async () => {
    try {
      const token = await SecureStore.getItemAsync('accessToken');
      if (!token) return false;
      const meRes = await api.get<{ data: User }>('/api/users/me');
      set({ user: meRes.data.data, isAuthenticated: true });
      return true;
    } catch {
      return false;
    }
  },
}));
