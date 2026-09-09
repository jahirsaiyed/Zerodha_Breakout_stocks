import { useState } from 'react'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import type { LivePosition, Position, UserConfig } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'
import { CustomizePanel } from '../components/CustomizePanel'
import { useAuth } from '../contexts/AuthContext'
import {
  DEFAULT_POSITION_COLUMNS,
  DEFAULT_STAT_CARDS,
  POSITION_COLUMNS_STORAGE_KEY,
  POSITION_COLUMN_LABELS,
  STAT_CARDS_STORAGE_KEY,
  STAT_CARD_LABELS,
  loadVisibleKeys,
  saveVisibleKeys,
  type PositionColumnKey,
  type StatCardKey,
} from '../lib/dashboardPrefs'

function StatCard({ label, value, sub, color = 'text-gray-950' }: {
  label: string; value: string; sub?: string; color?: string
}) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5">
      <p className="text-sm text-gray-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold ${color}`}>{value}</p>
      {sub && <p className="mt-0.5 text-xs text-gray-400">{sub}</p>}
    </div>
  )
}

function pnlCls(v: number | null) {
  if (v == null) return 'text-gray-400'
  return v >= 0 ? 'text-emerald-600' : 'text-red-600'
}
function pnlStr(v: number | null, pct: number | null = null) {
  if (v == null) return '—'
  const base = (v >= 0 ? '+' : '') + '₹' + v.toFixed(2)
  if (pct == null) return base
  return `${base} (${pct >= 0 ? '+' : ''}${pct.toFixed(2)}%)`
}

function unrealisedPnlPct(pos: Position, livePos: LivePosition | undefined): number | null {
  const costBasis = (pos.avgEntryPrice ?? 0) * pos.quantity
  if (livePos?.unrealisedPnl == null || !costBasis) return null
  return (livePos.unrealisedPnl / costBasis) * 100
}

const STAT_CARD_OPTIONS = DEFAULT_STAT_CARDS.map(key => ({ key, label: STAT_CARD_LABELS[key] }))
const POSITION_COLUMN_OPTIONS = DEFAULT_POSITION_COLUMNS.map(key => ({ key, label: POSITION_COLUMN_LABELS[key] }))

export function DashboardPage() {
  const { user } = useAuth()

  const [visibleStatCards, setVisibleStatCards] = useState<StatCardKey[]>(() =>
    loadVisibleKeys(STAT_CARDS_STORAGE_KEY, DEFAULT_STAT_CARDS, DEFAULT_STAT_CARDS))
  const [visibleColumns, setVisibleColumns] = useState<PositionColumnKey[]>(() =>
    loadVisibleKeys(POSITION_COLUMNS_STORAGE_KEY, DEFAULT_POSITION_COLUMNS, DEFAULT_POSITION_COLUMNS))

  const handleStatCardsChange = (keys: string[]) => {
    const next = keys as StatCardKey[]
    setVisibleStatCards(next)
    saveVisibleKeys(STAT_CARDS_STORAGE_KEY, next)
  }
  const handleColumnsChange = (keys: string[]) => {
    const next = keys as PositionColumnKey[]
    setVisibleColumns(next)
    saveVisibleKeys(POSITION_COLUMNS_STORAGE_KEY, next)
  }

  const { data: positions = [] } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.get('/portfolio/positions').then(r => r.data),
  })

  const { data: live = [] } = useQuery<LivePosition[]>({
    queryKey: ['positions-live'],
    queryFn: () => api.get('/portfolio/positions/live').then(r => r.data),
    refetchInterval: 30_000,
  })

  const { data: config } = useQuery<UserConfig>({
    queryKey: ['config'],
    queryFn: () => api.get('/users/me/config').then(r => r.data.data),
  })

  const liveMap = new Map(live.map(p => [p.id, p]))

  const active  = positions.filter(p => p.status === 'ACTIVE')
  const pending = positions.filter(p => p.status === 'PENDING_ENTRY')
  const closed  = positions.filter(p => ['CLOSED_TARGET','CLOSED_SL','CLOSED_MANUAL'].includes(p.status))
  const totalPnl = closed.reduce((sum, p) => sum + (p.realisedPnl ?? 0), 0)
  const wins = closed.filter(p => p.status === 'CLOSED_TARGET').length
  const winRate = closed.length > 0 ? Math.round((wins / closed.length) * 100) : 0

  const totalUnrealised = live.reduce((sum, p) => sum + (p.unrealisedPnl ?? 0), 0)

  const pnlColor = totalPnl >= 0 ? 'text-emerald-600' : 'text-red-600'
  const pnlFormatted = (totalPnl >= 0 ? '+' : '') + '₹' + totalPnl.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

  const unrealisedColor = totalUnrealised >= 0 ? 'text-emerald-600' : 'text-red-600'
  const unrealisedFormatted = (totalUnrealised >= 0 ? '+' : '') + '₹' + totalUnrealised.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

  const statCardContent: Record<StatCardKey, { value: string; sub?: string; color?: string }> = {
    active: { value: String(active.length), sub: 'in market' },
    pending: { value: String(pending.length), sub: 'awaiting fill' },
    realisedPnl: { value: pnlFormatted, color: pnlColor, sub: `${closed.length} closed trades` },
    winRate: { value: `${winRate}%`, sub: `${wins} of ${closed.length} trades` },
    unrealisedPnl: { value: unrealisedFormatted, color: unrealisedColor, sub: `${live.length} live position${live.length === 1 ? '' : 's'}` },
  }

  const positionColumnCells: Record<PositionColumnKey, (pos: Position, livePos: LivePosition | undefined) => ReactNode> = {
    qty: pos => pos.quantity,
    avgEntry: pos => pos.avgEntryPrice?.toFixed(2) ?? '—',
    ltp: (_pos, livePos) => livePos?.ltp != null ? `₹${livePos.ltp.toFixed(2)}` : '—',
    invested: pos => pos.avgEntryPrice != null ? `₹${(pos.avgEntryPrice * pos.quantity).toFixed(2)}` : '—',
    unrealisedPnl: (pos, livePos) => (
      <span className={pnlCls(livePos?.unrealisedPnl ?? null)}>
        {pnlStr(livePos?.unrealisedPnl ?? null, unrealisedPnlPct(pos, livePos))}
      </span>
    ),
    sl: pos => pos.signalStopLoss?.toFixed(2) ?? '—',
    target: pos => pos.signalTarget?.toFixed(2) ?? '—',
    status: pos => <Badge label={statusLabel(pos.status)} variant={statusVariant(pos.status)} />,
  }

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-950">Dashboard</h1>
          <p className="text-sm text-gray-500">Welcome back, {user?.name}</p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-gray-500">Zerodha</span>
          <Badge
            label={config?.zerodhaConnected ? 'Connected' : 'Not connected'}
            variant={config?.zerodhaConnected ? 'green' : 'gray'}
          />
          {!config?.zerodhaConnected && (
            <a href="/api/zerodha/login" className="text-xs text-indigo-600 hover:text-indigo-700">
              Reconnect →
            </a>
          )}
        </div>
      </div>

      {/* Stats */}
      <div className="mb-3 flex justify-end">
        <CustomizePanel
          label="Customize stat cards"
          options={STAT_CARD_OPTIONS}
          visibleKeys={visibleStatCards}
          onChange={handleStatCardsChange}
          defaultKeys={DEFAULT_STAT_CARDS}
        />
      </div>
      {visibleStatCards.length === 0 ? (
        <div className="mb-8 rounded-xl border border-dashed border-gray-200 p-5 text-center text-sm text-gray-400">
          No stat cards selected. Use Customize to show some.
        </div>
      ) : (
        <div className="mb-8 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-5">
          {visibleStatCards.map(key => (
            <StatCard key={key} label={STAT_CARD_LABELS[key]} {...statCardContent[key]} />
          ))}
        </div>
      )}

      {/* Active positions table */}
      <div className="rounded-xl border border-gray-200 bg-white">
        <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-gray-950">Active Positions</h2>
          <div className="flex items-center gap-4">
            {live.length > 0 && (
              <span className={`text-xs font-medium ${totalUnrealised >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
                Unrealised {totalUnrealised >= 0 ? '+' : ''}₹{totalUnrealised.toFixed(2)}
              </span>
            )}
            <CustomizePanel
              label="Customize columns"
              options={POSITION_COLUMN_OPTIONS}
              visibleKeys={visibleColumns}
              onChange={handleColumnsChange}
              defaultKeys={DEFAULT_POSITION_COLUMNS}
            />
            <Link to="/positions" className="text-xs text-indigo-600 hover:text-indigo-700">View all →</Link>
          </div>
        </div>

        {active.length === 0 ? (
          <div className="px-5 py-10 text-center text-sm text-gray-400">
            No active positions. <Link to="/signals" className="text-indigo-600 hover:underline">Add a signal</Link> to get started.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-left">
                  <th className="px-5 py-3 text-xs font-medium text-gray-400 whitespace-nowrap">Symbol</th>
                  {visibleColumns.map(key => (
                    <th key={key} className="px-5 py-3 text-xs font-medium text-gray-400 whitespace-nowrap">
                      {POSITION_COLUMN_LABELS[key]}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {active.slice(0, 8).map(pos => {
                  const livePos = liveMap.get(pos.id)
                  return (
                    <tr key={pos.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                      <td className="px-5 py-3 font-medium text-gray-900 whitespace-nowrap">{pos.symbol}</td>
                      {visibleColumns.map(key => (
                        <td key={key} className="px-5 py-3 text-gray-600 whitespace-nowrap">
                          {positionColumnCells[key](pos, livePos)}
                        </td>
                      ))}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
