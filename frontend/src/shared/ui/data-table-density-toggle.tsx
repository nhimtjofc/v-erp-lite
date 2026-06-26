'use client'

import { GripHorizontal } from 'lucide-react'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'
import { cn } from '@/shared/lib/cn'
import { buttonVariants } from '@/shared/ui/button'

export type Density = 'compact' | 'standard' | 'comfortable'

const densityConfig: Record<Density, { label: string; description: string }> = {
  compact: { label: 'Compact', description: 'Tighter spacing, more rows' },
  standard: { label: 'Standard', description: 'Balanced readability' },
  comfortable: { label: 'Comfortable', description: 'Extra room per row' },
}

interface DataTableDensityToggleProps {
  density: Density
  onChange: (density: Density) => void
}

export function DataTableDensityToggle({
  density,
  onChange,
}: DataTableDensityToggleProps) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className={cn(
          buttonVariants({ variant: 'outline', size: 'sm' }),
          'h-8 gap-1'
        )}
      >
        <GripHorizontal className="size-4" />
        {densityConfig[density].label}
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-44">
        {(Object.entries(densityConfig) as [Density, typeof densityConfig[ Density ]][]).map(
          ([key, config]) => (
            <DropdownMenuItem
              key={key}
              onClick={() => onChange(key)}
              className="flex-col items-start gap-0"
            >
              <div className="flex w-full items-center gap-2">
                <span className="inline-block w-4 text-center text-xs">
                  {density === key ? '✓' : ''}
                </span>
                <span className="font-medium">{config.label}</span>
              </div>
              <p className="pl-6 text-xs text-muted-foreground">
                {config.description}
              </p>
            </DropdownMenuItem>
          )
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
