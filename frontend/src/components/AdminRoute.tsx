import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export function AdminRoute() {
  const { user, isLoading } = useAuth()
  if (isLoading) return null
  if (!user || user.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}
