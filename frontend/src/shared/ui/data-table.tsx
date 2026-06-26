'use client'

import { useEffect, useState } from 'react'
import {
  flexRender,
  getCoreRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type ColumnFiltersState,
  type PaginationState,
  type SortingState,
  type Table as TanStackTable,
  type VisibilityState,
} from '@tanstack/react-table'
import { cn } from '@/shared/lib/cn'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { DataTablePagination } from '@/shared/ui/data-table-pagination'
import { DataTableToolbar } from '@/shared/ui/data-table-toolbar'
import {
  DataTableFilterPanel,
  type FilterField,
} from '@/shared/ui/data-table-filter-panel'
import {
  DataTableDensityToggle,
  type Density,
} from '@/shared/ui/data-table-density-toggle'

/* ------------------------------------------------------------------ */
/*  Density helpers                                                    */
/* ------------------------------------------------------------------ */

const DENSITY_STORAGE_KEY = 'data-table-density'

function loadDensity(): Density {
  if (typeof window === 'undefined') return 'compact'
  return (localStorage.getItem(DENSITY_STORAGE_KEY) as Density) ?? 'compact'
}

function saveDensity(d: Density) {
  if (typeof window === 'undefined') return
  localStorage.setItem(DENSITY_STORAGE_KEY, d)
}

const densityPadding: Record<Density, string> = {
  compact: 'py-2',
  standard: 'py-3',
  comfortable: 'py-4',
}

/* ------------------------------------------------------------------ */
/*  DataTable — TanStack Table-backed data table with toolbar + pag.  */
/*                                                                     */
/*  Usage:                                                             */
/*    <DataTable columns={columns} data={data} searchColumnId="name">  */
/*      <DataTableFacetedFilter column={table.getColumn("status")} ..> */
/*    </DataTable>                                                     */
/* ------------------------------------------------------------------ */

export interface DataTableProps<TData> {
  columns: ColumnDef<TData>[]
  data: TData[]
  /** Show skeleton loading rows */
  loading?: boolean
  /** Number of skeleton rows to show while loading (default: 5) */
  skeletonRows?: number
  /** Empty state message (default: "No results.") */
  emptyMessage?: string
  /** Called when a data row is clicked */
  onRowClick?: (row: TData) => void
  /** Called when row selection changes */
  onRowSelectionChange?: (selectedRows: TData[]) => void
  /** Initial page size (default: 25) */
  pageSize?: number
  /**
   * If set, shows a global search input filtered against this column ID.
   * Column must use a text-based filterFn (default: `includesString`).
   */
  searchColumnId?: string
  /** Placeholder for the search input (default: "Filter...") */
  searchPlaceholder?: string
  /** Enable row selection features (default: false) */
  enableRowSelection?: boolean
  /** Column IDs to hide by default (stays searchable, just not visible) */
  defaultHiddenColumns?: string[]
  /** Configure the advanced filter panel. When set, filter chips appear in the toolbar. */
  filterFields?: FilterField[]
  /**
   * Render prop for toolbar content (between search input and view toggle).
   * Receives the TanStack Table instance so you can access columns for faceted filters.
   */
  renderToolbar?: (table: TanStackTable<TData>) => React.ReactNode
}

export function DataTable<TData>({
  columns,
  data,
  loading = false,
  skeletonRows = 5,
  emptyMessage = 'No results.',
  onRowClick,
  onRowSelectionChange,
  pageSize = 25,
  searchColumnId,
  searchPlaceholder,
  enableRowSelection = false,
  renderToolbar,
  filterFields,
  defaultHiddenColumns,
}: DataTableProps<TData>) {
  const [rowSelection, setRowSelection] = useState({})
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(
    () =>
      defaultHiddenColumns
        ? Object.fromEntries(defaultHiddenColumns.map((id) => [id, false]))
        : {}
  )
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([])
  const [sorting, setSorting] = useState<SortingState>([])
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize,
  })
  const [density, setDensity] = useState<Density>(loadDensity)

  const handleDensityChange = (d: Density) => {
    setDensity(d)
    saveDensity(d)
  }

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
      columnVisibility,
      rowSelection,
      columnFilters,
      pagination,
    },
    initialState: {
      pagination: { pageIndex: 0, pageSize },
    },
    enableRowSelection,
    onRowSelectionChange: setRowSelection,
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    onPaginationChange: setPagination,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  })

  // Notify parent when row selection changes
  useEffect(() => {
    onRowSelectionChange?.(
      table.getSelectedRowModel().rows.map((r) => r.original)
    )
    // Intentionally only fire on explicit selection changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rowSelection, onRowSelectionChange])

  const headerGroups = table.getHeaderGroups()
  const rowModel = table.getRowModel()
  const cellPadding = densityPadding[density]

  return (
    <div className="flex flex-col gap-4">
      <DataTableToolbar
        table={table}
        searchColumnId={searchColumnId}
        searchPlaceholder={searchPlaceholder}
        filterButton={
          filterFields && (
            <DataTableFilterPanel table={table} fields={filterFields} />
          )
        }
        toolbarRight={
          <DataTableDensityToggle
            density={density}
            onChange={handleDensityChange}
          />
        }
      >
        {renderToolbar?.(table)}
      </DataTableToolbar>
      <div className="overflow-hidden rounded-md border">
        <Table>
          <TableHeader>
            {headerGroups.map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    colSpan={header.colSpan}
                    style={{
                      width:
                        header.getSize() !== 150
                          ? header.getSize()
                          : undefined,
                    }}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {loading ? (
              <SkeletonRows
                columns={headerGroups[0]?.headers.length ?? columns.length}
                rows={skeletonRows}
                cellPadding={cellPadding}
              />
            ) : rowModel.rows.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={
                    headerGroups[0]?.headers.length ?? columns.length
                  }
                  className="h-24 text-center text-muted-foreground"
                >
                  {emptyMessage}
                </TableCell>
              </TableRow>
            ) : (
              rowModel.rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && 'selected'}
                  className={cn(onRowClick && 'cursor-pointer')}
                  onClick={
                    onRowClick ? () => onRowClick(row.original) : undefined
                  }
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id} className={cellPadding}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>
      <DataTablePagination table={table} />
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  SkeletonRows — loading placeholder                                 */
/* ------------------------------------------------------------------ */

function SkeletonRows({
  columns,
  rows,
  cellPadding,
}: {
  columns: number
  rows: number
  cellPadding: string
}) {
  return (
    <>
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <TableRow key={rowIdx}>
          {Array.from({ length: columns }).map((_, colIdx) => (
            <TableCell key={colIdx} className={cellPadding}>
              <div className="h-4 w-full animate-pulse rounded bg-muted" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </>
  )
}
