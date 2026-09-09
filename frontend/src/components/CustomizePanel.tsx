import { useEffect, useId, useRef, useState } from 'react'

interface CustomizePanelProps {
  /** Accessible label for the trigger button, e.g. "Customize stat cards". */
  label: string
  options: { key: string; label: string }[]
  /**
   * The current order. In the default (hideable) mode this is the subset of `options` keys
   * that are visible, in display order — omitted keys are hidden. In `reorderOnly` mode this
   * must always contain every option key (nothing can be hidden), in display order.
   */
  visibleKeys: string[]
  onChange: (nextVisibleKeys: string[]) => void
  /** When true, hides the show/hide checkboxes — every column stays visible, order-only. */
  reorderOnly?: boolean
  /** Shown as a "Reset to default order" action when provided. */
  defaultKeys?: string[]
}

function moveKeyBefore(order: string[], key: string, beforeKey: string): string[] {
  if (key === beforeKey) return order
  const without = order.filter(k => k !== key)
  const targetIndex = without.indexOf(beforeKey)
  without.splice(targetIndex, 0, key)
  return without
}

export function CustomizePanel({ label, options, visibleKeys, onChange, reorderOnly = false, defaultKeys }: CustomizePanelProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const dragKeyRef = useRef<string | null>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  const toggle = (key: string) => {
    const isVisible = visibleKeys.includes(key)
    onChange(isVisible ? visibleKeys.filter(k => k !== key) : [...visibleKeys, key])
  }

  // Display order: visible keys first (in their current order), then hidden ones — dragging
  // reorders within this combined list, while checkboxes control membership in `visibleKeys`.
  const hiddenKeys = reorderOnly ? [] : options.map(o => o.key).filter(k => !visibleKeys.includes(k))
  const displayKeys = [...visibleKeys, ...hiddenKeys]
  const visibleKeySet = new Set(visibleKeys)
  const labelByKey = new Map(options.map(o => [o.key, o.label]))

  const reorder = (key: string, beforeKey: string) => {
    const nextDisplay = moveKeyBefore(displayKeys, key, beforeKey)
    onChange(reorderOnly ? nextDisplay : nextDisplay.filter(k => visibleKeySet.has(k)))
  }

  const moveBy = (key: string, direction: -1 | 1) => {
    const index = displayKeys.indexOf(key)
    const targetIndex = index + direction
    if (targetIndex < 0 || targetIndex >= displayKeys.length) return
    // Moving down means "insert after the neighbor"; moving up means "insert before it".
    const beforeKey = direction === -1 ? displayKeys[targetIndex] : displayKeys[targetIndex + 1]
    if (beforeKey === undefined) {
      onChange(reorderOnly
        ? [...displayKeys.filter(k => k !== key), key]
        : [...displayKeys.filter(k => k !== key), key].filter(k => visibleKeySet.has(k)))
      return
    }
    reorder(key, beforeKey)
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-label={label}
        aria-haspopup="true"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(v => !v)}
        className="inline-flex items-center gap-1.5 rounded-md border border-gray-200 px-2.5 py-1 text-xs
                   text-gray-500 transition-colors hover:border-gray-300 hover:bg-gray-50 hover:text-gray-700"
      >
        <svg className="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
          <path fillRule="evenodd" d="M11.078 2.25c-.917 0-1.699.663-1.85 1.567L9.05 5.174a7.469 7.469 0 00-1.377.795l-1.577-.63a1.874 1.874 0 00-2.196.833l-.847 1.464a1.874 1.874 0 00.434 2.36l1.244 1.014c-.026.24-.04.48-.04.723s.014.484.04.723l-1.244 1.014a1.874 1.874 0 00-.434 2.36l.847 1.464a1.874 1.874 0 002.196.833l1.577-.63c.425.328.885.6 1.377.795l.178 1.357a1.875 1.875 0 001.85 1.567h1.844c.917 0 1.699-.663 1.85-1.567l.178-1.357a7.47 7.47 0 001.377-.795l1.577.63a1.875 1.875 0 002.196-.833l.847-1.464a1.874 1.874 0 00-.434-2.36l-1.244-1.014c.026-.24.04-.48.04-.723s-.014-.484-.04-.723l1.244-1.014a1.874 1.874 0 00.434-2.36l-.847-1.464a1.874 1.874 0 00-2.196-.833l-1.577.63a7.468 7.468 0 00-1.377-.795l-.178-1.357a1.875 1.875 0 00-1.85-1.567h-1.844zM12 15.75a3.75 3.75 0 100-7.5 3.75 3.75 0 000 7.5z" clipRule="evenodd" />
        </svg>
        Customize
      </button>

      {open && (
        <div id={panelId} role="menu" className="absolute right-0 z-10 mt-1.5 w-60 rounded-lg border border-gray-200 bg-white p-2 shadow-lg">
          {displayKeys.map((key, index) => {
            const isVisible = reorderOnly || visibleKeySet.has(key)
            const optionLabel = labelByKey.get(key) ?? key
            return (
              <div
                key={key}
                draggable
                onDragStart={e => {
                  dragKeyRef.current = key
                  e.dataTransfer.effectAllowed = 'move'
                  e.dataTransfer.setData('text/plain', key)
                }}
                onDragOver={e => e.preventDefault()}
                onDrop={e => {
                  e.preventDefault()
                  const dragged = dragKeyRef.current
                  dragKeyRef.current = null
                  if (dragged && dragged !== key) reorder(dragged, key)
                }}
                className="group flex items-center gap-1.5 rounded-md px-1.5 py-1.5 hover:bg-gray-50"
              >
                <span aria-hidden="true" className="cursor-grab select-none px-0.5 text-gray-300 group-hover:text-gray-400">
                  ⠿
                </span>
                <label className="flex flex-1 cursor-pointer items-center gap-2 text-sm text-gray-700">
                  {!reorderOnly && (
                    <input
                      type="checkbox"
                      checked={isVisible}
                      onChange={() => toggle(key)}
                      className="h-3.5 w-3.5 rounded border-gray-300 text-indigo-500 focus:ring-indigo-400"
                    />
                  )}
                  {optionLabel}
                </label>
                <div className="flex flex-col opacity-0 group-hover:opacity-100 focus-within:opacity-100">
                  <button
                    type="button"
                    aria-label={`Move ${optionLabel} up`}
                    disabled={index === 0}
                    onClick={() => moveBy(key, -1)}
                    className="px-1 text-[10px] leading-3 text-gray-400 hover:text-indigo-600 disabled:opacity-30"
                  >
                    ▲
                  </button>
                  <button
                    type="button"
                    aria-label={`Move ${optionLabel} down`}
                    disabled={index === displayKeys.length - 1}
                    onClick={() => moveBy(key, 1)}
                    className="px-1 text-[10px] leading-3 text-gray-400 hover:text-indigo-600 disabled:opacity-30"
                  >
                    ▼
                  </button>
                </div>
              </div>
            )
          })}

          {defaultKeys && (
            <button
              type="button"
              onClick={() => onChange(defaultKeys)}
              className="mt-1 w-full rounded-md px-2 py-1.5 text-left text-xs text-gray-400 hover:bg-gray-50 hover:text-gray-600"
            >
              Reset to default order
            </button>
          )}
        </div>
      )}
    </div>
  )
}
