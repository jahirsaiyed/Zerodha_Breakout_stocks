import { useEffect, useId, useRef, useState } from 'react'

interface CustomizePanelProps {
  /** Accessible label for the trigger button, e.g. "Customize stat cards". */
  label: string
  options: { key: string; label: string }[]
  visibleKeys: string[]
  onChange: (nextVisibleKeys: string[]) => void
}

export function CustomizePanel({ label, options, visibleKeys, onChange }: CustomizePanelProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()

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
        <div id={panelId} role="menu" className="absolute right-0 z-10 mt-1.5 w-56 rounded-lg border border-gray-200 bg-white p-2 shadow-lg">
          {options.map(opt => (
            <label
              key={opt.key}
              className="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm text-gray-700 hover:bg-gray-50"
            >
              <input
                type="checkbox"
                checked={visibleKeys.includes(opt.key)}
                onChange={() => toggle(opt.key)}
                className="h-3.5 w-3.5 rounded border-gray-300 text-indigo-500 focus:ring-indigo-400"
              />
              {opt.label}
            </label>
          ))}
        </div>
      )}
    </div>
  )
}
