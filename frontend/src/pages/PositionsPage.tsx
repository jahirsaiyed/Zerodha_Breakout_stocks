import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../lib/api'
import type { LivePosition, Position } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'

type Tab = 'ACTIVE' | 'PENDING_ENTRY'

export function PositionsPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [exiting, setExiting] = useState<number | null>(null)
  const [cancelling, setCancelling] = useState<number | null>(null)

  const { data: positions = [], isLoading } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.get('/portfolio/positions').then(r => r.data),
    refetchInterval: 30_000,
  })

  const { data: live = [] } = useQuery<LivePosition[]>({
    queryKey: ['positions-live'],
    queryFn: () => api.get('/portfolio/positions/live').then(r => r.data),
    refetchInterval: 30_000,
  })

  const liveMap = new Map(live.map(p => [p.id, p]))

  const exit = useMutation({
    mutationFn: (id: number) => api.post(`/portfolio/positions/${id}/exit`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['positions'] })
      qc.invalidateQueries({ queryKey: ['positions-live'] })
      setExiting(null)
    },
  })

  const cancel = useMutation({
    mutationFn: (id: number) => api.post(`/portfolio/positions/${id}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['positions'] })
      setCancelling(null)
    },
  })

  const rows = positions.filter(p => p.status === tab)

  const tabCls = (t: Tab) => tab === t
    ? 'border-b-2 border-indigo-500 text-indigo-600 font-medium'
    : 'border-b-2 border-transparent text-gray-500 hover:text-gray-700'

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Positions</h1>
        <p className="text-sm text-gray-500">Manage your active and pending orders</p>
      </div>

      <div className="rounded-xl border border-gray-200 bg-white">
        {/* Tabs */}
        <div className="flex gap-6 border-b border-gray-200 px-5">
          {(['ACTIVE', 'PENDING_ENTRY'] as Tab[]).map(t => (
            <button key={t} onClick={() => setTab(t)}
              className={`py-3 text-sm transition-colors ${tabCls(t)}`}>
              {t === 'ACTIVE' ? 'Active' : 'Pending'}
              <span className="ml-1.5 rounded-full bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">
                {positions.filter(p => p.status === t).length}
              </span>
            </button>
          ))}
        </div>

        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
        ) : rows.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">No {tab === 'ACTIVE' ? 'active' : 'pending'} positions</div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {tab === 'ACTIVE'
                  ? ['Symbol','Qty','Avg Entry','LTP','Unrealised P&L','Stop Loss','Target','GTT','Status',''].map(h => (
                      <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                    ))
                  : ['Symbol','Qty','Entry Price','Stop Loss','Target','Status',''].map(h => (
                      <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                    ))
                }
              </tr>
            </thead>
            <tbody>
              {rows.map(pos => {
                const livePos = liveMap.get(pos.id)
                const unPnl = livePos?.unrealisedPnl ?? null
                return (
                  <tr key={pos.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                    <td className="px-5 py-3.5 font-medium text-gray-900">{pos.symbol}</td>
                    <td className="px-5 py-3.5 text-gray-600">{pos.quantity}</td>
                    <td className="px-5 py-3.5 text-gray-600">{pos.avgEntryPrice?.toFixed(2) ?? '—'}</td>
                    {tab === 'ACTIVE' && (
                      <>
                        <td className="px-5 py-3.5 font-medium text-gray-900">
                          {livePos?.ltp != null ? `₹${livePos.ltp.toFixed(2)}` : '—'}
                        </td>
                        <td className="px-5 py-3.5">
                          <span className={unPnl == null ? 'text-gray-400' : unPnl >= 0 ? 'text-emerald-600 font-medium' : 'text-red-600 font-medium'}>
                            {unPnl == null ? '—' : (unPnl >= 0 ? '+' : '') + `₹${unPnl.toFixed(2)}`}
                          </span>
                        </td>
                      </>
                    )}
                    <td className="px-5 py-3.5">
                      {pos.breakevenSl != null ? (
                        <span className="inline-flex flex-col gap-0.5">
                          <span className="font-medium text-amber-600">{pos.breakevenSl.toFixed(2)}</span>
                          <span className="text-xs text-amber-500">breakeven</span>
                        </span>
                      ) : (
                        <span className="text-gray-600">{pos.signalStopLoss?.toFixed(2) ?? '—'}</span>
                      )}
                    </td>
                    <td className="px-5 py-3.5 text-gray-600">{pos.signalTarget?.toFixed(2) ?? '—'}</td>
                    {tab === 'ACTIVE' && (
                      <td className="px-5 py-3.5">
                        {pos.gttOrderId
                          ? <Badge label="GTT Active" variant="indigo" />
                          : <span className="text-xs text-gray-400">None</span>}
                      </td>
                    )}
                    <td className="px-5 py-3.5">
                      <Badge label={statusLabel(pos.status)} variant={statusVariant(pos.status)} />
                    </td>
                    <td className="px-5 py-3.5">
                      {pos.status === 'ACTIVE' && (
                        exiting === pos.id ? (
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-gray-500">Confirm close?</span>
                            <button onClick={() => exit.mutate(pos.id)}
                              className="rounded-md bg-red-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-600">
                              Yes
                            </button>
                            <button onClick={() => setExiting(null)}
                              className="rounded-md border border-gray-200 px-2.5 py-1 text-xs hover:bg-gray-50">
                              No
                            </button>
                          </div>
                        ) : (
                          <button onClick={() => setExiting(pos.id)}
                            className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-600
                                       transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-600">
                            Close
                          </button>
                        )
                      )}
                      {pos.status === 'PENDING_ENTRY' && (
                        cancelling === pos.id ? (
                          <div className="flex items-center gap-2">
                            <span className="text-xs text-gray-500">Confirm cancel?</span>
                            <button onClick={() => cancel.mutate(pos.id)}
                              className="rounded-md bg-red-500 px-2.5 py-1 text-xs font-medium text-white hover:bg-red-600">
                              Yes
                            </button>
                            <button onClick={() => setCancelling(null)}
                              className="rounded-md border border-gray-200 px-2.5 py-1 text-xs hover:bg-gray-50">
                              No
                            </button>
                          </div>
                        ) : (
                          <button onClick={() => setCancelling(pos.id)}
                            className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-600
                                       transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-600">
                            Cancel
                          </button>
                        )
                      )}
                    </td>
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
