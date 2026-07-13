'use client'

import { type Table } from '@tanstack/react-table'
import { Settings2 } from 'lucide-react'
import { cn } from '@/shared/lib/cn'
import { buttonVariants } from '@/shared/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'

interface DataTableViewOptionsProps<TData> {
  table: Table<TData>
}

export function DataTableViewOptions<TData>({
  table,
}: DataTableViewOptionsProps<TData>) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className={cn(
          buttonVariants({ variant: 'outline', size: 'sm' }),
          'ml-auto hidden h-8 gap-1 lg:flex'
        )}
      >
        <Settings2 />
        View
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-[150px]">
        <p className="px-2 py-1.5 text-xs font-medium text-muted-foreground">
          Toggle columns
        </p>
        <DropdownMenuSeparator />
        {table
          .getAllColumns()
          .filter(
            (column) =>
              typeof column.accessorFn !== 'undefined' && column.getCanHide()
          )
          .map((column) => {
            return (
              <DropdownMenuItem
                key={column.id}
                className="capitalize"
                onClick={() => column.toggleVisibility(!column.getIsVisible())}
              >
                <span className="mr-2 inline-block w-4">
                  {column.getIsVisible() ? '✓' : ''}
                </span>
                {column.id}
              </DropdownMenuItem>
            )
          })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
