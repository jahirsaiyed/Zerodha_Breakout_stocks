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
    // If this page is ever reached, forward all query params to settings
    const zerodha = params.get('zerodha') ?? params.get('status') ?? 'error'
    navigate(`/settings?zerodha=${zerodha}`, { replace: true })
  }, [navigate, params])

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50">
      <div className="text-sm text-gray-500">Completing Zerodha connection…</div>
    </div>
  )
}
