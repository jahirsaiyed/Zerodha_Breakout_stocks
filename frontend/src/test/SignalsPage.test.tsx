import { describe, it, expect, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SignalsPage } from '../pages/SignalsPage'
import { renderWithAuth, REGULAR_USER } from './helpers'
import api from '../lib/api'
import type { Signal, SignalQuote, Position } from '../lib/types'
import { SIGNAL_COLUMNS_STORAGE_KEY } from '../lib/signalsColumns'

vi.mock('../lib/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockGet = () => (api.get as ReturnType<typeof vi.fn>)

const SIGNAL: Signal = {
  id: 1,
  symbol: 'RELIANCE',
  entryPrice: 2500,
  stopLoss: 2400,
  target: 2700,
  riskRewardRatio: 2,
  source: 'GOOGLE_SHEET',
  sourceRef: null,
  status: 'ACTIVE',
  closingBasis: 'DAILY',
  notes: null,
  addedAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
}

const QUOTE: SignalQuote = { signalId: 1, rank: 1, ltp: 2550, diffFromEntryPct: 2.0 }

function mockApiResponses(overrides?: { signals?: Signal[]; quotes?: SignalQuote[]; positions?: Position[] }) {
  const signals = overrides?.signals ?? [SIGNAL]
  const quotes = overrides?.quotes ?? [QUOTE]
  const positions = overrides?.positions ?? []
  mockGet().mockImplementation((url: string) => {
    if (url === '/signals') return Promise.resolve({ data: { data: signals } })
    if (url === '/signals/quotes') return Promise.resolve({ data: { data: quotes } })
    if (url === '/portfolio/positions') return Promise.resolve({ data: positions })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

function renderSignals() {
  return renderWithAuth(<SignalsPage />, { user: REGULAR_USER })
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('SignalsPage', () => {
  it('renders the table with default column order', async () => {
    mockApiResponses()
    renderSignals()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers).toEqual(['#', 'Symbol', 'Entry', 'Stop Loss', 'SL Basis', 'Target', 'R:R', 'LTP', 'vs Entry', 'Source', 'Status', ''])
  })

  it('reorders columns via the customize panel and persists the order', async () => {
    mockApiResponses()
    renderSignals()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Customize columns' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move LTP up' }))
    await userEvent.click(document.body)

    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers.indexOf('LTP')).toBeLessThan(headers.indexOf('R:R'))

    const saved = JSON.parse(localStorage.getItem(SIGNAL_COLUMNS_STORAGE_KEY) ?? '[]')
    expect(saved.indexOf('ltp')).toBeLessThan(saved.indexOf('riskReward'))
  })

  it('keeps the edit row usable after columns are reordered — entry/stop-loss/target become inputs', async () => {
    mockApiResponses()
    renderSignals()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())

    // Reorder first: move SL Basis before Entry.
    await userEvent.click(screen.getByRole('button', { name: 'Customize columns' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move SL Basis up' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move SL Basis up' }))
    await userEvent.click(document.body)

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))

    // The row switches to edit mode: entry/stop-loss/target are text inputs with their values,
    // regardless of where they now sit in the column order.
    expect(screen.getByDisplayValue('2500')).toBeInTheDocument() // entryPrice
    expect(screen.getByDisplayValue('2400')).toBeInTheDocument() // stopLoss
    expect(screen.getByDisplayValue('2700')).toBeInTheDocument() // target
    // R:R / LTP / vs Entry collapse to placeholders while editing.
    expect(screen.getAllByText('—').length).toBeGreaterThanOrEqual(3)
  })

  it('restores a previously saved column order on load', async () => {
    localStorage.setItem(SIGNAL_COLUMNS_STORAGE_KEY, JSON.stringify(
      ['status', 'source', 'entry', 'stopLoss', 'slBasis', 'target', 'riskReward', 'ltp', 'vsEntry']
    ))
    mockApiResponses()
    renderSignals()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers[2]).toBe('Status')
  })
})
