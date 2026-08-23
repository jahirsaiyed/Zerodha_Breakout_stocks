import { useState, useEffect } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import api from '../lib/api'
import type { UserConfig, AccountSummary, LivePosition, TelegramChat } from '../lib/types'
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
  const [pwForm, setPwForm] = useState({ current: '', next: '', confirm: '' })
  const [pwMsg, setPwMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [searchParams, setSearchParams] = useSearchParams()
  const [zerodhaMsg, setZerodhaMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const [botToken, setBotToken] = useState('')
  const [botMsg, setBotMsg] = useState<{ ok: boolean; text: string } | null>(null)

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
    } else if (zerodha === 'error') {
      const reason = searchParams.get('reason')
      const text = reason === 'session_expired'
        ? 'Connection timed out — please try again.'
        : reason === 'init_failed'
        ? 'Could not start Zerodha login. Contact the administrator.'
        : 'Zerodha connection failed. Please try again or contact the administrator.'
      setZerodhaMsg({ type: 'error', text })
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
      qc.invalidateQueries({ queryKey: ['account-summary'] })
      setZerodhaMsg({ type: 'success', text: 'Zerodha disconnected.' })
    },
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

  return (
    <div className="p-4 sm:p-8">
      <div className="mb-6">
        <h1 className="text-xl font-semibold text-gray-950">Settings</h1>
        <p className="text-sm text-gray-500">Configure your trading preferences and connections</p>
      </div>

      {/* Account Overview — read-only, outside the save form */}
      <div className="mb-6 max-w-2xl rounded-xl border border-gray-200 bg-white p-6">
        <h2 className="mb-4 text-sm font-semibold text-gray-900">Account Overview</h2>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div>
            <p className="text-xs text-gray-500">Available Margin</p>
            {summary?.availableMargin != null ? (
              <p className="mt-1 text-xl font-semibold text-gray-950">
                ₹{summary.availableMargin.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </p>
            ) : (
              <p className="mt-1 text-xl font-semibold text-gray-400">—</p>
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
            {(() => {
              const total = livePositions.reduce((sum, p) => sum + (p.unrealisedPnl ?? 0), 0)
              const hasLive = livePositions.length > 0
              if (!hasLive) return <p className="mt-1 text-xl font-semibold text-gray-400">—</p>
              const cls = total >= 0 ? 'text-emerald-600' : 'text-red-600'
              const str = (total >= 0 ? '+' : '') + '₹' + total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
              return <p className={`mt-1 text-xl font-semibold ${cls}`}>{str}</p>
            })()}
            <p className="mt-0.5 text-xs text-gray-400">unrealised across active positions</p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl">
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
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
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
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
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

        {/* Telegram chat ID — only shown once bot is configured */}
        {config?.hasBotToken && (
          <div className="rounded-xl border border-gray-200 bg-white p-6">
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
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
          </div>
        )}

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

      {/* Telegram Bot — separate form, not part of main config save */}
      <div className="mt-8 max-w-2xl rounded-xl border border-gray-200 bg-white p-6">
        <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-sm font-semibold text-gray-900">Your Telegram Bot</h2>
            <p className="mt-0.5 text-xs text-gray-400">
              Connect your own bot to receive trade alerts. Get a token from{' '}
              <span className="font-medium text-gray-600">@BotFather</span> on Telegram.
            </p>
          </div>
          {config?.hasBotToken && (
            <Badge label="Connected" variant="green" />
          )}
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

      {/* Change Password (separate form — not part of config save) */}
      <form onSubmit={handlePasswordSubmit} className="mt-8 space-y-6 max-w-2xl">
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
    </div>
  )
}
