import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

/**
 * Handles the edge-case where the Zerodha OAuth callback lands on the frontend
 * instead of being intercepted by the backend. In normal production setup,
 * Nginx routes /api/zerodha/callback to the backend, which then redirects to
 * /settings?zerodha=connected. This page is a safety fallback.
 */
export function ZerodhaCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const requestToken = params.get('request_token')
    const status = params.get('status')

    if (requestToken && status) {
      // Raw Zerodha OAuth callback — forward to the backend to exchange the token
      window.location.replace(
        `/api/zerodha/callback?request_token=${encodeURIComponent(requestToken)}&status=${encodeURIComponent(status)}`
      )
      return
    }

    // Already-processed result forwarded from the backend (e.g. zerodha=connected).
    // Allow-list valid values to prevent open-redirect via a crafted query string.
    const raw = params.get('zerodha')
    const VALID_STATES = new Set(['connected', 'error', 'disconnected'])
    const zerodha = raw && VALID_STATES.has(raw) ? raw : 'error'
    navigate(`/settings?zerodha=${zerodha}`, { replace: true })
  }, [navigate, params])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-sm text-gray-500">Completing Zerodha connection…</div>
    </div>
  )
}
