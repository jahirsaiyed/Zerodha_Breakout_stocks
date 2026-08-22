import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '../lib/api'
import type { Signal } from '../lib/types'
import { Badge, statusVariant, statusLabel } from '../components/Badge'

const EMPTY = { symbol: '', entryPrice: '', stopLoss: '', target: '', notes: '' }

type EditState = { id: number; entryPrice: string; stopLoss: string; target: string; notes: string }

export function SignalsPage() {
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [formError, setFormError] = useState('')
  const [editState, setEditState] = useState<EditState | null>(null)
  const [editError, setEditError] = useState('')
  const [syncResult, setSyncResult] = useState<{ added: number; modified: number; removed: number } | null>(null)

  const { data: signals = [], isLoading } = useQuery<Signal[]>({
    queryKey: ['signals'],
    queryFn: () => api.get('/signals').then(r => r.data.data),
  })

  const create = useMutation({
    mutationFn: (body: typeof EMPTY) => api.post('/signals', {
      symbol: body.symbol.toUpperCase().trim(),
      entryPrice: Number(body.entryPrice),
      stopLoss: Number(body.stopLoss),
      target: Number(body.target),
      notes: body.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
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
      notes: state.notes || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['signals'] })
      setEditState(null)
      setEditError('')
    },
    onError: (e: any) => setEditError(e.response?.data?.error ?? 'Update failed'),
  })

  const cancel = useMutation({
    mutationFn: (id: number) => api.delete(`/signals/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['signals'] }),
  })

  const syncNow = useMutation({
    mutationFn: () => api.post('/signals/sync'),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['signals'] })
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
      notes: sig.notes ?? '',
    })
    setEditError('')
  }

  const setEdit = (k: keyof EditState) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setEditState(prev => prev ? { ...prev, [k]: e.target.value } : prev)

  const active  = signals.filter(s => s.status === 'ACTIVE')
  const others  = signals.filter(s => s.status !== 'ACTIVE')

  const inputCls = 'w-full rounded border border-gray-200 bg-white px-2 py-1 text-sm focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400'

  return (
    <div className="p-8">
      <div className="mb-6 flex items-center justify-between">
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

      {/* Add form */}
      {showForm && (
        <div className="mb-6 rounded-xl border border-indigo-100 bg-indigo-50/40 p-5">
          <h2 className="mb-4 text-sm font-semibold text-gray-900">New Signal</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4 lg:grid-cols-5">
            {[
              { name: 'symbol',     label: 'Symbol',      placeholder: 'RELIANCE' },
              { name: 'entryPrice', label: 'Entry Price',  placeholder: '2400' },
              { name: 'stopLoss',   label: 'Stop Loss',    placeholder: '2300' },
              { name: 'target',     label: 'Target',       placeholder: '2600' },
              { name: 'notes',      label: 'Notes',        placeholder: 'Optional' },
            ].map(({ name, label, placeholder }) => (
              <div key={name}>
                <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
                <input
                  value={form[name as keyof typeof EMPTY]}
                  onChange={e => setForm(f => ({ ...f, [name]: e.target.value }))}
                  placeholder={placeholder}
                  required={name !== 'notes'}
                  className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm
                             focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400" />
              </div>
            ))}
            <div className="col-span-2 flex items-end gap-2 lg:col-span-5">
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
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['Symbol','Entry','Stop Loss','Target','R:R','Source','Status','Added',''].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {[...active, ...others].map(sig => {
                const isEditing = editState?.id === sig.id

                if (isEditing && editState) {
                  return (
                    <tr key={sig.id} className="border-b border-indigo-50 bg-indigo-50/30">
                      <td className="px-5 py-2 font-medium text-gray-900">{sig.symbol}</td>
                      <td className="px-5 py-2">
                        <input value={editState.entryPrice} onChange={setEdit('entryPrice')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2">
                        <input value={editState.stopLoss} onChange={setEdit('stopLoss')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2">
                        <input value={editState.target} onChange={setEdit('target')} className={inputCls} />
                      </td>
                      <td className="px-5 py-2 text-gray-400">—</td>
                      <td className="px-5 py-2">
                        <Badge label={sig.source === 'MANUAL' ? 'Manual' : 'Sheet'} variant={sig.source === 'MANUAL' ? 'indigo' : 'blue'} />
                      </td>
                      <td className="px-5 py-2">
                        <Badge label={statusLabel(sig.status)} variant={statusVariant(sig.status)} />
                      </td>
                      <td className="px-5 py-2 text-xs text-gray-400">
                        <input value={editState.notes} onChange={setEdit('notes')} placeholder="Notes"
                          className={inputCls} />
                      </td>
                      <td className="px-5 py-2">
                        <div className="flex flex-col gap-1">
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

                return (
                  <tr key={sig.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                    <td className="px-5 py-3.5 font-medium text-gray-900">{sig.symbol}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.entryPrice).toFixed(2)}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.stopLoss).toFixed(2)}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.target).toFixed(2)}</td>
                    <td className="px-5 py-3.5 text-gray-600">{Number(sig.riskRewardRatio).toFixed(2)}x</td>
                    <td className="px-5 py-3.5">
                      <Badge label={sig.source === 'MANUAL' ? 'Manual' : 'Sheet'} variant={sig.source === 'MANUAL' ? 'indigo' : 'blue'} />
                    </td>
                    <td className="px-5 py-3.5">
                      <Badge label={statusLabel(sig.status)} variant={statusVariant(sig.status)} />
                    </td>
                    <td className="px-5 py-3.5 text-gray-400 text-xs">
                      {new Date(sig.addedAt).toLocaleDateString('en-IN')}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex gap-2">
                        {sig.status === 'ACTIVE' && (
                          <button onClick={() => startEdit(sig)}
                            className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-500
                                       transition-colors hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600">
                            Edit
                          </button>
                        )}
                        {sig.status === 'ACTIVE' && (
                          <button onClick={() => cancel.mutate(sig.id)}
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
        )}
      </div>
    </div>
  )
}
