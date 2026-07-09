'use client'

import { useState } from 'react'
import { X, ChevronDown, Check } from 'lucide-react'
import { Popover } from '@base-ui/react/popover'
import { cn } from '@/shared/lib/cn'
import type { Table } from '@tanstack/react-table'

export interface FilterField {
  columnId: string
  label: string
  type: 'text' | 'select'
  options?: { label: string; value: string }[]
}

interface DataTableFilterPanelProps<TData> {
  table: Table<TData>
  fields: FilterField[]
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function getDisplayValue(field: FilterField, raw: any): string {
  if (field.type === 'select' && field.options) {
    const arr = Array.isArray(raw) ? raw : [raw]
    return arr
      .map((v: string) => field.options?.find((o) => o.value === v)?.label ?? v)
      .join(', ')
  }
  return String(raw ?? '')
}

/* ------------------------------------------------------------------ */
/*  Text editor — immediate apply on Enter / blur                     */
/* ------------------------------------------------------------------ */

function TextEditor({
  value,
  onChange,
  placeholder,
}: {
  value: string
  onChange: (v: string | undefined) => void
  placeholder: string
}) {
  const [draft, setDraft] = useState(value)

  const apply = () => onChange(draft || undefined)

  return (
    <div className="p-2">
      <input
        type="text"
        className={cn(
          'flex h-8 w-full rounded-md border border-input bg-transparent px-2 text-sm outline-none',
          'placeholder:text-muted-foreground/50',
          'focus-visible:ring-1 focus-visible:ring-ring'
        )}
        placeholder={placeholder}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            apply()
            // Close the popover by blurring the input
            ;(e.target as HTMLInputElement).blur()
          }
        }}
        onBlur={apply}
        autoFocus
      />
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Select editor — immediate toggle on click                         */
/* ------------------------------------------------------------------ */

function SelectEditor({
  options,
  selected,
  onChange,
}: {
  options: { label: string; value: string }[]
  selected: string[]
  onChange: (v: string[] | undefined) => void
}) {
  const selectedSet = new Set(selected)

  const toggle = (optionValue: string) => {
    const next = new Set(selectedSet)
    if (next.has(optionValue)) {
      next.delete(optionValue)
    } else {
      next.add(optionValue)
    }
    onChange(next.size > 0 ? Array.from(next) : undefined)
  }

  return (
    <div className="min-w-[140px]">
      {options.map((opt) => {
        const active = selectedSet.has(opt.value)
        return (
          <div
            key={opt.value}
            role="option"
            aria-selected={active}
            onClick={() => toggle(opt.value)}
            className={cn(
              'flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none',
              'hover:bg-accent hover:text-accent-foreground'
            )}
          >
            <div
              className={cn(
                'flex size-4 shrink-0 items-center justify-center rounded-[4px] border',
                active
                  ? 'border-primary bg-primary text-primary-foreground'
                  : 'border-input'
              )}
            >
              {active && <Check className="size-3.5" />}
            </div>
            <span className="flex-1 truncate">{opt.label}</span>
          </div>
        )
      })}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Chip-based filter                                                  */
/* ------------------------------------------------------------------ */

export function DataTableFilterPanel<TData>({
  table,
  fields,
}: DataTableFilterPanelProps<TData>) {
  const setFieldValue = (columnId: string, value: any) => {
    const col = table.getColumn(columnId)
    if (!col) return
    col.setFilterValue(
      value === '' || value === undefined || value === null
        ? undefined
        : value
    )
  }

  return (
    <>
      {fields.map((field) => {
        const val = table.getColumn(field.columnId)?.getFilterValue()
        const isActive = val !== undefined

        return (
          <Popover.Root key={field.columnId}>
            <Popover.Trigger
              className={cn(
                'inline-flex h-8 items-center gap-1 rounded-md border px-2.5 text-xs font-medium transition-colors',
                'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
                isActive
                  ? 'border-primary bg-primary/5 text-foreground'
                  : 'border-input bg-background text-muted-foreground hover:bg-accent hover:text-accent-foreground'
              )}
            >
              <span>{field.label}</span>
              {isActive && (
                <>
                  <span className="text-muted-foreground/40">:</span>
                  <span className="max-w-[100px] truncate font-semibold">
                    {getDisplayValue(field, val)}
                  </span>
                </>
              )}
              {isActive ? (
                <span
                  role="button"
                  tabIndex={0}
                  onClick={(e) => {
                    e.stopPropagation()
                    setFieldValue(field.columnId, undefined)
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.stopPropagation()
                      setFieldValue(field.columnId, undefined)
                    }
                  }}
                  className="-mr-1 ml-0.5 rounded-sm p-0.5 hover:bg-muted-foreground/15"
                >
                  <X className="size-3" />
                </span>
              ) : (
                <ChevronDown className="size-3 text-muted-foreground/40" />
              )}
            </Popover.Trigger>

            <Popover.Portal>
              <Popover.Positioner align="start" sideOffset={4}>
                <Popover.Popup className="z-50 min-w-[160px] rounded-lg border bg-popover p-1 shadow-md outline-none data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 data-closed:zoom-out-95 data-open:zoom-in-95">
                  {field.type === 'text' && (
                    <TextEditor
                      value={isActive ? String(val ?? '') : ''}
                      onChange={(v) => setFieldValue(field.columnId, v)}
                      placeholder={`Filter ${field.label.toLowerCase()}...`}
                    />
                  )}
                  {field.type === 'select' && field.options && (
                    <SelectEditor
                      options={field.options}
                      selected={isActive ? (Array.isArray(val) ? val : [val]) : []}
                      onChange={(v) => setFieldValue(field.columnId, v)}
                    />
                  )}
                </Popover.Popup>
              </Popover.Positioner>
            </Popover.Portal>
          </Popover.Root>
        )
      })}
    </>
  )
}
