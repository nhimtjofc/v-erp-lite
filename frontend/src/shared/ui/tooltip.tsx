import * as React from 'react'
import { Tooltip } from '@base-ui/react/tooltip'
import { cn } from '@/shared/lib/cn'

export interface SimpleTooltipProps {
    children: React.ReactElement
    content: React.ReactNode
    side?: 'top' | 'right' | 'bottom' | 'left'
    sideOffset?: number
    className?: string
}

export function SimpleTooltip({
    children,
    content,
    side = 'right',
    sideOffset = 8,
    className,
}: SimpleTooltipProps) {
    return (
        <Tooltip.Provider delay={100}>
            <Tooltip.Root>
                <Tooltip.Trigger render={children} />
                <Tooltip.Portal>
                    <Tooltip.Positioner side={side} sideOffset={sideOffset}>
                        <Tooltip.Popup
                            className={cn(
                                'z-50 rounded-md border bg-popover px-3 py-1.5 text-sm text-popover-foreground shadow-md',
                                'transition-all duration-150 ease-out',
                                'data-[starting-style]:opacity-0 data-[starting-style]:scale-95',
                                'data-[ending-style]:opacity-0 data-[ending-style]:scale-95',
                                className
                            )}
                        >
                            {content}
                        </Tooltip.Popup>
                    </Tooltip.Positioner>
                </Tooltip.Portal>
            </Tooltip.Root>
        </Tooltip.Provider>
    )
}
