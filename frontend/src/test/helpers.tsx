/**
 * Shared test utilities — wrap components with the providers they need.
 */
import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthContext } from '../contexts/AuthContext'

interface User { id: number; name: string; email: string; role: string; active: boolean }

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

interface RenderWithAuthOptions {
  user?: User | null
  isLoading?: boolean
  initialPath?: string
}

/**
 * Renders `ui` inside MemoryRouter + QueryClientProvider + AuthContext.
 * The auth context is driven by the provided `user` and `isLoading` values,
 * so no API calls are made during tests.
 */
export function renderWithAuth(
  ui: ReactNode,
  { user = null, isLoading = false, initialPath = '/' }: RenderWithAuthOptions = {}
) {
  const logout = vi.fn().mockResolvedValue(undefined)

  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <AuthContext.Provider value={{ user, isLoading, logout }}>
          {ui}
        </AuthContext.Provider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

export const ADMIN_USER: User = { id: 1, name: 'Admin', email: 'admin@test.com', role: 'ADMIN', active: true }
export const REGULAR_USER: User = { id: 2, name: 'Alice', email: 'alice@test.com', role: 'USER', active: true }
