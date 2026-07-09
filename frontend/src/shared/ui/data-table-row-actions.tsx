'use client'

import { MoreVertical } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { cn } from '@/shared/lib/cn'
import { buttonVariants } from '@/shared/ui/button'

export interface RowAction {
  label: string
  icon?: React.ComponentType<{ className?: string }>
  onClick?: () => void
  disabled?: boolean
  variant?: 'default' | 'destructive'
  separator?: boolean
}

interface DataTableRowActionsProps {
  actions: RowAction[]
}

export function DataTableRowActions({ actions }: DataTableRowActionsProps) {
  const visible = actions.filter((a) => !a.disabled)

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        onClick={(e: React.MouseEvent) => e.stopPropagation()}
        className={cn(
          buttonVariants({ variant: 'ghost', size: 'sm' }),
          'h-8 w-8 p-0 data-[state=open]:bg-accent'
        )}
      >
        <MoreVertical className="size-4" />
        <span className="sr-only">Open menu</span>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-32">
        {visible.map((action, i) => (
          <div key={i}>
            {action.separator && <DropdownMenuSeparator />}
            <DropdownMenuItem
              onClick={(e: React.MouseEvent) => {
                e.stopPropagation()
                action.onClick?.()
              }}
              className={cn(action.variant === 'destructive' && 'text-destructive')}
            >
              {action.icon && <action.icon className="size-4" />}
              {action.label}
            </DropdownMenuItem>
          </div>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
