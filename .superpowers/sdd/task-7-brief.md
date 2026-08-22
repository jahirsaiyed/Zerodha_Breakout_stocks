# Task 7 Brief: React Frontend Scaffold + Login

## Context
Task 7 of 7 in Phase 1 (Foundation) of a Trading Portfolio Management System.
Project root: `D:\Zerodha_Breakout_stocks`
Branch: main

## Global Constraints
- React 18, TypeScript 5, Vite 5
- TanStack Query v5 (package: `@tanstack/react-query`)
- React Router v6 (package: `react-router-dom`)
- Tailwind CSS 3
- Axios with `withCredentials: true` (JWT is in httpOnly cookie)
- All API calls go to `/api/*` (Vite dev proxy → `http://localhost:8080`)
- Protected routes redirect to `/login` when unauthenticated (401 → redirect in Axios interceptor)

## What already exists in frontend/
- `frontend/Dockerfile` — do NOT touch (created in Task 1)
- `frontend/nginx.conf` — do NOT touch (created in Task 1)

## Steps

### Step 1: Scaffold Vite React TypeScript app
```bash
cd /d/Zerodha_Breakout_stocks
npm create vite@latest frontend -- --template react-ts --force
# If --force doesn't exist or doesn't work, scaffold to a temp dir and copy files
cd frontend
npm install
npm install react-router-dom @tanstack/react-query axios
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

Note: The `frontend/` directory already has Dockerfile and nginx.conf. After scaffolding, make sure those files are preserved (don't overwrite them). If Vite would conflict, scaffold to a temp directory first and copy files manually.

### Step 2: Configure Tailwind — replace tailwind.config.js content
```js
// frontend/tailwind.config.js
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: {} },
  plugins: [],
}
```

### Step 3: Add Tailwind directives to src/index.css (replace entire file)
```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

### Step 4: Configure Vite proxy — replace vite.config.ts
```ts
// frontend/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

### Step 5: Create src/lib/api.ts
```ts
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  withCredentials: true,
})

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
```

### Step 6: Create src/contexts/AuthContext.tsx
```tsx
import { createContext, useContext, ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import api from '../lib/api'

interface User { id: number; name: string; email: string; role: string; active: boolean }
interface AuthCtx { user: User | null; isLoading: boolean; logout: () => void }

const AuthContext = createContext<AuthCtx>({ user: null, isLoading: true, logout: () => {} })

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
```

### Step 7: Create src/components/ProtectedRoute.tsx
```tsx
import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'

export function ProtectedRoute() {
  const { user, isLoading } = useAuth()
  if (isLoading) return (
    <div className="flex h-screen items-center justify-center text-gray-500">Loading...</div>
  )
  if (!user) return <Navigate to="/login" replace />
  return <Outlet />
}
```

### Step 8: Create src/pages/LoginPage.tsx
```tsx
import { useState, FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../contexts/AuthContext'
import api from '../lib/api'

export function LoginPage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  if (user) return <Navigate to="/" replace />

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    const form = new FormData(e.currentTarget)
    try {
      await api.post('/auth/login', {
        email: form.get('email'),
        password: form.get('password'),
      })
      await queryClient.invalidateQueries({ queryKey: ['me'] })
      navigate('/')
    } catch {
      setError('Invalid email or password')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm rounded-xl bg-white p-8 shadow-md">
        <h1 className="mb-2 text-2xl font-bold text-gray-900">Trading System</h1>
        <p className="mb-6 text-sm text-gray-500">Sign in to your account</p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700">Email</label>
            <input name="email" type="email" required autoFocus
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm
                         focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700">Password</label>
            <input name="password" type="password" required
              className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm
                         focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500" />
          </div>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <button type="submit" disabled={loading}
            className="w-full rounded-lg bg-blue-600 py-2 text-sm font-semibold text-white
                       hover:bg-blue-700 disabled:opacity-60">
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>
      </div>
    </div>
  )
}
```

### Step 9: Create src/pages/DashboardPage.tsx
```tsx
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
```

### Step 10: Replace src/App.tsx
```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './contexts/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { DashboardPage } from './pages/DashboardPage'

const queryClient = new QueryClient()

function AppRoutes() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
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
```

### Step 11: Replace src/main.tsx
```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
)
```

### Step 12: Verify TypeScript compiles
```bash
cd /d/Zerodha_Breakout_stocks/frontend
npx tsc --noEmit
```
Expected: no errors.

### Step 13: Build succeeds
```bash
npm run build
```
Expected: BUILD successful, dist/ directory created.

### Step 14: Verify Dockerfile still exists and nginx.conf unchanged
```bash
ls /d/Zerodha_Breakout_stocks/frontend/
# Should show: Dockerfile, nginx.conf, package.json, vite.config.ts, etc.
```

### Step 15: Commit
```bash
cd /d/Zerodha_Breakout_stocks
git add frontend/ && git commit -m "feat: React frontend scaffold with login, protected routing, AuthContext"
```

## Report
Write your report to: `D:\Zerodha_Breakout_stocks\.superpowers\sdd\task-7-report.md`

Include:
- STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- Files created (list)
- Build result (TypeScript check + npm run build output summary)
- Any deviations from the plan
- Commits made (hash + message)
