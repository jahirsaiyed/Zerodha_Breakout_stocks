import { useState, useEffect } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import api from '../lib/api'
import type { UserConfig } from '../lib/types'
import { Badge } from '../components/Badge'

function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">{label}</label>
      {children}
      {hint && <p className="mt-1 text-xs text-gray-400">{hint}</p>}
    </div>
  )
}

const inputCls = "w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400"

export function SettingsPage() {
  const qc = useQueryClient()
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')
  const [telegramTestMsg, setTelegramTestMsg] = useState('')
  const [searchParams, setSearchParams] = useSearchParams()
  const [zerodhaMsg, setZerodhaMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const { data: config } = useQuery<UserConfig>({
    queryKey: ['config'],
    queryFn: () => api.get('/users/me/config').then(r => r.data.data),
  })

  const [form, setForm] = useState({
    maxPositions: '5',
    positionSizingMethod: 'FIXED',
    positionSizingValue: '10000',
    orderExpiryDays: '5',
    telegramChatId: '',
    zerodhaApiKey: '',
    zerodhaApiSecret: '',
    zerodhaTotpSecret: '',
  })

  useEffect(() => {
    if (config) {
      setForm({
        maxPositions: String(config.maxPositions),
        positionSizingMethod: config.positionSizingMethod,
        positionSizingValue: String(config.positionSizingValue),
        orderExpiryDays: String(config.orderExpiryDays),
        telegramChatId: config.telegramChatId ?? '',
        zerodhaApiKey: config.zerodhaApiKey ?? '',
        zerodhaApiSecret: '',
        zerodhaTotpSecret: '',
      })
    }
  }, [config])

  // Handle OAuth callback result in query params
  useEffect(() => {
    const zerodha = searchParams.get('zerodha')
    if (zerodha === 'connected') {
      setZerodhaMsg({ type: 'success', text: 'Zerodha connected successfully.' })
      qc.invalidateQueries({ queryKey: ['config'] })
      setSearchParams({}, { replace: true })
    } else if (zerodha === 'error') {
      const reason = searchParams.get('reason')
      setZerodhaMsg({ type: 'error', text: reason === 'session_expired'
        ? 'Connection timed out — please try again.'
        : 'Zerodha connection failed. Check your API key and try again.' })
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, setSearchParams, qc])

  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm(f => ({ ...f, [k]: e.target.value }))

  const update = useMutation({
    mutationFn: () => api.put('/users/me/config', {
      maxPositions: Number(form.maxPositions),
      positionSizingMethod: form.positionSizingMethod,
      positionSizingValue: Number(form.positionSizingValue),
      orderExpiryDays: Number(form.orderExpiryDays),
      telegramChatId: form.telegramChatId || null,
      zerodhaApiKey: form.zerodhaApiKey || null,
      zerodhaApiSecret: form.zerodhaApiSecret || null,
      zerodhaTotpSecret: form.zerodhaTotpSecret || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (e: any) => setError(e.response?.data?.error ?? 'Save failed'),
  })

  const disconnect = useMutation({
    mutationFn: () => api.delete('/zerodha/disconnect'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      setZerodhaMsg({ type: 'success', text: 'Zerodha disconnected.' })
    },
  })

  const telegramTest = useMutation({
    mutationFn: () => api.post('/users/me/telegram/test'),
    onSuccess: () => {
      setTelegramTestMsg('Test message sent!')
      setTimeout(() => setTelegramTestMsg(''), 4000)
    },
    onError: () => {
      setTelegramTestMsg('Failed to send — check your Chat ID and bot token.')
      setTimeout(() => setTelegramTestMsg(''), 4000)
    },
  })

  const handleSubmit = (e: FormEvent) => { e.preventDefault(); setError(''); update.mutate() }

  return (
    <div className="p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Settings</h1>
        <p className="text-sm text-gray-500">Configure your trading preferences and connections</p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl">
        {/* Trading config */}
        <div className="rounded-xl border border-gray-200 bg-white p-6">
          <h2 className="mb-5 text-sm font-semibold text-gray-900">Trading Configuration</h2>
          <div className="grid grid-cols-2 gap-4">
            <Field label="Max Positions" hint="Maximum simultaneous open positions (1–50)">
              <input type="number" min={1} max={50} value={form.maxPositions}
                onChange={set('maxPositions')} className={inputCls} />
            </Field>

            <Field label="Order Expiry Days" hint="Cancel unfilled orders after N days">
              <input type="number" min={1} max={30} value={form.orderExpiryDays}
                onChange={set('orderExpiryDays')} className={inputCls} />
            </Field>

            <Field label="Position Sizing Method">
              <select value={form.positionSizingMethod} onChange={set('positionSizingMethod')} className={inputCls}>
                <option value="FIXED">Fixed Amount</option>
                <option value="EQUAL">Equal Split</option>
                <option value="RISK_BASED">Risk-Based (%)</option>
              </select>
            </Field>

            <Field label="Sizing Value"
              hint={form.positionSizingMethod === 'RISK_BASED' ? 'Risk % of capital per trade' : 'Amount in ₹ per position'}>
              <input type="number" min={1} value={form.positionSizingValue}
                onChange={set('positionSizingValue')} className={inputCls} />
            </Field>
          </div>
        </div>

        {/* Zerodha */}
        <div className="rounded-xl border border-gray-200 bg-white p-6">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">Zerodha Connection</h2>
            <div className="flex items-center gap-3">
              {config?.zerodhaConnected
                ? <Badge label="Connected" variant="green" />
                : <Badge label="Not connected" variant="gray" />}
              {config?.zerodhaConnected ? (
                <button type="button" onClick={() => disconnect.mutate()}
                  disabled={disconnect.isPending}
                  className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-500
                             hover:border-red-200 hover:bg-red-50 hover:text-red-600 disabled:opacity-60">
                  {disconnect.isPending ? 'Disconnecting…' : 'Disconnect'}
                </button>
              ) : (
                <a href="/api/zerodha/login"
                  className="rounded-md bg-indigo-500 px-3 py-1.5 text-xs font-medium text-white
                             hover:bg-indigo-600">
                  Connect Zerodha
                </a>
              )}
            </div>
          </div>
          {zerodhaMsg && (
            <p className={`mb-4 rounded-md px-3 py-2 text-sm ${
              zerodhaMsg.type === 'success' ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
            }`}>{zerodhaMsg.text}</p>
          )}
          <div className="grid grid-cols-2 gap-4">
            <Field label="API Key" hint="Obtain from kite.trade developer console">
              <input type="text" value={form.zerodhaApiKey} onChange={set('zerodhaApiKey')}
                placeholder="Enter Zerodha API key" className={inputCls} />
            </Field>
            <Field label="API Secret" hint="Leave blank to keep current secret">
              <input type="password" value={form.zerodhaApiSecret} onChange={set('zerodhaApiSecret')}
                placeholder="Enter to update secret" className={inputCls} />
            </Field>
            <Field label="TOTP Secret (optional)"
              hint={config?.hasTotpSecret
                ? 'TOTP secret saved. Enter to replace, or leave blank.'
                : 'Zerodha 2FA TOTP secret for auto-generating login codes.'}>
              <input type="password" value={form.zerodhaTotpSecret} onChange={set('zerodhaTotpSecret')}
                placeholder={config?.hasTotpSecret ? '••••••••' : 'Optional TOTP secret'}
                className={inputCls} />
            </Field>
          </div>
        </div>

        {/* Telegram */}
        <div className="rounded-xl border border-gray-200 bg-white p-6">
          <div className="mb-5 flex items-center justify-between">
            <h2 className="text-sm font-semibold text-gray-900">Telegram Notifications</h2>
            {form.telegramChatId && (
              <button type="button" onClick={() => telegramTest.mutate()}
                disabled={telegramTest.isPending}
                className="rounded-md border border-gray-200 px-3 py-1 text-xs text-gray-500
                           hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600 disabled:opacity-60">
                {telegramTest.isPending ? 'Sending…' : 'Send Test'}
              </button>
            )}
          </div>
          {telegramTestMsg && (
            <p className={`mb-4 rounded-md px-3 py-2 text-sm ${
              telegramTestMsg.startsWith('Test')
                ? 'bg-emerald-50 text-emerald-700'
                : 'bg-red-50 text-red-700'
            }`}>{telegramTestMsg}</p>
          )}
          <Field label="Chat ID" hint="Your Telegram chat ID for trade alerts">
            <input type="text" value={form.telegramChatId} onChange={set('telegramChatId')}
              placeholder="e.g. 123456789" className={inputCls} />
          </Field>
        </div>

        <div className="flex items-center gap-3">
          <button type="submit" disabled={update.isPending}
            className="rounded-md bg-indigo-500 px-5 py-2 text-sm font-medium text-white
                       hover:bg-indigo-600 disabled:opacity-60">
            {update.isPending ? 'Saving…' : 'Save Changes'}
          </button>
          {saved && <span className="text-sm text-emerald-600">Saved successfully</span>}
          {error && <span className="text-sm text-red-600">{error}</span>}
        </div>
      </form>
    </div>
  )
}
