import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '../lib/api'
import type { LivePosition, Position } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'
import { useAuth } from '../contexts/AuthContext'

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
function pnlStr(v: number | null) {
  if (v == null) return '—'
  return (v >= 0 ? '+' : '') + '₹' + v.toFixed(2)
}

export function DashboardPage() {
  const { user } = useAuth()

  const { data: positions = [] } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.get('/portfolio/positions').then(r => r.data),
  })

  const { data: live = [] } = useQuery<LivePosition[]>({
    queryKey: ['positions-live'],
    queryFn: () => api.get('/portfolio/positions/live').then(r => r.data),
    refetchInterval: 30_000,
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

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Dashboard</h1>
        <p className="text-sm text-gray-500">Welcome back, {user?.name}</p>
      </div>

      {/* Stats */}
      <div className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label="Active Positions" value={String(active.length)} sub="in market" />
        <StatCard label="Pending Orders" value={String(pending.length)} sub="awaiting fill" />
        <StatCard label="Total Realised P&L" value={pnlFormatted} color={pnlColor} sub={`${closed.length} closed trades`} />
        <StatCard label="Win Rate" value={`${winRate}%`} sub={`${wins} of ${closed.length} trades`} />
      </div>

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
            <Link to="/positions" className="text-xs text-indigo-600 hover:text-indigo-700">View all →</Link>
          </div>
        </div>

        {active.length === 0 ? (
          <div className="px-5 py-10 text-center text-sm text-gray-400">
            No active positions. <Link to="/signals" className="text-indigo-600 hover:underline">Add a signal</Link> to get started.
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['Symbol','Qty','Avg Entry','LTP','Unrealised P&L','SL','Target','Status'].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {active.slice(0, 8).map(pos => {
                const livePos = liveMap.get(pos.id)
                return (
                  <tr key={pos.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                    <td className="px-5 py-3 font-medium text-gray-900">{pos.symbol}</td>
                    <td className="px-5 py-3 text-gray-600">{pos.quantity}</td>
                    <td className="px-5 py-3 text-gray-600">{pos.avgEntryPrice?.toFixed(2) ?? '—'}</td>
                    <td className="px-5 py-3 text-gray-900 font-medium">
                      {livePos?.ltp != null ? `₹${livePos.ltp.toFixed(2)}` : '—'}
                    </td>
                    <td className="px-5 py-3">
                      <span className={pnlCls(livePos?.unrealisedPnl ?? null)}>
                        {pnlStr(livePos?.unrealisedPnl ?? null)}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-gray-600">{pos.signalStopLoss?.toFixed(2) ?? '—'}</td>
                    <td className="px-5 py-3 text-gray-600">{pos.signalTarget?.toFixed(2) ?? '—'}</td>
                    <td className="px-5 py-3">
                      <Badge label={statusLabel(pos.status)} variant={statusVariant(pos.status)} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
