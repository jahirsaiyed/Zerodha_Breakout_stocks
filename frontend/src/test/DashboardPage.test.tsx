import { describe, it, expect, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Routes, Route } from 'react-router-dom'
import { DashboardPage } from '../pages/DashboardPage'
import { renderWithAuth, REGULAR_USER } from './helpers'
import api from '../lib/api'
import type { Position, LivePosition, UserConfig } from '../lib/types'
import { STAT_CARDS_STORAGE_KEY, POSITION_COLUMNS_STORAGE_KEY } from '../lib/dashboardPrefs'

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

const CONFIG: UserConfig = {
  maxPositions: 5,
  positionSizingMethod: 'EQUAL',
  positionSizingValue: 10000,
  orderExpiryDays: 1,
  telegramChatId: null,
  zerodhaConnected: true,
  hasTotpSecret: true,
  hasBotToken: false,
  botName: null,
  botUsername: null,
  marginUsagePercent: 100,
  marginUsageFixedLimit: null,
  tradingPaused: false,
  syncPaused: false,
  partialProfitBookingEnabled: false,
  partialProfitBookingPercent: 50,
}

function mockApiResponses(overrides?: { positions?: Position[]; live?: LivePosition[] }) {
  const positions = overrides?.positions ?? [ACTIVE_POSITION]
  const live = overrides?.live ?? [LIVE_POSITION]
  mockGet().mockImplementation((url: string) => {
    if (url === '/portfolio/positions') return Promise.resolve({ data: positions })
    if (url === '/portfolio/positions/live') return Promise.resolve({ data: live })
    if (url === '/users/me/config') return Promise.resolve({ data: { data: CONFIG } })
    return Promise.reject(new Error(`Unexpected GET ${url}`))
  })
}

function renderDashboard() {
  return renderWithAuth(
    <Routes><Route path="/" element={<DashboardPage />} /></Routes>,
    { user: REGULAR_USER }
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('DashboardPage', () => {
  it('renders all stat cards by default, including Total Unrealised P&L', async () => {
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    // "Active Positions" appears both as a stat-card label and as the table section heading.
    expect(screen.getAllByText('Active Positions').length).toBe(2)
    expect(screen.getByText('Pending Orders')).toBeInTheDocument()
    expect(screen.getByText('Total Realised P&L')).toBeInTheDocument()
    expect(screen.getByText('Win Rate')).toBeInTheDocument()
    expect(screen.getByText('Total Unrealised P&L')).toBeInTheDocument()
    expect(screen.getByText('+₹500.00')).toBeInTheDocument()
  })

  it('renders the Invested Amount column with qty * avgEntryPrice for a position', async () => {
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    expect(screen.getByText('Invested Amount')).toBeInTheDocument()
    expect(screen.getByText('₹25000.00')).toBeInTheDocument()
  })

  it('hides a stat card via the customize panel and persists the choice', async () => {
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('Pending Orders')).toBeInTheDocument())

    const panels = screen.getAllByRole('button', { name: /customize/i })
    await userEvent.click(panels[0])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pending Orders' }))
    await userEvent.click(document.body) // close the popover so its own option label doesn't shadow the assertion below

    expect(screen.queryByText('Pending Orders')).not.toBeInTheDocument()

    const saved = JSON.parse(localStorage.getItem(STAT_CARDS_STORAGE_KEY) ?? '[]')
    expect(saved).not.toContain('pending')
  })

  it('hides a table column via the customize panel and persists the choice', async () => {
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())

    const panels = screen.getAllByRole('button', { name: /customize/i })
    await userEvent.click(panels[1])
    await userEvent.click(screen.getByRole('checkbox', { name: 'Invested Amount' }))
    await userEvent.click(document.body) // close the popover so its own option label doesn't shadow the assertion below

    expect(screen.queryByText('Invested Amount')).not.toBeInTheDocument()

    const saved = JSON.parse(localStorage.getItem(POSITION_COLUMNS_STORAGE_KEY) ?? '[]')
    expect(saved).not.toContain('invested')
  })

  it('restores previously saved stat card and column preferences on load', async () => {
    localStorage.setItem(STAT_CARDS_STORAGE_KEY, JSON.stringify(['active', 'unrealisedPnl']))
    localStorage.setItem(POSITION_COLUMNS_STORAGE_KEY, JSON.stringify(['qty', 'invested']))
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    expect(screen.queryByText('Pending Orders')).not.toBeInTheDocument()
    expect(screen.queryByText('Win Rate')).not.toBeInTheDocument()
    expect(screen.getByText('Total Unrealised P&L')).toBeInTheDocument()

    expect(screen.getByText('Invested Amount')).toBeInTheDocument()
    expect(screen.queryByText('LTP')).not.toBeInTheDocument()
  })

  it('falls back to defaults when localStorage holds malformed JSON', async () => {
    localStorage.setItem(STAT_CARDS_STORAGE_KEY, '{not valid json')
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('Pending Orders')).toBeInTheDocument())
    expect(screen.getByText('Win Rate')).toBeInTheDocument()
  })

  it('respects a deliberately-saved "hide everything" preference instead of reverting to defaults', async () => {
    localStorage.setItem(STAT_CARDS_STORAGE_KEY, JSON.stringify([]))
    localStorage.setItem(POSITION_COLUMNS_STORAGE_KEY, JSON.stringify([]))
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())
    expect(screen.getByText(/no stat cards selected/i)).toBeInTheDocument()
    expect(screen.queryByText('Win Rate')).not.toBeInTheDocument()
    expect(screen.queryByText('Invested Amount')).not.toBeInTheDocument()
    // Symbol stays visible regardless — it's the row identity, not a toggleable column.
    const table = screen.getByText('RELIANCE').closest('table')!
    expect(table.querySelectorAll('th')).toHaveLength(1)
  })

  it('reorders a table column via the customize panel up-arrow and persists the new order', async () => {
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('RELIANCE')).toBeInTheDocument())

    const panels = screen.getAllByRole('button', { name: /customize/i })
    await userEvent.click(panels[1])
    // Default order is qty, avgEntry, ltp, invested, ... — move "LTP" up above "Avg Entry".
    await userEvent.click(screen.getByRole('button', { name: 'Move LTP up' }))

    const saved = JSON.parse(localStorage.getItem(POSITION_COLUMNS_STORAGE_KEY) ?? '[]')
    expect(saved.indexOf('ltp')).toBeLessThan(saved.indexOf('avgEntry'))

    // Header order in the DOM should reflect the new order too.
    await userEvent.click(document.body)
    const headers = Array.from(document.querySelectorAll('table thead th')).map(th => th.textContent)
    expect(headers.indexOf('LTP')).toBeLessThan(headers.indexOf('Avg Entry'))
  })

  it('falls back to defaults when the stored array filters down to zero recognized keys (stale data)', async () => {
    localStorage.setItem(STAT_CARDS_STORAGE_KEY, JSON.stringify(['someRemovedKeyFromAnOldVersion']))
    mockApiResponses()
    renderDashboard()

    await waitFor(() => expect(screen.getByText('Pending Orders')).toBeInTheDocument())
    expect(screen.getByText('Win Rate')).toBeInTheDocument()
  })
})
