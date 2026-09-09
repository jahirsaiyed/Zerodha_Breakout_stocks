import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CustomizePanel } from '../components/CustomizePanel'

const OPTIONS = [
  { key: 'a', label: 'Alpha' },
  { key: 'b', label: 'Beta' },
  { key: 'c', label: 'Gamma' },
]

describe('CustomizePanel', () => {
  it('toggles a column off and calls onChange without it', async () => {
    const onChange = vi.fn()
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']} onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Beta' }))

    expect(onChange).toHaveBeenCalledWith(['a', 'c'])
  })

  it('moves a column up via the arrow button', async () => {
    const onChange = vi.fn()
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']} onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move Gamma up' }))

    expect(onChange).toHaveBeenCalledWith(['a', 'c', 'b'])
  })

  it('moves a column down via the arrow button', async () => {
    const onChange = vi.fn()
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']} onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move Alpha down' }))

    expect(onChange).toHaveBeenCalledWith(['b', 'a', 'c'])
  })

  it('disables the up arrow for the first item and the down arrow for the last', async () => {
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']} onChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))

    expect(screen.getByRole('button', { name: 'Move Alpha up' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Move Gamma down' })).toBeDisabled()
  })

  it('reorderOnly mode hides checkboxes and keeps every key in the emitted order', async () => {
    const onChange = vi.fn()
    render(
      <CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']}
        onChange={onChange} reorderOnly />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Move Beta up' }))
    expect(onChange).toHaveBeenCalledWith(['b', 'a', 'c'])
  })

  it('keeps a hidden column out of the emitted order when moved among other hidden columns', async () => {
    const onChange = vi.fn()
    // "c" is hidden (not in visibleKeys) — moving it around should never add it back.
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b']} onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move Gamma up' }))

    expect(onChange).toHaveBeenCalledWith(['a', 'b'])
  })

  it('shows a reset action when defaultKeys is provided and restores it on click', async () => {
    const onChange = vi.fn()
    render(
      <CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['c', 'a']}
        onChange={onChange} defaultKeys={['a', 'b', 'c']} />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    await userEvent.click(screen.getByRole('button', { name: /reset to default order/i }))

    expect(onChange).toHaveBeenCalledWith(['a', 'b', 'c'])
  })

  it('omits the reset action when defaultKeys is not provided', async () => {
    render(<CustomizePanel label="Customize" options={OPTIONS} visibleKeys={['a', 'b', 'c']} onChange={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Customize' }))
    expect(screen.queryByRole('button', { name: /reset to default order/i })).not.toBeInTheDocument()
  })
})
