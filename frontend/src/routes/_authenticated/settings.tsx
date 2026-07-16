import { createFileRoute, Outlet } from '@tanstack/react-router'
import { useMeQuery } from '@/shared/api'

export const Route = createFileRoute('/_authenticated/settings')({
  component: SettingsLayout,
})

function SettingsLayout() {
  const { data: me, isLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isStaff = roleName === 'admin'
    || roleName === 'operator'
    || roleName === 'businessOwner'

  if (isLoading) {
    return <div className="text-center py-8 text-muted-foreground">Loading access control...</div>
  }

  if (!isStaff) {
    return (
      <div className="mx-auto max-w-3xl flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4 mt-8">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only staff members are allowed to view settings.
        </p>
      </div>
    )
  }

  return <Outlet />
}
