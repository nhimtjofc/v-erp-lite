'use client'

import { type Column } from '@tanstack/react-table'
import { Check, PlusCircle } from 'lucide-react'
import { Popover } from '@base-ui/react/popover'
import { cn } from '@/shared/lib/cn'
import { Badge } from '@/shared/ui/badge'
import { buttonVariants } from '@/shared/ui/button'
import { Separator } from '@/shared/ui/separator'

interface DataTableFacetedFilterProps<TData, TValue> {
  column?: Column<TData, TValue>
  title?: string
  options: {
    label: string
    value: string
    icon?: React.ComponentType<{ className?: string }>
  }[]
}

export function DataTableFacetedFilter<TData, TValue>({
  column,
  title,
  options,
}: DataTableFacetedFilterProps<TData, TValue>) {
  const facets = column?.getFacetedUniqueValues()
  const selectedValues = new Set(column?.getFilterValue() as string[])

  return (
    <Popover.Root>
      <Popover.Trigger
        className={cn(
          buttonVariants({ variant: 'outline', size: 'sm' }),
          'h-8 gap-1 border-dashed'
        )}
      >
        <PlusCircle />
        {title}
        {selectedValues.size > 0 && (
          <>
            <Separator orientation="vertical" className="mx-2 h-4" />
            <Badge
              variant="secondary"
              className="rounded-sm px-1 font-normal lg:hidden"
            >
              {selectedValues.size}
            </Badge>
            <div className="hidden gap-1 lg:flex">
              {selectedValues.size > 2 ? (
                <Badge
                  variant="secondary"
                  className="rounded-sm px-1 font-normal"
                >
                  {selectedValues.size} selected
                </Badge>
              ) : (
                options
                  .filter((option) => selectedValues.has(option.value))
                  .map((option) => (
                    <Badge
                      variant="secondary"
                      key={option.value}
                      className="rounded-sm px-1 font-normal"
                    >
                      {option.label}
                    </Badge>
                  ))
              )}
            </div>
          </>
        )}
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Positioner align="start" sideOffset={4}>
          <Popover.Popup className="z-50 w-[200px] rounded-lg border bg-popover p-1 shadow-md outline-none data-open:animate-in data-closed:animate-out data-closed:fade-out-0 data-open:fade-in-0 data-closed:zoom-out-95 data-open:zoom-in-95">
            <div className="flex flex-col gap-0.5 p-0.5">
              {options.map((option) => {
                const isSelected = selectedValues.has(option.value)
                return (
                  <div
                    key={option.value}
                    role="option"
                    aria-selected={isSelected}
                    onClick={() => {
                      if (isSelected) {
                        selectedValues.delete(option.value)
                      } else {
                        selectedValues.add(option.value)
                      }
                      const filterValues = Array.from(selectedValues)
                      column?.setFilterValue(
                        filterValues.length ? filterValues : undefined
                      )
                    }}
                    className={cn(
                      'flex cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none',
                      'hover:bg-accent hover:text-accent-foreground'
                    )}
                  >
                    <div
                      className={cn(
                        'flex size-4 shrink-0 items-center justify-center rounded-[4px] border',
                        isSelected
                          ? 'border-primary bg-primary text-primary-foreground'
                          : 'border-input'
                      )}
                    >
                      {isSelected && <Check className="size-3.5" />}
                    </div>
                    {option.icon && (
                      <option.icon className="size-4 text-muted-foreground" />
                    )}
                    <span className="flex-1 truncate">{option.label}</span>
                    {facets?.get(option.value) && (
                      <span className="flex size-4 items-center justify-center font-mono text-xs text-muted-foreground">
                        {facets.get(option.value)}
                      </span>
                    )}
                  </div>
                )
              })}
            </div>
            {selectedValues.size > 0 && (
              <>
                <Separator className="my-1" />
                <div className="p-0.5">
                  <button
                    onClick={() => column?.setFilterValue(undefined)}
                    className="flex w-full cursor-pointer items-center justify-center rounded-sm px-2 py-1.5 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  >
                    Clear filters
                  </button>
                </div>
              </>
            )}
          </Popover.Popup>
        </Popover.Positioner>
      </Popover.Portal>
    </Popover.Root>
  )
}
