import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import { AdminRoute } from '../components/AdminRoute'
import { renderWithAuth, ADMIN_USER, REGULAR_USER } from './helpers'

describe('AdminRoute', () => {
  it('shows nothing while auth is loading', () => {
    const { container } = renderWithAuth(
      <Routes>
        <Route element={<AdminRoute />}>
          <Route path="/" element={<div>Admin Panel</div>} />
        </Route>
      </Routes>,
      { isLoading: true }
    )
    expect(container.firstChild).toBeNull()
  })

  it('redirects non-admin user to /', () => {
    renderWithAuth(
      <Routes>
        <Route path="/" element={<div>Home</div>} />
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<div>Admin Panel</div>} />
        </Route>
      </Routes>,
      { user: REGULAR_USER, isLoading: false, initialPath: '/admin' }
    )
    expect(screen.getByText('Home')).toBeInTheDocument()
    expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument()
  })

  it('redirects unauthenticated user to /', () => {
    renderWithAuth(
      <Routes>
        <Route path="/" element={<div>Home</div>} />
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<div>Admin Panel</div>} />
        </Route>
      </Routes>,
      { user: null, isLoading: false, initialPath: '/admin' }
    )
    expect(screen.getByText('Home')).toBeInTheDocument()
  })

  it('renders admin outlet for ADMIN role user', () => {
    renderWithAuth(
      <Routes>
        <Route element={<AdminRoute />}>
          <Route path="/" element={<div>Admin Panel</div>} />
        </Route>
      </Routes>,
      { user: ADMIN_USER, isLoading: false }
    )
    expect(screen.getByText('Admin Panel')).toBeInTheDocument()
  })
})
