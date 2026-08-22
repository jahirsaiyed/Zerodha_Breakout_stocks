import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer, ReferenceLine,
} from 'recharts'
import api from '../lib/api'
import type { Position } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'

type Filter = 'ALL' | 'CLOSED_TARGET' | 'CLOSED_SL' | 'CLOSED_MANUAL'

export function HistoryPage() {
  const [filter, setFilter] = useState<Filter>('ALL')

  const { data: positions = [], isLoading } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.get('/portfolio/positions').then(r => r.data),
  })

  const closed = positions.filter(p =>
    ['CLOSED_TARGET', 'CLOSED_SL', 'CLOSED_MANUAL', 'CANCELLED'].includes(p.status))

  const filtered = filter === 'ALL' ? closed : closed.filter(p => p.status === filter)

  const totalPnl = closed
    .filter(p => p.status !== 'CANCELLED')
    .reduce((s, p) => s + (p.realisedPnl ?? 0), 0)

  const wins = closed.filter(p => p.status === 'CLOSED_TARGET').length
  const losses = closed.filter(p => p.status === 'CLOSED_SL').length

  // Build cumulative P&L series from closed (non-cancelled), sorted by closedAt
  const closedForChart = closed
    .filter(p => p.status !== 'CANCELLED' && p.closedAt)
    .sort((a, b) => new Date(a.closedAt!).getTime() - new Date(b.closedAt!).getTime())

  let running = 0
  const chartData = closedForChart.map(p => {
    running += p.realisedPnl ?? 0
    return {
      date: new Date(p.closedAt!).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }),
      cumPnl: parseFloat(running.toFixed(2)),
      pnl: parseFloat((p.realisedPnl ?? 0).toFixed(2)),
      symbol: p.symbol,
    }
  })

  const FILTERS: { label: string; value: Filter }[] = [
    { label: 'All', value: 'ALL' },
    { label: 'Target Hit', value: 'CLOSED_TARGET' },
    { label: 'Stop Loss', value: 'CLOSED_SL' },
    { label: 'Manual Exit', value: 'CLOSED_MANUAL' },
  ]

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">History</h1>
        <p className="text-sm text-gray-500">Closed positions and trade outcomes</p>
      </div>

      {/* Summary cards */}
      <div className="mb-6 grid grid-cols-3 gap-4">
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="text-xs text-gray-500">Total Realised P&L</p>
          <p className={`mt-1 text-xl font-semibold ${totalPnl >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
            {(totalPnl >= 0 ? '+' : '') + '₹' + totalPnl.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
          </p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="text-xs text-gray-500">Win / Loss</p>
          <p className="mt-1 text-xl font-semibold text-gray-900">
            <span className="text-emerald-600">{wins}W</span>
            <span className="mx-1 text-gray-300">/</span>
            <span className="text-red-500">{losses}L</span>
          </p>
        </div>
        <div className="rounded-xl border border-gray-200 bg-white p-4">
          <p className="text-xs text-gray-500">Win Rate</p>
          <p className="mt-1 text-xl font-semibold text-gray-900">
            {wins + losses > 0 ? Math.round((wins / (wins + losses)) * 100) : 0}%
          </p>
        </div>
      </div>

      {/* Cumulative P&L chart */}
      {chartData.length > 1 && (
        <div className="mb-6 rounded-xl border border-gray-200 bg-white p-5">
          <h2 className="mb-4 text-sm font-semibold text-gray-950">Cumulative P&L</h2>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={chartData} margin={{ top: 4, right: 4, bottom: 0, left: 0 }}>
              <defs>
                <linearGradient id="pnlGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#6366f1" stopOpacity={0.15} />
                  <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#9ca3af' }} />
              <YAxis tickFormatter={v => `₹${v}`} tick={{ fontSize: 11, fill: '#9ca3af' }} width={70} />
              <ReferenceLine y={0} stroke="#e5e7eb" />
              <Tooltip
                formatter={(value) => [`₹${Number(value).toFixed(2)}`, 'Cumulative P&L']}
                contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e5e7eb' }}
              />
              <Area type="monotone" dataKey="cumPnl" stroke="#6366f1" strokeWidth={2}
                fill="url(#pnlGrad)" dot={false} activeDot={{ r: 4 }} />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Filter pills */}
      <div className="mb-4 flex gap-2">
        {FILTERS.map(f => (
          <button key={f.value} onClick={() => setFilter(f.value)}
            className={`rounded-full px-3.5 py-1.5 text-xs font-medium transition-colors ${
              filter === f.value
                ? 'bg-indigo-500 text-white'
                : 'border border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
            }`}>
            {f.label}
          </button>
        ))}
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
        ) : filtered.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">No closed trades yet</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['Symbol','Qty','Avg Entry','P&L','Outcome','Closed'].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(pos => (
                <tr key={pos.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                  <td className="px-5 py-3.5 font-medium text-gray-900">{pos.symbol}</td>
                  <td className="px-5 py-3.5 text-gray-600">{pos.quantity}</td>
                  <td className="px-5 py-3.5 text-gray-600">{pos.avgEntryPrice?.toFixed(2) ?? '—'}</td>
                  <td className="px-5 py-3.5">
                    {pos.realisedPnl != null ? (
                      <span className={pos.realisedPnl >= 0 ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>
                        {pos.realisedPnl >= 0 ? '+' : ''}{pos.realisedPnl.toFixed(2)}
                      </span>
                    ) : <span className="text-gray-400">—</span>}
                  </td>
                  <td className="px-5 py-3.5">
                    <Badge label={statusLabel(pos.status)} variant={statusVariant(pos.status)} />
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-400">
                    {pos.closedAt ? new Date(pos.closedAt).toLocaleDateString('en-IN') : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
