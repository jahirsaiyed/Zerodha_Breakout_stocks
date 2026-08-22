import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './contexts/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'
import { PositionsPage } from './pages/PositionsPage'
import { SignalsPage } from './pages/SignalsPage'
import { HistoryPage } from './pages/HistoryPage'
import { OrdersPage } from './pages/OrdersPage'
import { SettingsPage } from './pages/SettingsPage'
import { AdminPage } from './pages/AdminPage'
import { ZerodhaCallbackPage } from './pages/ZerodhaCallbackPage'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 60_000 } },
})

function AppRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/zerodha/callback" element={<ZerodhaCallbackPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/"          element={<DashboardPage />} />
            <Route path="/positions" element={<PositionsPage />} />
            <Route path="/signals"   element={<SignalsPage />} />
            <Route path="/history"   element={<HistoryPage />} />
            <Route path="/orders"    element={<OrdersPage />} />
            <Route path="/settings"  element={<SettingsPage />} />
            <Route path="/admin"     element={<AdminPage />} />
            <Route path="*"          element={<Navigate to="/" replace />} />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
