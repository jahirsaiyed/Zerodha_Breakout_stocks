import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../lib/api'
import type { OrderPreview, Position, Signal, SignalQuote, StopLossBasis } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'

const BASIS_OPTIONS: StopLossBasis[] = ['DAILY', 'HOURLY', 'WEEKLY']

const EMPTY = { symbol: '', entryPrice: '', stopLoss: '', target: '', closingBasis: 'DAILY' as StopLossBasis, notes: '' }

type EditState = { id: number; entryPrice: string; stopLoss: string; target: string; closingBasis: StopLossBasis; notes: string }

function diffBg(diff: number | null): string {
  if (diff === null) return ''
  if (diff < 0) return 'bg-red-50 text-red-600'
  if (diff < 5) return 'bg-amber-50 text-amber-700'
  return 'bg-emerald-50 text-emerald-700'
}

function fmt(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// ── Place Order Modal ─────────────────────────────────────────────────────────

interface PlaceOrderModalProps {
  signal: Signal
  onClose: () => void
  onSuccess: (msg: string) => void
}

function PlaceOrderModal({ signal, onClose, onSuccess }: PlaceOrderModalProps) {
  const qc = useQueryClient()

  const { data: preview, isLoading, isError } = useQuery<OrderPreview>({
    queryKey: ['order-preview', signal.id],
    queryFn: () => api.get(`/portfolio/signals/${signal.id}/order-preview`).then(r => r.data),
    staleTime: 0,
    retry: false,
  })

  const place = useMutation({
    mutationFn: () => api.post(`/portfolio/signals/${signal.id}/place-order`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      qc.invalidateQueries({ queryKey: ['signals-quotes'] })
      qc.invalidateQueries({ queryKey: ['positions'] })
      const qty = preview?.estimatedQty ?? '?'
      const cost = preview?.estimatedCost != null ? `₹${fmt(preview.estimatedCost)}` : ''
      onSuccess(`Order placed for ${signal.symbol} — ${qty} shares${cost ? ` · ${cost}` : ''}`)
      onClose()
    },
  })

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
         onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white shadow-xl">

        {/* Header */}
        <div className="flex items-center justify-between border-b border-gray-100 px-6 py-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-gray-400">Place Buy Order</p>
            <h2 className="text-lg font-semibold text-gray-900">{signal.symbol}</h2>
          </div>
          <button onClick={onClose}
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600">
            <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>

        {/* Signal summary */}
        <div className="grid grid-cols-3 gap-px border-b border-gray-100 bg-gray-100">
          {[
            { label: 'Entry', value: `₹${fmt(signal.entryPrice)}` },
            { label: 'Stop Loss', value: `₹${fmt(signal.stopLoss)}` },
            { label: 'Target', value: `₹${fmt(signal.target)}` },
          ].map(({ label, value }) => (
            <div key={label} className="bg-white px-4 py-3">
              <p className="text-xs text-gray-400">{label}</p>
              <p className="mt-0.5 font-medium text-gray-800">{value}</p>
            </div>
          ))}
        </div>

        {/* Preview body */}
        <div className="px-6 py-5">
          {isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-400">
              <svg className="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
              </svg>
              Fetching order preview…
            </div>
          )}

          {isError && (
            <p className="text-sm text-red-600">Could not load preview. Check your Zerodha connection.</p>
          )}

          {preview && (
            <div className="space-y-4">
              {/* Cannot place reason */}
              {!preview.canPlace && preview.reason && (
                <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
                  <svg className="mt-0.5 h-4 w-4 flex-shrink-0 text-amber-500" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
                  </svg>
                  <p className="text-sm text-amber-700">{preview.reason}</p>
                </div>
              )}

              {/* Order details */}
              <div className="rounded-xl border border-gray-100 bg-gray-50">
                <div className="divide-y divide-gray-100">
                  <Row label="Order type" value="Limit buy" />
                  <Row label="Estimated quantity"
                    value={preview.canPlace ? `${preview.estimatedQty} shares` : '—'}
                    highlight={preview.canPlace} />
                  <Row label="Estimated cost"
                    value={preview.canPlace ? `₹${fmt(preview.estimatedCost)}` : '—'}
                    highlight={preview.canPlace} />
                  {preview.canPlace && (() => {
                    const atRisk = preview.estimatedQty * (preview.entryPrice - preview.stopLoss)
                    const riskPct = preview.estimatedCost > 0 ? (atRisk / preview.estimatedCost) * 100 : null
                    return (
                      <Row
                        label="Max loss if SL hit"
                        value={`₹${fmt(Math.max(0, atRisk))}`}
                        danger
                        sub={riskPct != null ? `(${riskPct.toFixed(1)}% of capital)` : undefined}
                      />
                    )
                  })()}
                  <Row label="R:R ratio" value={`${Number(signal.riskRewardRatio).toFixed(2)}x`} />
                  {preview.availableMargin != null && (
                    <Row label="Available margin" value={`₹${fmt(preview.availableMargin)}`} />
                  )}
                  {preview.availableSlots > 0 && (
                    <Row label="Position slots free" value={String(preview.availableSlots)} />
                  )}
                </div>
              </div>

              {preview.canPlace && (
                <p className="text-xs text-gray-400">
                  A limit order at ₹{fmt(signal.entryPrice)} will be placed via Zerodha.
                  A GTT target order is set automatically on fill.
                </p>
              )}
            </div>
          )}

          {place.isError && (
            <p className="mt-3 text-sm text-red-600">
              {(place.error as any)?.response?.data?.error ?? 'Order placement failed'}
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
            onClick={() => place.mutate()}
            disabled={!preview?.canPlace || place.isPending || isLoading}
            className="rounded-lg bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                       hover:bg-indigo-600 disabled:cursor-not-allowed disabled:opacity-50">
            {place.isPending
              ? 'Placing…'
              : preview?.canPlace
                ? `Confirm — Buy ${preview.estimatedQty} shares`
                : 'Cannot Place Order'}
          </button>
        </div>
      </div>
    </div>
  )
}

function Row({ label, value, highlight = false, danger = false, sub }: { label: string; value: string; highlight?: boolean; danger?: boolean; sub?: string }) {
  return (
    <div className="flex items-center justify-between px-4 py-2.5">
      <span className="text-xs text-gray-500">{label}</span>
      <div className="flex items-baseline gap-1.5">
        <span className={`text-sm font-medium ${danger ? 'text-red-600' : highlight ? 'text-indigo-700' : 'text-gray-700'}`}>{value}</span>
        {sub && <span className="text-xs text-red-400">{sub}</span>}
      </div>
    </div>
  )
}

// ── Signals Page ──────────────────────────────────────────────────────────────

export function SignalsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [formError, setFormError] = useState('')
  const [editState, setEditState] = useState<EditState | null>(null)
  const [editError, setEditError] = useState('')
  const [syncResult, setSyncResult] = useState<{ added: number; modified: number; removed: number } | null>(null)
  const [tradeSignal, setTradeSignal] = useState<Signal | null>(null)
  const [successMsg, setSuccessMsg] = useState('')

  const { data: signals = [], isLoading } = useQuery<Signal[]>({
    queryKey: ['signals'],
    queryFn: () => api.get('/signals').then(r => r.data.data),
  })

  const { data: quotes = [] } = useQuery<SignalQuote[]>({
    queryKey: ['signals-quotes'],
    queryFn: () => api.get('/signals/quotes').then(r => r.data.data),
    refetchInterval: 60_000,
  })

  // Track which signals already have open positions so we can hide the Trade button
  const { data: positions = [] } = useQuery<Position[]>({
    queryKey: ['positions'],
    queryFn: () => api.get('/portfolio/positions').then(r => r.data),
    staleTime: 30_000,
  })

  const occupiedSignalIds = new Set(
    positions
      .filter(p => p.status === 'PENDING_ENTRY' || p.status === 'ACTIVE')
      .map(p => p.signalId)
      .filter((id): id is number => id != null)
  )

  const quotesMap = new Map<number, SignalQuote>(quotes.map(q => [q.signalId, q]))

  const create = useMutation({
    mutationFn: (body: typeof EMPTY) => api.post('/signals', {
      symbol: body.symbol.toUpperCase().trim(),
      entryPrice: Number(body.entryPrice),
      stopLoss: Number(body.stopLoss),
      target: Number(body.target),
      closingBasis: body.closingBasis,
      notes: body.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      qc.invalidateQueries({ queryKey: ['signals-quotes'] })
      setShowForm(false)
      setForm(EMPTY)
      setFormError('')
    },
    onError: (e: any) => setFormError(e.response?.data?.error ?? 'Failed to create signal'),
  })

  const update = useMutation({
    mutationFn: (state: EditState) => api.put(`/signals/${state.id}`, {
      entryPrice: Number(state.entryPrice),
      stopLoss: Number(state.stopLoss),
      target: Number(state.target),
      closingBasis: state.closingBasis,
      notes: state.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      qc.invalidateQueries({ queryKey: ['signals-quotes'] })
      setEditState(null)
      setEditError('')
    },
    onError: (e: any) => setEditError(e.response?.data?.error ?? 'Update failed'),
  })

  const cancelSignal = useMutation({
    mutationFn: (id: number) => api.delete(`/signals/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  })

  const syncNow = useMutation({
    mutationFn: () => api.post('/signals/sync'),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      qc.invalidateQueries({ queryKey: ['signals-quotes'] })
      const d = res.data.data
      setSyncResult({ added: d.signalsAdded, modified: d.signalsModified, removed: d.signalsRemoved })
      setTimeout(() => setSyncResult(null), 8000)
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    setFormError('')
    create.mutate(form)
  }

  const startEdit = (sig: Signal) => {
    setEditState({
      id: sig.id,
      entryPrice: String(sig.entryPrice),
      stopLoss: String(sig.stopLoss),
      target: String(sig.target),
      closingBasis: sig.closingBasis,
      notes: sig.notes ?? '',
    })
    setEditError('')
  }

  const setEdit = (k: keyof EditState) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setEditState(prev => prev ? { ...prev, [k]: e.target.value } : prev)

  const handleSuccess = (msg: string) => {
    setSuccessMsg(msg)
    setTimeout(() => setSuccessMsg(''), 8000)
  }

  const active  = signals.filter(s => s.status === 'ACTIVE')
  const others  = signals.filter(s => s.status !== 'ACTIVE')

  const inputCls = 'w-full rounded border border-gray-200 bg-white px-2 py-1 text-sm focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400'

  return (
    <div className="p-4 sm:p-8">
      {/* Modal */}
      {tradeSignal && (
        <PlaceOrderModal
          signal={tradeSignal}
          onClose={() => setTradeSignal(null)}
          onSuccess={handleSuccess}
        />
      )}

      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-950">Signals</h1>
          <p className="text-sm text-gray-500">Manage your trading signals</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={() => syncNow.mutate()}
            disabled={syncNow.isPending}
            className="rounded-md border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-600
                       hover:bg-gray-50 disabled:opacity-60">
            {syncNow.isPending ? 'Syncing…' : 'Sync Now'}
          </button>
          <button onClick={() => setShowForm(v => !v)}
            className="rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                       transition-colors hover:bg-indigo-600">
            {showForm ? 'Cancel' : '+ Add Signal'}
          </button>
        </div>
      </div>

      {syncResult && (
        <div className="mb-4 rounded-lg bg-emerald-50 px-4 py-2.5 text-sm text-emerald-700">
          Sync complete — {syncResult.added} added, {syncResult.modified} modified, {syncResult.removed} removed
        </div>
      )}

      {successMsg && (
        <div className="mb-4 rounded-lg bg-emerald-50 px-4 py-2.5 text-sm text-emerald-700">
          {successMsg}
        </div>
      )}

      {/* Add form */}
      {showForm && (
        <div className="mb-6 rounded-xl border border-indigo-100 bg-indigo-50/40 p-5">
          <h2 className="mb-4 text-sm font-semibold text-gray-900">New Signal</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4 lg:grid-cols-6">
            {[
              { name: 'symbol',     label: 'Symbol',     placeholder: 'RELIANCE' },
              { name: 'entryPrice', label: 'Entry Price', placeholder: '2400' },
              { name: 'stopLoss',   label: 'Stop Loss',   placeholder: '2300' },
              { name: 'target',     label: 'Target',      placeholder: '2600' },
              { name: 'notes',      label: 'Notes',       placeholder: 'Optional' },
            ].map(({ name, label, placeholder }) => (
              <div key={name}>
                <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
                <input
                  value={form[name as keyof typeof EMPTY] as string}
                  onChange={e => setForm(f => ({ ...f, [name]: e.target.value }))}
                  placeholder={placeholder}
                  required={name !== 'notes'}
                  className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm
                             focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400" />
              </div>
            ))}
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">SL Basis</label>
              <select value={form.closingBasis}
                onChange={e => setForm(f => ({ ...f, closingBasis: e.target.value as StopLossBasis }))}
                required
                className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm
                           focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400">
                {BASIS_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
              </select>
            </div>
            <div className="col-span-2 flex items-end gap-2 lg:col-span-6">
              {formError && <p className="text-xs text-red-600">{formError}</p>}
              <button type="submit" disabled={create.isPending}
                className="rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                           hover:bg-indigo-600 disabled:opacity-60">
                {create.isPending ? 'Adding…' : 'Add Signal'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white">
        <div className="border-b border-gray-200 px-5 py-4">
          <h2 className="text-sm font-semibold text-gray-950">
            Active Signals <span className="ml-1 text-gray-400 font-normal">({active.length})</span>
          </h2>
        </div>

        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
        ) : signals.length === 0 ? (
          <div className="py-12 text-center text-sm text-gray-400">No signals yet. Add one above.</div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['#','Symbol','Entry','Stop Loss','SL Basis','Target','R:R','LTP','vs Entry','Source','Status',''].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[...active, ...others].map(sig => {
                const isEditing = editState?.id === sig.id
                const q = quotesMap.get(sig.id)

                if (isEditing && editState) {
                  return (
                    <tr key={sig.id} className="border-b border-indigo-50 bg-indigo-50/30">
                      <td className="px-5 py-2 text-gray-400">—</td>
                      <td className="px-5 py-2 font-medium text-gray-900">{sig.symbol}</td>
                      <td className="px-5 py-2">
                        <input value={editState.entryPrice} onChange={setEdit('entryPrice')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2">
                        <input value={editState.stopLoss} onChange={setEdit('stopLoss')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2">
                        <select value={editState.closingBasis}
                          onChange={e => setEditState(prev => prev ? { ...prev, closingBasis: e.target.value as StopLossBasis } : prev)}
                          className={inputCls}>
                          {BASIS_OPTIONS.map(b => <option key={b} value={b}>{b}</option>)}
                        </select>
                      </td>
                      <td className="px-5 py-2">
                        <input value={editState.target} onChange={setEdit('target')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2 text-gray-400">—</td>
                      <td className="px-5 py-2 text-gray-400">—</td>
                      <td className="px-5 py-2 text-gray-400">—</td>
                      <td className="px-5 py-2">
                        <Badge label={sig.source === 'MANUAL' ? 'Manual' : 'Sheet'} variant={sig.source === 'MANUAL' ? 'indigo' : 'blue'} />
                      </td>
                      <td className="px-5 py-2">
                        <Badge label={statusLabel(sig.status)} variant={statusVariant(sig.status)} />
                      </td>
                      <td className="px-5 py-2">
                        <div className="flex flex-col gap-1">
                          <input value={editState.notes} onChange={setEdit('notes')} placeholder="Notes"
                            className={inputCls} />
                          {editError && <p className="text-xs text-red-600">{editError}</p>}
                          <div className="flex gap-2">
                            <button onClick={() => update.mutate(editState)}
                              disabled={update.isPending}
                              className="rounded border border-indigo-300 bg-indigo-500 px-3 py-1 text-xs text-white
                                         hover:bg-indigo-600 disabled:opacity-60">
                              {update.isPending ? 'Saving…' : 'Save'}
                            </button>
                            <button onClick={() => setEditState(null)}
                              className="rounded border border-gray-200 px-3 py-1 text-xs text-gray-500 hover:bg-gray-50">
                              Cancel
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )
                }

                const hasPosition = occupiedSignalIds.has(sig.id)

                return (
                  <tr key={sig.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                    {/* Rank */}
                    <td className="px-5 py-3.5 text-xs font-medium text-gray-400">
                      {sig.status === 'ACTIVE' && q?.rank != null
                        ? <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 font-semibold">{q.rank}</span>
                        : <span className="text-gray-300">—</span>}
                    </td>
                    {/* Symbol + date */}
                    <td className="px-5 py-3.5">
                      <span className="font-medium text-gray-900">{sig.symbol}</span>
                      <span className="mt-0.5 block text-xs text-gray-400">
                        {new Date(sig.addedAt).toLocaleDateString('en-IN')}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.entryPrice).toFixed(2)}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.stopLoss).toFixed(2)}</td>
                    <td className="px-5 py-3.5">
                      <span className="inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium bg-slate-100 text-slate-600">
                        {sig.closingBasis}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.target).toFixed(2)}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.riskRewardRatio).toFixed(2)}x</td>
                    {/* LTP */}
                    <td className="px-5 py-3.5">
                      {sig.status === 'ACTIVE' && q?.ltp != null
                        ? <span className="font-medium text-gray-800">{Number(q.ltp).toFixed(2)}</span>
                        : <span className="text-gray-300">—</span>}
                    </td>
                    {/* Diff from entry */}
                    <td className="px-5 py-3.5">
                      {sig.status === 'ACTIVE' && q?.diffFromEntryPct != null
                        ? <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-medium ${diffBg(q.diffFromEntryPct)}`}>
                            {q.diffFromEntryPct > 0 ? '+' : ''}{Number(q.diffFromEntryPct).toFixed(2)}%
                          </span>
                        : <span className="text-gray-300">—</span>}
                    </td>
                    <td className="px-5 py-3.5">
                      <Badge label={sig.source === 'MANUAL' ? 'Manual' : 'Sheet'} variant={sig.source === 'MANUAL' ? 'indigo' : 'blue'} />
                    </td>
                    <td className="px-5 py-3.5">
                      <Badge label={statusLabel(sig.status)} variant={statusVariant(sig.status)} />
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex gap-2">
                        {/* Trade button — visible for ACTIVE signals without an existing position */}
                        {sig.status === 'ACTIVE' && !hasPosition && (
                          <button onClick={() => setTradeSignal(sig)}
                            className="rounded-md border border-emerald-200 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700
                                       transition-colors hover:border-emerald-300 hover:bg-emerald-100">
                            Trade
                          </button>
                        )}
                        {/* Ordered badge — signal already has an open position */}
                        {sig.status === 'ACTIVE' && hasPosition && (
                          <span className="inline-flex items-center rounded-md border border-indigo-100 bg-indigo-50 px-3 py-1 text-xs font-medium text-indigo-600">
                            Ordered
                          </span>
                        )}
                        {sig.status === 'ACTIVE' && (
                          <button onClick={() => startEdit(sig)}
                            className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-500
                                       transition-colors hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600">
                            Edit
                          </button>
                        )}
                        {sig.status === 'ACTIVE' && (
                          <button onClick={() => cancelSignal.mutate(sig.id)}
                            className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-500
                                       transition-colors hover:border-red-200 hover:bg-red-50 hover:text-red-600">
                            Cancel
                          </button>
                        )}
                      </div>
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
