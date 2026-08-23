import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router-dom'
import api from '../lib/api'
import type { AdminUser, HealthResponse } from '../lib/types'
import { Badge } from '../components/Badge'
import { useAuth } from '../contexts/AuthContext'

export function AdminPage() {
  const { user } = useAuth()
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'USER' })
  const [formError, setFormError] = useState('')

  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />

  const { data: users = [], isLoading } = useQuery<AdminUser[]>({
    queryKey: ['admin-users'],
    queryFn: () => api.get('/admin/users').then(r => r.data.data),
  })

  const { data: health } = useQuery<HealthResponse>({
    queryKey: ['admin-health'],
    queryFn: () => api.get('/admin/health').then(r => r.data.data),
    refetchInterval: 60_000,
  })

  const toggleActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api.patch(`/admin/users/${id}/status`, null, { params: { active } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const createUser = useMutation({
    mutationFn: () => api.post('/admin/users', form),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin-users'] })
      setShowForm(false)
      setForm({ name: '', email: '', password: '', role: 'USER' })
      setFormError('')
    },
    onError: (e: any) => setFormError(e.response?.data?.error ?? 'Failed to create user'),
  })

  const handleSubmit = (e: FormEvent) => { e.preventDefault(); setFormError(''); createUser.mutate() }

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }))

  return (
    <div className="p-4 sm:p-8">
      {/* System Health */}
      {health && (
        <div className="mb-8 rounded-xl border border-gray-200 bg-white p-6">
          <h2 className="mb-4 text-sm font-semibold text-gray-950">System Health</h2>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <div>
              <p className="text-xs text-gray-500">Instrument Cache</p>
              <p className="mt-1 text-base font-semibold text-gray-900">
                {health.instrumentCacheLoaded
                  ? health.instrumentCacheSize.toLocaleString() + ' symbols'
                  : 'Not loaded'}
              </p>
              <span className={`mt-0.5 inline-block text-xs ${health.instrumentCacheLoaded ? 'text-emerald-600' : 'text-red-500'}`}>
                {health.instrumentCacheLoaded ? 'Loaded' : 'Missing'}
              </span>
            </div>
            <div>
              <p className="text-xs text-gray-500">Last Sheet Sync</p>
              <p className="mt-1 text-base font-semibold text-gray-900">
                {health.lastSyncAt
                  ? new Date(health.lastSyncAt).toLocaleString('en-IN', { dateStyle: 'short', timeStyle: 'short' })
                  : 'Never'}
              </p>
              {health.lastSyncAt && (
                <p className="mt-0.5 text-xs text-gray-400">
                  +{health.lastSyncAdded} / ~{health.lastSyncModified}
                </p>
              )}
            </div>
            <div>
              <p className="text-xs text-gray-500">Zerodha Connected</p>
              <p className="mt-1 text-base font-semibold text-gray-900">
                {health.zerodhaStatuses.filter(s => s.connected).length} / {health.zerodhaStatuses.length}
              </p>
              <p className="mt-0.5 text-xs text-gray-400">users connected</p>
            </div>
            <div>
              <p className="text-xs text-gray-500">User Statuses</p>
              <div className="mt-1 space-y-1">
                {health.zerodhaStatuses.map(s => (
                  <div key={s.userId} className="flex items-center gap-2">
                    <span className={`h-1.5 w-1.5 rounded-full ${s.connected ? 'bg-emerald-500' : 'bg-gray-300'}`} />
                    <span className="text-xs text-gray-500 truncate max-w-[120px]">{s.email}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-gray-950">User Management</h1>
          <p className="text-sm text-gray-500">Admin — manage system users</p>
        </div>
        <button onClick={() => setShowForm(v => !v)}
          className="rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-600">
          {showForm ? 'Cancel' : '+ New User'}
        </button>
      </div>

      {showForm && (
        <div className="mb-6 rounded-xl border border-indigo-100 bg-indigo-50/40 p-5">
          <h2 className="mb-4 text-sm font-semibold text-gray-900">Create User</h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            {[
              { k: 'name',     label: 'Name',     type: 'text',     ph: 'Full name' },
              { k: 'email',    label: 'Email',    type: 'email',    ph: 'user@example.com' },
              { k: 'password', label: 'Password', type: 'password', ph: 'Min 8 characters' },
            ].map(({ k, label, type, ph }) => (
              <div key={k}>
                <label className="mb-1 block text-xs font-medium text-gray-600">{label}</label>
                <input type={type} value={form[k as keyof typeof form]} onChange={set(k)}
                  placeholder={ph} required
                  className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm
                             focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400" />
              </div>
            ))}
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Role</label>
              <select value={form.role} onChange={set('role')}
                className="w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm
                           focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400">
                <option value="USER">User</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
            <div className="col-span-2 flex items-end gap-2 lg:col-span-4">
              {formError && <p className="text-xs text-red-600">{formError}</p>}
              <button type="submit" disabled={createUser.isPending}
                className="rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                           hover:bg-indigo-600 disabled:opacity-60">
                {createUser.isPending ? 'Creating…' : 'Create User'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="rounded-xl border border-gray-200 bg-white">
        {isLoading ? (
          <div className="py-12 text-center text-sm text-gray-400">Loading…</div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100 text-left">
                {['Name','Email','Role','Status','Joined','Actions'].map(h => (
                  <th key={h} className="px-5 py-3 text-xs font-medium text-gray-400">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {users.map(u => (
                <tr key={u.id} className="border-b border-gray-50 last:border-0 hover:bg-gray-50/50">
                  <td className="px-5 py-3.5 font-medium text-gray-900">{u.name}</td>
                  <td className="px-5 py-3.5 text-gray-500">{u.email}</td>
                  <td className="px-5 py-3.5">
                    <Badge label={u.role} variant={u.role === 'ADMIN' ? 'indigo' : 'gray'} />
                  </td>
                  <td className="px-5 py-3.5">
                    <Badge label={u.active ? 'Active' : 'Disabled'} variant={u.active ? 'green' : 'red'} />
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-400">
                    {new Date(u.createdAt).toLocaleDateString('en-IN')}
                  </td>
                  <td className="px-5 py-3.5">
                    {u.id !== user?.id && (
                      <button
                        onClick={() => toggleActive.mutate({ id: u.id, active: !u.active })}
                        className={`rounded-md border px-3 py-1 text-xs transition-colors
                          ${u.active
                            ? 'border-gray-200 text-gray-500 hover:border-red-200 hover:bg-red-50 hover:text-red-600'
                            : 'border-gray-200 text-gray-500 hover:border-emerald-200 hover:bg-emerald-50 hover:text-emerald-600'}`}>
                        {u.active ? 'Disable' : 'Enable'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>
    </div>
  )
}
