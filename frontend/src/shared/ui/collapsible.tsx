import * as React from 'react'
import { Collapsible } from '@base-ui/react/collapsible'
import { cn } from '@/shared/lib/cn'

export interface CollapsibleSectionProps {
    children: React.ReactNode
    triggerContent: React.ReactNode
    triggerClassName?: string
    open?: boolean
    onOpenChange?: (open: boolean) => void
    className?: string
}

export const CollapsibleSection = React.forwardRef<
    HTMLDivElement,
    CollapsibleSectionProps
>(({ children, triggerContent, triggerClassName, open, onOpenChange, className }, ref) => {
    return (
        <Collapsible.Root open={open} onOpenChange={onOpenChange} className={className}>
            <Collapsible.Trigger className={triggerClassName}>
                {triggerContent}
            </Collapsible.Trigger>
            <Collapsible.Panel
                ref={ref}
                className={cn(
                    'h-0 overflow-hidden transition-[height] duration-200 ease-out',
                    'data-[open]:h-[var(--collapsible-panel-height)]'
                )}
            >
                {children}
            </Collapsible.Panel>
        </Collapsible.Root>
    )
})
CollapsibleSection.displayName = 'CollapsibleSection'
