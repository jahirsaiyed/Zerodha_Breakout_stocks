import { useAuth } from '../contexts/AuthContext'

export function DashboardPage() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen bg-gray-50 p-8">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <button onClick={logout}
          className="rounded-lg border border-gray-300 px-4 py-2 text-sm hover:bg-gray-100">
          Sign Out
        </button>
      </div>
      <p className="mt-4 text-gray-600">Welcome, {user?.name}. More features coming in Phase 6.</p>
    </div>
  )
}
