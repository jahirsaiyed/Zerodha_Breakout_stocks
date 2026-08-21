import { createContext, useContext } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import api from '../lib/api'

interface User { id: number; name: string; email: string; role: string; active: boolean }
interface AuthCtx { user: User | null; isLoading: boolean; logout: () => Promise<void> }

const AuthContext = createContext<AuthCtx>({ user: null, isLoading: true, logout: () => Promise.resolve() })

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const { data: user, isLoading } = useQuery<User>({
    queryKey: ['me'],
    queryFn: () => api.get('/users/me').then(r => r.data.data),
    retry: false,
    staleTime: 5 * 60 * 1000,
  })

  const logout = async () => {
    await api.delete('/auth/logout').catch(() => {})
    queryClient.clear()
    navigate('/login')
  }

  return (
    <AuthContext.Provider value={{ user: user ?? null, isLoading, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)
