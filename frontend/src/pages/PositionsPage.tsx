import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../lib/api'
import type { LivePosition, Position, StopLossBasis } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'

type Tab = 'ACTIVE' | 'PENDING_ENTRY'

const BASIS_OPTIONS: StopLossBasis[] = ['DAILY', 'HOURLY', 'WEEKLY']

const EMPTY_MANUAL_TRADE = {
  symbol: '', entryPrice: '', stopLoss: '', target: '', closingBasis: 'DAILY' as StopLossBasis,
  quantity: '', avgPrice: '',
}

const inputCls = 'w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400'
const amberInputCls = 'w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-amber-400 focus:outline-none focus:ring-1 focus:ring-amber-400'

// ── Confirm Fill Modal ──────────────────────────────────────────────────────

interface ConfirmFillModalProps {
  position: Position
  onClose: () => void
  onSuccess: () => void
}

function ConfirmFillModal({ position, onClose, onSuccess }: ConfirmFillModalProps) {
  const [quantity, setQuantity] = useState(String(position.quantity))
  const [avgPrice, setAvgPrice] = useState('')

  const confirmFill = useMutation({
    mutationFn: () => api.post(`/portfolio/positions/${position.id}/confirm-fill`, {
      quantity: Number(quantity),
      avgPrice: Number(avgPrice),
    }),
    onSuccess: () => {
      onSuccess()
      onClose()
    },
  })

  const qtyValid = Number(quantity) > 0
  const priceValid = Number(avgPrice) > 0

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
         onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white shadow-xl">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-100 px-6 py-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Confirm Fill</p>
            <h2 className="text-lg font-semibold text-gray-900">{position.symbol}</h2>
          </div>
          <button onClick={onClose}
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600">
            <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>

        {/* Body */}
        <div className="px-6 py-5">
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
            <svg className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-500" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <p className="text-sm text-amber-700">
              Only confirm if you've verified this order actually filled on Zerodha
              (order book or holdings). This cannot be undone automatically.
            </p>
          </div>

          <div className="space-y-4">
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">Filled quantity</label>
              <input type="number" min={1} step={1} value={quantity}
                onChange={e => setQuantity(e.target.value)} className={inputCls} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-gray-700">Average fill price (₹)</label>
              <input type="number" min={0} step={0.01} value={avgPrice}
                onChange={e => setAvgPrice(e.target.value)} placeholder="e.g. 410.50" className={inputCls} />
            </div>
          </div>

          {confirmFill.isError && (
            <p className="mt-3 text-sm text-red-600">
              {(confirmFill.error as any)?.response?.data?.error ?? 'Could not confirm fill'}
            </p>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 border-t border-gray-100 px-6 py-4">
          <button onClick={onClose}
            className="rounded-lg border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
            Cancel
          </button>
          <button
            onClick={() => confirmFill.mutate()}
            disabled={!qtyValid || !priceValid || confirmFill.isPending}
            className="rounded-lg bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                       hover:bg-indigo-600 disabled:cursor-not-allowed disabled:opacity-50">
            {confirmFill.isPending ? 'Confirming…' : 'Confirm Fill'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Record Manual Trade Modal (freeform — no pre-existing tracked signal) ─────
// Distinct from Confirm Fill: this records a trade that was never placed via this app
// at all (no order, no pre-existing signal). It creates the tracked signal and the
// position in one step. It NEVER places an order in Zerodha.

interface RecordManualTradeModalProps {
  onClose: () => void
  onSuccess: (msg: string) => void
}

function RecordManualTradeModal({ onClose, onSuccess }: RecordManualTradeModalProps) {
  const qc = useQueryClient()
  const [form, setForm] = useState(EMPTY_MANUAL_TRADE)

  const record = useMutation({
    mutationFn: () => api.post('/portfolio/manual-orders', {
      signal: {
        symbol: form.symbol.toUpperCase().trim(),
        entryPrice: Number(form.entryPrice),
        stopLoss: Number(form.stopLoss),
        target: Number(form.target),
        closingBasis: form.closingBasis,
      },
      quantity: Number(form.quantity),
      avgPrice: Number(form.avgPrice),
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      qc.invalidateQueries({ queryKey: ['positions'] })
      qc.invalidateQueries({ queryKey: ['positions-live'] })
      onSuccess(`Recorded manual trade — ${form.symbol.toUpperCase()} · ${form.quantity} shares @ ₹${Number(form.avgPrice).toFixed(2)}. Tracking started.`)
      onClose()
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    record.mutate()
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
         onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="w-full max-w-lg rounded-2xl border border-amber-200 bg-white shadow-xl">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-100 px-6 py-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-amber-600">Record Manual Trade</p>
            <h2 className="text-lg font-semibold text-gray-900">No order will be placed</h2>
          </div>
          <button onClick={onClose}
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600">
            <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="px-6 py-5">
          {/* Disclaimer — never let this look like placing an order */}
          <div className="mb-4 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
            <svg className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-500" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
            <p className="text-sm text-amber-800">
              This does <strong>not</strong> place an order in Zerodha. Only use this for a trade you already
              bought yourself in Kite — it just starts tracking it here (target and stop-loss will still be
              managed automatically from here on).
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Symbol</label>
              <input value={form.symbol} onChange={e => setForm(f => ({ ...f, symbol: e.target.value }))}
                placeholder="RELIANCE" required className={amberInputCls} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">SL Basis</label>
              <select value={form.closingBasis}
                onChange={e => setForm(f => ({ ...f, closingBasis: e.target.value as StopLossBasis }))}
                required className={amberInputCls}>
                {BASIS_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Stop Loss (₹)</label>
              <input type="number" step={0.01} value={form.stopLoss}
                onChange={e => setForm(f => ({ ...f, stopLoss: e.target.value }))}
                placeholder="2300" required className={amberInputCls} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Target (₹)</label>
              <input type="number" step={0.01} value={form.target}
                onChange={e => setForm(f => ({ ...f, target: e.target.value }))}
                placeholder="2600" required className={amberInputCls} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Filled quantity</label>
              <input type="number" min={1} step={1} value={form.quantity}
                onChange={e => setForm(f => ({ ...f, quantity: e.target.value }))}
                placeholder="e.g. 4" required className={amberInputCls} />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Average fill price (₹)</label>
              <input type="number" step={0.01} value={form.avgPrice}
                onChange={e => setForm(f => ({ ...f, avgPrice: e.target.value }))}
                placeholder="e.g. 2410" required className={amberInputCls} />
            </div>
          </div>

          {record.isError && (
            <p className="mt-3 text-sm text-red-600">
              {(record.error as any)?.response?.data?.error ?? 'Could not record manual trade'}
            </p>
          )}

          {/* Footer */}
          <div className="mt-5 flex items-center justify-end gap-3 border-t border-gray-100 pt-4">
            <button type="button" onClick={onClose}
              className="rounded-lg border border-gray-200 px-4 py-2 text-sm text-gray-600 hover:bg-gray-50">
              Cancel
            </button>
            <button type="submit" disabled={record.isPending}
              className="rounded-lg bg-amber-500 px-4 py-2 text-sm font-medium text-white
                         hover:bg-amber-600 disabled:cursor-not-allowed disabled:opacity-50">
              {record.isPending ? 'Recording…' : 'Record Trade'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function PositionsPage() {
  const qc = useQueryClient()
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [exiting, setExiting] = useState<number | null>(null)
  const [cancelling, setCancelling] = useState<number | null>(null)
  const [confirmingFill, setConfirmingFill] = useState<Position | null>(null)
  const [recordingManualTrade, setRecordingManualTrade] = useState(false)
  const [actionError, setActionError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')

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
      setActionError('')
    },
    onError: (e: any) => setActionError(e.response?.data?.error ?? 'Failed to exit position'),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => api.post(`/portfolio/positions/${id}/cancel`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['positions'] })
      setCancelling(null)
      setActionError('')
    },
    onError: (e: any) => setActionError(e.response?.data?.error ?? 'Failed to cancel position'),
  })

  const rows = positions.filter(p => p.status === tab)

  const tabCls = (t: Tab) => tab === t
    ? 'border-b-2 border-indigo-500 text-indigo-600 font-medium'
    : 'border-b-2 border-transparent text-gray-500 hover:text-gray-700'

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-950">Positions</h1>
          <p className="text-sm text-gray-500">Manage your active and pending orders</p>
        </div>
        <button onClick={() => setRecordingManualTrade(true)}
          title="Record a trade you already placed manually in Zerodha — does not place an order"
          className="inline-flex items-center gap-1.5 rounded-md border border-amber-200 bg-white px-4 py-2 text-sm font-medium text-amber-700
                     transition-colors hover:border-amber-300 hover:bg-amber-50">
          <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fillRule="evenodd" d="M6 3a1 1 0 00-1 1v1H4a2 2 0 00-2 2v9a2 2 0 002 2h12a2 2 0 002-2V7a2 2 0 00-2-2h-1V4a1 1 0 10-2 0v1H7V4a1 1 0 00-1-1zm7.707 6.293a1 1 0 00-1.414-1.414L9 11.172 7.707 9.879a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
          </svg>
          Record Manual Trade
        </button>
      </div>

      {actionError && (
        <div className="mb-4 rounded-lg bg-red-50 px-4 py-2.5 text-sm text-red-600">
          {actionError}
        </div>
      )}

      {successMsg && (
        <div className="mb-4 rounded-lg bg-emerald-50 px-4 py-2.5 text-sm text-emerald-700">
          {successMsg}
        </div>
      )}

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
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-gray-900">{pos.symbol}</span>
                        {pos.entrySource === 'MANUAL' && (
                          <span title="Entered manually in Zerodha, not placed via this app">
                            <Badge label="Manual" variant="yellow" />
                          </span>
                        )}
                      </div>
                    </td>
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
                          <div className="flex items-center gap-2">
                            <button onClick={() => setConfirmingFill(pos)}
                              className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-600
                                         transition-colors hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-600">
                              Confirm Fill
                            </button>
                            <button onClick={() => setCancelling(pos.id)}
                              className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-600
                                         transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-600">
                              Cancel
                            </button>
                          </div>
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

      {confirmingFill && (
        <ConfirmFillModal
          position={confirmingFill}
          onClose={() => setConfirmingFill(null)}
          onSuccess={() => {
            qc.invalidateQueries({ queryKey: ['positions'] })
            qc.invalidateQueries({ queryKey: ['positions-live'] })
          }}
        />
      )}

      {recordingManualTrade && (
        <RecordManualTradeModal
          onClose={() => setRecordingManualTrade(false)}
          onSuccess={msg => {
            setActionError('')
            setSuccessMsg(msg)
            setTimeout(() => setSuccessMsg(''), 8000)
          }}
        />
      )}
    </div>
  )
}
