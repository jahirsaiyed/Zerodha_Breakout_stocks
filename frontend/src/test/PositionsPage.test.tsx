import { describe, it, expect, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PositionsPage } from '../pages/PositionsPage'
import { renderWithAuth, REGULAR_USER } from './helpers'
import api from '../lib/api'
import type { LivePosition, Position } from '../lib/types'
import { POSITION_ACTIVE_COLUMNS_STORAGE_KEY, POSITION_PENDING_COLUMNS_STORAGE_KEY } from '../lib/positionsColumns'

vi.mock('../lib/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

const mockGet = () => (api.get as ReturnType<typeof vi.fn>)

const ACTIVE_POSITION: Position = {
  id: 1,
  symbol: 'RELIANCE',
  quantity: 10,
  avgEntryPrice: 2500,
  entryOrderId: null,
  gttOrderId: null,
  status: 'ACTIVE',
  entrySource: 'AUTO',
  openedAt: '2026-09-01T00:00:00Z',
  closedAt: null,
  realisedPnl: null,
  signalId: 1,
  signalEntryPrice: 2500,
  signalStopLoss: 2400,
  signalTarget: 2700,
  breakevenSl: null,
}

const PENDING_POSITION: Position = {
  ...ACTIVE_POSITION,
  id: 2,
  symbol: 'TCS',
  status: 'PENDING_ENTRY',
}

const LIVE_POSITION: LivePosition = {
  id: 1,
  symbol: 'RELIANCE',
  quantity: 10,
  avgEntryPrice: 2500,
  signalStopLoss: 2400,
  signalTarget: 2700,
  status: 'ACTIVE',
  ltp: 2550,
  unrealisedPnl: 500,
  breakevenSl: null,
}

function mockApiResponses(overrides?: { positions?: Position[]; live?: LivePosition[] }) {
  const positions = overrides?.positions ?? [ACTIVE_POSITION, PENDING_POSITION]
  const live = overrides?.live ?? [LIVE_POSITION]
  mockGet().mockImplementation((url: string) => {
    if (url === '/portfolio/positions') return Promise.resolve({ data: positions })
    if (url === '/portfolio/positions/live') return Promise.resolve({ data: live })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

function renderPositions() {
  return renderWithAuth(<PositionsPage />, { user: REGULAR_USER })
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('PositionsPage', () => {
  it('renders the active tab with default column order', async () => {
    mockApiResponses()
    renderPositions()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers).toEqual(['Symbol', 'Qty', 'Avg Entry', 'LTP', 'Unrealised P&L', 'Stop Loss', 'Target', 'GTT', 'Status', ''])
  })

  it('reorders active-tab columns via the customize panel and persists the order', async () => {
    mockApiResponses()
    renderPositions()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: 'Customize columns' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move LTP up' }))
    await userEvent.click(document.body)

    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers.indexOf('LTP')).toBeLessThan(headers.indexOf('Avg Entry'))

    const saved = JSON.parse(localStorage.getItem(POSITION_ACTIVE_COLUMNS_STORAGE_KEY) ?? '[]')
    expect(saved.indexOf('ltp')).toBeLessThan(saved.indexOf('avgEntry'))
  })

  it('renders the pending tab with its own (smaller) default column set and no checkboxes', async () => {
    mockApiResponses()
    renderPositions()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: /^pending/i }))

    await waitFor(() => expect(screen.getByText('TCS')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers).toEqual(['Symbol', 'Qty', 'Entry Price', 'Stop Loss', 'Target', 'Status', ''])

    await userEvent.click(screen.getByRole('button', { name: 'Customize columns' }))
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  it('restores a previously saved column order on load', async () => {
    localStorage.setItem(POSITION_ACTIVE_COLUMNS_STORAGE_KEY, JSON.stringify(
      ['status', 'qty', 'avgEntry', 'ltp', 'unrealisedPnl', 'stopLoss', 'target', 'gtt']
    ))
    mockApiResponses()
    renderPositions()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers[1]).toBe('Status')
  })

  it('falls back to default order when saved pending-column data is stale', async () => {
    localStorage.setItem(POSITION_PENDING_COLUMNS_STORAGE_KEY, JSON.stringify(['someRemovedKey']))
    mockApiResponses()
    renderPositions()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: /^pending/i }))

    await waitFor(() => expect(screen.getByText('TCS')).toBeInTheDocument())
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers).toEqual(['Symbol', 'Qty', 'Entry Price', 'Stop Loss', 'Target', 'Status', ''])
  })
})
