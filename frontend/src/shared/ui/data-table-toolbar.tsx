'use client'

import { type Table } from '@tanstack/react-table'
import { X } from 'lucide-react'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { DataTableViewOptions } from '@/shared/ui/data-table-view-options'

interface DataTableToolbarProps<TData> {
  table: Table<TData>
  /** Placeholder for the global search input (default: "Filter...") */
  searchPlaceholder?: string
  /** ID of the column to use for global text search (default: "name") */
  searchColumnId?: string
  /** Optional filter-panel toggle button rendered next to search */
  filterButton?: React.ReactNode
  /** Children are rendered between the search input and the view toggle. Use for faceted filters. */
  children?: React.ReactNode
  /** Extra controls for the right side of the toolbar (next to View toggle) */
  toolbarRight?: React.ReactNode
}

export function DataTableToolbar<TData>({
  table,
  searchPlaceholder = 'Filter...',
  searchColumnId,
  filterButton,
  children,
  toolbarRight,
}: DataTableToolbarProps<TData>) {
  const isFiltered = table.getState().columnFilters.length > 0

  const searchColumn = searchColumnId
    ? table.getColumn(searchColumnId)
    : undefined
  const filterValue = (searchColumn?.getFilterValue() as string) ?? ''

  return (
    <div className="flex items-center justify-between">
      <div className="flex flex-1 items-center gap-2">
        {searchColumnId && (
          <Input
            placeholder={searchPlaceholder}
            value={filterValue}
            onChange={(event) =>
              searchColumn?.setFilterValue(event.target.value)
            }
            className="h-8 w-[150px] lg:w-[250px]"
          />
        )}
        {filterButton}
        {children}
        {isFiltered && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => table.resetColumnFilters()}
          >
            Reset
            <X />
          </Button>
        )}
      </div>
      <div className="flex items-center gap-2">
        {toolbarRight}
        <DataTableViewOptions table={table} />
      </div>
    </div>
  )
}
