import { useState, useEffect } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import api from '../lib/api'
import type { UserConfig, AccountSummary, LivePosition, TelegramChat } from '../lib/types'
import { Badge } from '../components/Badge'

type Tab = 'overview' | 'trading' | 'connections' | 'security'

function Field({ label, children, hint }: { label: string; children: React.ReactNode; hint?: string }) {
  return (
    <div>
      <label className="mb-1 block text-sm font-medium text-gray-700">{label}</label>
      {children}
      {hint && <p className="mt-1 text-xs text-gray-400">{hint}</p>}
    </div>
  )
}

function Toggle({
  checked,
  onChange,
  disabled,
  label,
}: {
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
  label: string
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full transition-colors
        focus:outline-none focus:ring-2 focus:ring-indigo-400 focus:ring-offset-2
        disabled:opacity-50 disabled:cursor-not-allowed
        ${checked ? 'bg-indigo-500' : 'bg-gray-200'}`}
    >
      <span
        className={`inline-block h-4 w-4 translate-x-1 rounded-full bg-white shadow transition-transform
          ${checked ? 'translate-x-6' : 'translate-x-1'}`}
      />
    </button>
  )
}

const inputCls = "w-full rounded-md border border-gray-200 px-3 py-2 text-sm focus:border-indigo-400 focus:outline-none focus:ring-1 focus:ring-indigo-400"

const TABS: { id: Tab; label: string }[] = [
  { id: 'overview',     label: 'Overview' },
  { id: 'trading',      label: 'Trading' },
  { id: 'connections',  label: 'Connections' },
  { id: 'security',     label: 'Security' },
]

export function SettingsPage() {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('overview')
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')
  const [telegramTestMsg, setTelegramTestMsg] = useState('')
  const [pwForm, setPwForm] = useState({ current: '', next: '', confirm: '' })
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const [zerodhaMsg, setZerodhaMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [botToken, setBotToken] = useState('')
  const [botMsg, setBotMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [pauseError, setPauseError] = useState('')

  const { data: config } = useQuery<UserConfig>({
    queryKey: ['config'],
    queryFn: () => api.get('/users/me/config').then(r => r.data.data),
  })

  const { data: summary } = useQuery<AccountSummary>({
    queryKey: ['account-summary'],
    queryFn: () => api.get('/users/me/account-summary').then(r => r.data.data),
    refetchInterval: 60_000,
    staleTime: 30_000,
  })

  const { data: livePositions = [] } = useQuery<LivePosition[]>({
    queryKey: ['positions-live'],
    queryFn: () => api.get('/portfolio/positions/live').then(r => r.data),
    refetchInterval: 60_000,
    staleTime: 30_000,
  })

  const { data: telegramChats = [] } = useQuery<TelegramChat[]>({
    queryKey: ['telegram-chats'],
    queryFn: () => api.get('/users/me/telegram/chats').then(r => r.data.data),
    staleTime: 30_000,
    enabled: config?.hasBotToken === true,
  })

  const [form, setForm] = useState({
    maxPositions: '5',
    positionSizingMethod: 'FIXED',
    positionSizingValue: '10000',
    orderExpiryDays: '5',
    marginUsagePercent: '100',
    marginUsageFixedLimit: '',
    telegramChatId: '',
    zerodhaTotpSecret: '',
  })

  useEffect(() => {
    if (config) {
      setForm({
        maxPositions: String(config.maxPositions),
        positionSizingMethod: config.positionSizingMethod,
        positionSizingValue: String(config.positionSizingValue),
        orderExpiryDays: String(config.orderExpiryDays),
        marginUsagePercent: String(config.marginUsagePercent),
        marginUsageFixedLimit: config.marginUsageFixedLimit != null ? String(config.marginUsageFixedLimit) : '',
        telegramChatId: config.telegramChatId ?? '',
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
      qc.invalidateQueries({ queryKey: ['account-summary'] })
      setSearchParams({}, { replace: true })
      setActiveTab('connections')
    } else if (zerodha === 'error') {
      const reason = searchParams.get('reason')
      const text = reason === 'session_expired'
        ? 'Connection timed out — please try again.'
        : reason === 'init_failed'
        ? 'Could not start Zerodha login. Contact the administrator.'
        : 'Zerodha connection failed. Please try again or contact the administrator.'
      setZerodhaMsg({ type: 'error', text })
      setSearchParams({}, { replace: true })
      setActiveTab('connections')
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
      marginUsagePercent: Number(form.marginUsagePercent),
      marginUsageFixedLimit: form.marginUsageFixedLimit !== '' ? Number(form.marginUsageFixedLimit) : null,
      telegramChatId: form.telegramChatId || null,
      zerodhaTotpSecret: form.zerodhaTotpSecret || null,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (e: any) => setError(e.response?.data?.error ?? 'Save failed'),
  })

  const togglePause = useMutation({
    mutationFn: (patch: { tradingPaused?: boolean; syncPaused?: boolean }) =>
      api.put('/users/me/config', patch),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      setPauseError('')
    },
    onError: (e: any) => setPauseError(e.response?.data?.error ?? 'Failed to update setting'),
  })

  const disconnect = useMutation({
    mutationFn: () => api.delete('/zerodha/disconnect'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      qc.invalidateQueries({ queryKey: ['account-summary'] })
      setZerodhaMsg({ type: 'success', text: 'Zerodha disconnected.' })
    },
    onError: (e: any) => setZerodhaMsg({ type: 'error', text: e.response?.data?.error ?? 'Failed to disconnect Zerodha' }),
  })

  const changePassword = useMutation({
    mutationFn: () => api.post('/users/me/password', {
      currentPassword: pwForm.current,
      newPassword: pwForm.next,
    }),
    onSuccess: () => {
      setPwMsg({ ok: true, text: 'Password changed successfully.' })
      setPwForm({ current: '', next: '', confirm: '' })
      setTimeout(() => setPwMsg(null), 4000)
    },
    onError: (e: any) => setPwMsg({ ok: false, text: e.response?.data?.error ?? 'Password change failed.' }),
  })

  const connectBot = useMutation({
    mutationFn: () => api.post('/users/me/telegram/bot', { botToken }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      qc.invalidateQueries({ queryKey: ['telegram-chats'] })
      setBotToken('')
      setBotMsg({ ok: true, text: 'Bot connected successfully.' })
      setTimeout(() => setBotMsg(null), 4000)
    },
    onError: (e: any) => {
      setBotMsg({ ok: false, text: e.response?.data?.error ?? 'Failed to connect bot — check the token.' })
    },
  })

  const disconnectBot = useMutation({
    mutationFn: () => api.delete('/users/me/telegram/bot'),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['config'] })
      qc.invalidateQueries({ queryKey: ['telegram-chats'] })
      setBotMsg({ ok: true, text: 'Bot disconnected.' })
      setTimeout(() => setBotMsg(null), 4000)
    },
    onError: (e: any) => {
      setBotMsg({ ok: false, text: e.response?.data?.error ?? 'Failed to disconnect bot' })
      setTimeout(() => setBotMsg(null), 4000)
    },
  })

  const telegramTest = useMutation({
    mutationFn: () => api.post('/users/me/telegram/test'),
    onSuccess: () => {
      setTelegramTestMsg('Test message sent!')
      setTimeout(() => setTelegramTestMsg(''), 4000)
    },
    onError: (e: any) => {
      setTelegramTestMsg(e.response?.data?.error ?? 'Failed to send — check your Chat ID and bot token.')
      setTimeout(() => setTelegramTestMsg(''), 4000)
    },
  })

  const handlePasswordSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (pwForm.next !== pwForm.confirm) {
      setPwMsg({ ok: false, text: 'New passwords do not match.' })
      return
    }
    if (pwForm.next.length < 8) {
      setPwMsg({ ok: false, text: 'New password must be at least 8 characters.' })
      return
    }
    setPwMsg(null)
    changePassword.mutate()
  }

  const handleBotConnect = (e: FormEvent) => { e.preventDefault(); setBotMsg(null); connectBot.mutate() }
  const handleSubmit = (e: FormEvent) => { e.preventDefault(); setError(''); update.mutate() }

  // ── Derived values ────────────────────────────────────────────────────────

  const usableMargin = (() => {
    if (summary?.availableMargin == null) return null
    const pctCap = summary.availableMargin * (config?.marginUsagePercent ?? 100) / 100
    const fixedCap = config?.marginUsageFixedLimit ?? null
    return fixedCap != null ? Math.min(pctCap, fixedCap) : pctCap
  })()

  const openPnl = livePositions.reduce((sum, p) => sum + (p.unrealisedPnl ?? 0), 0)

  // ── Tab panels ────────────────────────────────────────────────────────────

  const overviewPanel = (
    <div className="space-y-6">
      {/* Account summary */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-4 text-sm font-semibold text-gray-900">Account Overview</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div>
            <p className="text-xs text-gray-500">Available Margin</p>
            {summary?.availableMargin != null ? (
              <>
                <p className="mt-1 text-xl font-semibold text-gray-950">
                  ₹{summary.availableMargin.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                </p>
                {usableMargin != null && usableMargin < summary.availableMargin && (() => {
                  const label = config?.marginUsageFixedLimit != null &&
                    config.marginUsageFixedLimit <= summary.availableMargin * (config.marginUsagePercent / 100)
                    ? 'fixed cap'
                    : `${config?.marginUsagePercent}%`
                  return (
                    <p className="mt-0.5 text-xs text-gray-400">
                      ₹{usableMargin.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} usable ({label})
                    </p>
                  )
                })()}
              </>
            ) : (
              <p className="mt-1 text-xl font-semibold text-gray-400">—</p>
            )}
            {config?.zerodhaConnected && summary !== undefined && summary.availableMargin == null && (
              <p className="mt-0.5 text-xs text-amber-500">Session expired — please reconnect</p>
            )}
            {!config?.zerodhaConnected && (
              <p className="mt-0.5 text-xs text-gray-400">Connect Zerodha to see margin</p>
            )}
          </div>
          <div>
            <p className="text-xs text-gray-500">Positions Used</p>
            <p className="mt-1 text-xl font-semibold text-gray-950">
              {summary != null ? `${summary.activePositions} / ${summary.maxPositions}` : '—'}
            </p>
            <p className="mt-0.5 text-xs text-gray-400">active / max allowed</p>
          </div>
          <div>
            <p className="text-xs text-gray-500">Open P&L</p>
            {livePositions.length > 0 ? (
              <p className={`mt-1 text-xl font-semibold ${openPnl >= 0 ? 'text-emerald-600' : 'text-red-600'}`}>
                {(openPnl >= 0 ? '+' : '') + '₹' + openPnl.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </p>
            ) : (
              <p className="mt-1 text-xl font-semibold text-gray-400">—</p>
            )}
            <p className="mt-0.5 text-xs text-gray-400">unrealised across active positions</p>
          </div>
        </div>
      </div>

      {/* Pause controls */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-1 text-sm font-semibold text-gray-900">System Controls</h2>
        <p className="mb-5 text-xs text-gray-400">Changes take effect immediately — no save required.</p>
        {pauseError && <p className="mb-4 text-xs text-red-600">{pauseError}</p>}
        <div className="space-y-5">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-gray-800">Pause Trading</p>
              <p className="mt-0.5 text-xs text-gray-400">
                No new entry orders will be placed. Active positions are unaffected.
              </p>
            </div>
            <div className="flex items-center gap-3">
              {config?.tradingPaused && <Badge label="Paused" variant="gray" />}
              <Toggle
                label="Pause trading"
                checked={config?.tradingPaused ?? false}
                onChange={v => togglePause.mutate({ tradingPaused: v })}
                disabled={togglePause.isPending}
              />
            </div>
          </div>

          <div className="border-t border-gray-100" />

          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-gray-800">Pause Signal Evaluation</p>
              <p className="mt-0.5 text-xs text-gray-400">
                New signals from the sheet won't trigger orders. Signals still sync to the database.
              </p>
            </div>
            <div className="flex items-center gap-3">
              {config?.syncPaused && <Badge label="Paused" variant="gray" />}
              <Toggle
                label="Pause signal evaluation"
                checked={config?.syncPaused ?? false}
                onChange={v => togglePause.mutate({ syncPaused: v })}
                disabled={togglePause.isPending}
              />
            </div>
          </div>
        </div>
      </div>

      {/* Status summary */}
      <div className="rounded-xl border border-gray-200 bg-white p-5">
        <h2 className="mb-3 text-sm font-semibold text-gray-900">Connection Status</h2>
        <div className="flex flex-wrap gap-4">
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">Zerodha</span>
            <Badge
              label={config?.zerodhaConnected ? 'Connected' : 'Not connected'}
              variant={config?.zerodhaConnected ? 'green' : 'gray'}
            />
          </div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">Telegram Bot</span>
            <Badge
              label={config?.hasBotToken ? 'Connected' : 'Not connected'}
              variant={config?.hasBotToken ? 'green' : 'gray'}
            />
          </div>
        </div>
      </div>
    </div>
  )

  const tradingPanel = (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Pause controls (mirrored for quick access) */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-1 text-sm font-semibold text-gray-900">Quick Controls</h2>
        <p className="mb-5 text-xs text-gray-400">Changes take effect immediately — no save required.</p>
        {pauseError && <p className="mb-4 text-xs text-red-600">{pauseError}</p>}
        <div className="flex flex-col gap-4 sm:flex-row sm:gap-8">
          <div className="flex items-center gap-3">
            <Toggle
              label="Pause trading"
              checked={config?.tradingPaused ?? false}
              onChange={v => togglePause.mutate({ tradingPaused: v })}
              disabled={togglePause.isPending}
            />
            <div>
              <p className="text-sm font-medium text-gray-800">Pause Trading</p>
              <p className="text-xs text-gray-400">No new entry orders</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Toggle
              label="Pause signal evaluation"
              checked={config?.syncPaused ?? false}
              onChange={v => togglePause.mutate({ syncPaused: v })}
              disabled={togglePause.isPending}
            />
            <div>
              <p className="text-sm font-medium text-gray-800">Pause Signals</p>
              <p className="text-xs text-gray-400">Don't act on new signals</p>
            </div>
          </div>
        </div>
      </div>

      {/* Trading config */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-5 text-sm font-semibold text-gray-900">Trading Configuration</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Max Positions" hint="Maximum simultaneous open positions (1–50)">
            <input type="number" min={1} max={50} value={form.maxPositions}
              onChange={set('maxPositions')} className={inputCls} />
          </Field>

          <Field label="Order Expiry Days" hint="Cancel unfilled orders after N days">
            <input type="number" min={1} max={30} value={form.orderExpiryDays}
              onChange={set('orderExpiryDays')} className={inputCls} />
          </Field>

          <Field label="Margin Usage (%)" hint="Percentage of available margin the system may deploy (1–100%)">
            <input type="number" min={1} max={100} step={1} value={form.marginUsagePercent}
              onChange={set('marginUsagePercent')} className={inputCls} />
          </Field>

          <Field label="Max Margin (₹)" hint="Optional fixed cap in ₹. Leave blank to rely on the percentage limit only.">
            <input type="number" step={1000} value={form.marginUsageFixedLimit}
              onChange={set('marginUsageFixedLimit')} placeholder="e.g. 50000"
              className={inputCls} />
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
  )

  const connectionsPanel = (
    <div className="space-y-6">
      {/* Zerodha */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Zerodha</h2>
            <p className="mt-0.5 text-xs text-gray-400">Connect your Zerodha account to enable live trading.</p>
          </div>
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
        <form onSubmit={handleSubmit}>
          <Field label="TOTP Secret (optional)"
            hint={config?.hasTotpSecret
              ? 'TOTP secret saved. Enter to replace, or leave blank.'
              : 'Zerodha 2FA TOTP secret for auto-generating login codes.'}>
            <div className="flex items-end gap-3">
              <input type="password" value={form.zerodhaTotpSecret} onChange={set('zerodhaTotpSecret')}
                placeholder={config?.hasTotpSecret ? '••••••••' : 'Optional TOTP secret'}
                className={inputCls + ' max-w-xs'} />
              <button type="submit" disabled={update.isPending}
                className="rounded-md border border-gray-200 px-3 py-2 text-xs text-gray-500
                           hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600 disabled:opacity-60">
                {update.isPending ? 'Saving…' : 'Save'}
              </button>
            </div>
          </Field>
          {saved && <p className="mt-2 text-xs text-emerald-600">Saved successfully</p>}
        </form>
      </div>

      {/* Telegram Bot */}
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Telegram Bot</h2>
            <p className="mt-0.5 text-xs text-gray-400">
              Connect your own bot to receive trade alerts. Get a token from{' '}
              <span className="font-medium text-gray-600">@BotFather</span> on Telegram.
            </p>
          </div>
          {config?.hasBotToken && <Badge label="Connected" variant="green" />}
        </div>

        {botMsg && (
          <p className={`mb-4 rounded-md px-3 py-2 text-sm ${
            botMsg.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'
          }`}>{botMsg.text}</p>
        )}

        {config?.hasBotToken ? (
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-indigo-100 text-indigo-600 text-sm font-bold select-none">
                {(config.botName ?? 'B').charAt(0).toUpperCase()}
              </div>
              <div>
                <p className="text-sm font-medium text-gray-900">{config.botName ?? 'Unknown Bot'}</p>
                {config.botUsername && (
                  <p className="text-xs text-gray-400">@{config.botUsername}</p>
                )}
              </div>
            </div>
            <button
              type="button"
              onClick={() => disconnectBot.mutate()}
              disabled={disconnectBot.isPending}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-xs text-gray-500
                         hover:border-red-200 hover:bg-red-50 hover:text-red-600 disabled:opacity-60">
              {disconnectBot.isPending ? 'Disconnecting…' : 'Disconnect Bot'}
            </button>
          </div>
        ) : (
          <form onSubmit={handleBotConnect} className="flex flex-wrap items-end gap-3">
            <div className="flex-1 min-w-[260px]">
              <label className="mb-1 block text-xs font-medium text-gray-600">Bot Token</label>
              <input
                type="password"
                value={botToken}
                onChange={e => setBotToken(e.target.value)}
                placeholder="1234567890:AAGk…"
                required
                autoComplete="off"
                className={inputCls}
              />
              <p className="mt-1 text-xs text-gray-400">
                Message @BotFather → /newbot → copy the token here
              </p>
            </div>
            <button
              type="submit"
              disabled={connectBot.isPending || !botToken.trim()}
              className="rounded-md bg-indigo-500 px-4 py-2 text-sm font-medium text-white
                         hover:bg-indigo-600 disabled:opacity-60">
              {connectBot.isPending ? 'Connecting…' : 'Connect Bot'}
            </button>
          </form>
        )}
      </div>

      {/* Telegram Notifications — only shown once bot is configured */}
      {config?.hasBotToken && (
        <div className="rounded-xl border border-gray-200 bg-white p-6">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold text-gray-900">Notification Chat</h2>
              <p className="mt-0.5 text-xs text-gray-400">Choose where trade alerts are sent.</p>
            </div>
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
          <form onSubmit={handleSubmit} className="space-y-4">
            {(telegramChats.length > 0 || form.telegramChatId) ? (
              <Field label="Notification Chat"
                hint="Send any message to the bot to discover more chats, or enter an ID manually in the last option.">
                <select value={form.telegramChatId} onChange={set('telegramChatId')} className={inputCls}>
                  <option value="">— Select a chat —</option>
                  {telegramChats.map(chat => (
                    <option key={chat.chatId} value={chat.chatId}>
                      {chat.chatTitle} ({chat.chatType})
                    </option>
                  ))}
                  {form.telegramChatId && !telegramChats.some(c => c.chatId === form.telegramChatId) && (
                    <option value={form.telegramChatId}>{form.telegramChatId} (current)</option>
                  )}
                </select>
              </Field>
            ) : (
              <Field label="Chat ID"
                hint="Send any message to your bot in the desired chat or channel — it will appear here as a selectable option. Or enter the ID manually.">
                <input type="text" value={form.telegramChatId} onChange={set('telegramChatId')}
                  placeholder="e.g. 123456789" autoComplete="off" className={inputCls} />
              </Field>
            )}
            <button type="submit" disabled={update.isPending}
              className="rounded-md border border-gray-200 px-3 py-2 text-xs text-gray-500
                         hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-600 disabled:opacity-60">
              {update.isPending ? 'Saving…' : 'Save Chat'}
            </button>
            {saved && <span className="ml-2 text-xs text-emerald-600">Saved</span>}
          </form>
        </div>
      )}
    </div>
  )

  const securityPanel = (
    <form onSubmit={handlePasswordSubmit} className="space-y-6">
      <div className="rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-5 text-sm font-semibold text-gray-900">Change Password</h2>
        {pwMsg && (
          <p className={`mb-4 rounded-md px-3 py-2 text-sm ${pwMsg.ok ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}`}>
            {pwMsg.text}
          </p>
        )}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <Field label="Current Password">
            <input type="password" value={pwForm.current}
              onChange={e => setPwForm(f => ({ ...f, current: e.target.value }))}
              placeholder="Current password" className={inputCls} />
          </Field>
          <Field label="New Password">
            <input type="password" value={pwForm.next}
              onChange={e => setPwForm(f => ({ ...f, next: e.target.value }))}
              placeholder="Min 8 characters" className={inputCls} />
          </Field>
          <Field label="Confirm New Password">
            <input type="password" value={pwForm.confirm}
              onChange={e => setPwForm(f => ({ ...f, confirm: e.target.value }))}
              placeholder="Repeat new password" className={inputCls} />
          </Field>
        </div>
        <button type="submit" disabled={changePassword.isPending}
          className="mt-4 rounded-md bg-gray-800 px-5 py-2 text-sm font-medium text-white
                     hover:bg-gray-900 disabled:opacity-60">
          {changePassword.isPending ? 'Changing…' : 'Change Password'}
        </button>
      </div>
    </form>
  )

  const panels: Record<Tab, React.ReactNode> = {
    overview: overviewPanel,
    trading: tradingPanel,
    connections: connectionsPanel,
    security: securityPanel,
  }

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Settings</h1>
        <p className="text-sm text-gray-500">Configure your trading preferences and connections</p>
      </div>

      {/* Tab bar */}
      <div className="mb-6 flex gap-1 border-b border-gray-200">
        {TABS.map(tab => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm font-medium transition-colors
              ${activeTab === tab.id
                ? 'border-b-2 border-indigo-500 text-indigo-600'
                : 'text-gray-500 hover:text-gray-800'}`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="max-w-2xl">
        {panels[activeTab]}
      </div>
    </div>
  )
}
