import * as React from 'react'
import { Avatar } from '@base-ui/react/avatar'
import { cn } from '@/shared/lib/cn'

const AvatarRoot = React.forwardRef<
  React.ComponentRef<typeof Avatar.Root>,
  React.ComponentPropsWithoutRef<typeof Avatar.Root>
>(({ className, ...props }, ref) => (
  <Avatar.Root
    ref={ref}
    className={cn('relative flex h-10 w-10 shrink-0 overflow-hidden rounded-full', className)}
    {...props}
  />
))
AvatarRoot.displayName = 'AvatarRoot'

const AvatarImage = React.forwardRef<
  React.ComponentRef<typeof Avatar.Image>,
  React.ComponentPropsWithoutRef<typeof Avatar.Image>
>(({ className, ...props }, ref) => (
  <Avatar.Image
    ref={ref}
    className={cn('aspect-square h-full w-full', className)}
    {...props}
  />
))
AvatarImage.displayName = 'AvatarImage'

const AvatarFallback = React.forwardRef<
  React.ComponentRef<typeof Avatar.Fallback>,
  React.ComponentPropsWithoutRef<typeof Avatar.Fallback>
>(({ className, ...props }, ref) => (
  <Avatar.Fallback
    ref={ref}
    className={cn('flex h-full w-full items-center justify-center rounded-full bg-muted', className)}
    {...props}
  />
))
AvatarFallback.displayName = 'AvatarFallback'

export { AvatarRoot as Avatar, AvatarImage, AvatarFallback }
