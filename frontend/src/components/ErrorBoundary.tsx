import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { error: Error | null }

function isChunkLoadError(error: Error): boolean {
  return (
    error.name === 'ChunkLoadError' ||
    error.message.includes('Failed to fetch dynamically imported module') ||
    error.message.includes('Importing a module script failed')
  )
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Uncaught error:', error, info.componentStack)
    // Stale deployment: chunk hash changed — reload to get fresh HTML.
    if (isChunkLoadError(error)) {
      window.location.reload()
    }
  }

  render() {
    const { error } = this.state
    if (error) {
      if (isChunkLoadError(error)) {
        // Reload is already in flight; show a neutral message while waiting.
        return (
          <div className="flex h-screen items-center justify-center bg-gray-50">
            <p className="text-sm text-gray-400">Updating app… reloading</p>
          </div>
        )
      }
      return (
        <div className="flex h-screen flex-col items-center justify-center gap-4 bg-gray-50 p-8 text-center">
          <div className="rounded-xl border border-red-200 bg-white p-8 max-w-md w-full">
            <p className="text-sm font-semibold text-red-600">Something went wrong</p>
            <p className="mt-2 text-xs text-gray-500 break-words">{error.message}</p>
            <button
              onClick={() => this.setState({ error: null })}
              className="mt-4 rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-600"
            >
              Try again
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
