import { describe, it, expect, beforeEach } from 'vitest'
import { screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Routes, Route } from 'react-router-dom'
import { LoginPage } from '../pages/LoginPage'
import { renderWithAuth, REGULAR_USER } from './helpers'
import api from '../lib/api'

vi.mock('../lib/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

// Safe typed access to the mocked api post
const mockPost = () => (api.post as ReturnType<typeof vi.fn>)

beforeEach(() => {
  vi.clearAllMocks()
})

/** Gets email and password inputs by their name attribute. */
function getInputs(container: HTMLElement) {
  const email = container.querySelector<HTMLInputElement>('input[name="email"]')!
  const password = container.querySelector<HTMLInputElement>('input[name="password"]')!
  return { email, password }
}

describe('LoginPage', () => {
  it('renders email and password fields and submit button', () => {
    const { container } = renderWithAuth(
      <Routes><Route path="/" element={<LoginPage />} /></Routes>
    )

    const { email, password } = getInputs(container)
    expect(email).toBeInTheDocument()
    expect(password).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('does not render form when user is already logged in', () => {
    renderWithAuth(
      <Routes>
        <Route path="/" element={<LoginPage />} />
      </Routes>,
      { user: REGULAR_USER }
    )
    expect(screen.queryByRole('button', { name: /sign in/i })).not.toBeInTheDocument()
  })

  it('calls POST /auth/login with email and password on submit', async () => {
    mockPost().mockResolvedValue({ data: {} })

    const { container } = renderWithAuth(
      <Routes><Route path="/" element={<LoginPage />} /></Routes>
    )

    const { email, password } = getInputs(container)
    await userEvent.type(email, 'alice@test.com')
    await userEvent.type(password, 'password123')
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(mockPost()).toHaveBeenCalledWith('/auth/login', {
        email: 'alice@test.com',
        password: 'password123',
      })
    })
  })

  it('shows error message when login fails', async () => {
    mockPost().mockRejectedValue(new Error('Unauthorized'))

    const { container } = renderWithAuth(
      <Routes><Route path="/" element={<LoginPage />} /></Routes>
    )

    const { email, password } = getInputs(container)
    await userEvent.type(email, 'bad@test.com')
    await userEvent.type(password, 'wrongpass')
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument()
    })
  })

  it('disables sign-in button while submitting', async () => {
    mockPost().mockReturnValue(new Promise(() => {})) // never resolves

    const { container } = renderWithAuth(
      <Routes><Route path="/" element={<LoginPage />} /></Routes>
    )

    const { email, password } = getInputs(container)
    await userEvent.type(email, 'alice@test.com')
    await userEvent.type(password, 'password123')
    fireEvent.submit(screen.getByRole('button', { name: /sign in/i }).closest('form')!)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /signing in/i })).toBeDisabled()
    })
  })
})
