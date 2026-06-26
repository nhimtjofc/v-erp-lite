import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import { useList2Query } from '@/shared/api'
import type { Customer } from '@/shared/api'
import type { ColumnDef } from '@tanstack/react-table'
import { Button } from '@/shared/ui/button'
import { DataTable } from '@/shared/ui/data-table'
import { DataTableColumnHeader } from '@/shared/ui/data-table-column-header'
import { DataTableRowActions } from '@/shared/ui/data-table-row-actions'
import { Badge } from '@/shared/ui/badge'
import { Link } from '@tanstack/react-router'
import { Plus, Eye, Pencil, Trash2 } from 'lucide-react'
import type { RowAction } from '@/shared/ui/data-table-row-actions'

export function CustomersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: customers, isLoading } = useList2Query()
  const customerList = customers ?? []

  const columns = useMemo(
    (): ColumnDef<Customer>[] => [
      {
        accessorKey: 'firstName',
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Name" />
        ),
        cell: ({ row }) => {
          const first = row.original.firstName ?? ''
          const last = row.original.lastName ?? ''
          const name = [first, last].filter(Boolean).join(' ') || 'Unnamed'
          return (
            <div>
              <div className="text-sm font-medium">{name}</div>
              <div className="mt-px truncate text-xs text-muted-foreground">
                {row.original.email}
              </div>
            </div>
          )
        },
      },
      {
        accessorKey: 'email',
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Email" />
        ),
      },
      {
        accessorKey: 'phone',
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Phone" />
        ),
      },
      {
        accessorKey: 'active',
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Status" />
        ),
        cell: ({ row }) => (
          <Badge variant={row.original.active ? 'default' : 'secondary'}>
            {row.original.active ? 'Active' : 'Inactive'}
          </Badge>
        ),
        filterFn: (row, id, value: string[]) =>
          value.includes(String(row.getValue(id))),
      },
      {
        accessorKey: 'createdAt',
        header: ({ column }) => (
          <DataTableColumnHeader column={column} title="Created" />
        ),
        cell: ({ row }) => (
          <span className="text-muted-foreground">
            {new Date(row.original.createdAt!).toLocaleDateString()}
          </span>
        ),
      },
      {
        id: 'actions',
        enableSorting: false,
        enableHiding: false,
        cell: ({ row }) => {
          const id = row.original.id!
          const actions: RowAction[] = [
            {
              label: 'View',
              icon: Eye,
              onClick: () => navigate({ to: '/customers/$id', params: { id } }),
            },
            {
              label: 'Edit',
              icon: Pencil,
              onClick: () =>
                navigate({ to: '/customers/$id', params: { id } }),
            },
            {
              label: 'Delete',
              icon: Trash2,
              variant: 'destructive',
              separator: true,
              onClick: () => {
                // TODO: confirm dialog + delete mutation
              },
            },
          ]
          return (
            <div className="flex justify-end">
              <DataTableRowActions actions={actions} />
            </div>
          )
        },
      },
    ],
    [navigate]
  )

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">
            {t('nav:customers', 'Customers')}
          </h1>
          <p className="text-muted-foreground mt-1">
            {t('common:manageCustomerRecords', 'Manage your customer records')}
          </p>
        </div>
        <Link to="/customers/new">
          <Button>
            <Plus className="h-4 w-4" />
            {t('common:addCustomer', 'Add Customer')}
          </Button>
        </Link>
      </div>

      <DataTable
        columns={columns}
        data={customerList}
        loading={isLoading}
        defaultHiddenColumns={['email']}
        searchColumnId="email"
        searchPlaceholder={t(
          'common:searchCustomers',
          'Search customers...'
        )}
        onRowClick={(customer) =>
          navigate({
            to: '/customers/$id',
            params: { id: customer.id! },
          })
        }
        filterFields={[
          { columnId: 'firstName', label: 'Name', type: 'text' },
          { columnId: 'phone', label: 'Phone', type: 'text' },
          {
            columnId: 'active',
            label: 'Status',
            type: 'select',
            options: [
              { label: 'Active', value: 'true' },
              { label: 'Inactive', value: 'false' },
            ],
          },
        ]}
      />
    </div>
  )
}
