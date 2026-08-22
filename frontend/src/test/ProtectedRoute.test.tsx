import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { renderWithAuth, REGULAR_USER } from './helpers'

describe('ProtectedRoute', () => {
  it('shows loading indicator while auth is resolving', () => {
    renderWithAuth(
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<div>Dashboard</div>} />
        </Route>
      </Routes>,
      { isLoading: true }
    )
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('redirects to /login when user is not authenticated', () => {
    renderWithAuth(
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<div>Dashboard</div>} />
        </Route>
      </Routes>,
      { user: null, isLoading: false }
    )
    expect(screen.getByText('Login Page')).toBeInTheDocument()
    expect(screen.queryByText('Dashboard')).not.toBeInTheDocument()
  })

  it('renders outlet when user is authenticated', () => {
    renderWithAuth(
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/" element={<div>Dashboard</div>} />
        </Route>
      </Routes>,
      { user: REGULAR_USER, isLoading: false }
    )
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })
})
