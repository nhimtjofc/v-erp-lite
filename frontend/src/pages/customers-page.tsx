import { useMemo, useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useListCustomersQuery, useMeQuery } from '@/shared/api'
import type { Customer } from '@/shared/api'
import type { ColumnDef } from '@tanstack/react-table'
import { Button } from '@/shared/ui/button'
import { DataTable } from '@/shared/ui/data-table'
import { DataTableColumnHeader } from '@/shared/ui/data-table-column-header'
import { DataTableRowActions } from '@/shared/ui/data-table-row-actions'
import { Badge } from '@/shared/ui/badge'
import { Link, useNavigate } from '@tanstack/react-router'
import { Plus, Eye, Pencil, Trash2 } from 'lucide-react'
import type { RowAction } from '@/shared/ui/data-table-row-actions'

export function CustomersPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: me, isLoading: isMeLoading } = useMeQuery()
  const roleName = me?.role?.name
  const isCustomer = roleName === 'customer'

  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const { data: customers, isLoading } = useListCustomersQuery(
    debouncedSearch ? { query: { search: debouncedSearch } } : undefined
  )
  const customerList = customers ?? []

  const columns = useMemo<ColumnDef<Customer>[]>(() => [
    {
      accessorKey: 'firstName',
      header: ({ column }) => <DataTableColumnHeader column={column} title={t('common:firstName', 'First Name')} />,
      cell: ({ row }) => <span>{row.getValue('firstName') || '-'}</span>,
    },
    {
      accessorKey: 'lastName',
      header: ({ column }) => <DataTableColumnHeader column={column} title={t('common:lastName', 'Last Name')} />,
      cell: ({ row }) => <span>{row.getValue('lastName') || '-'}</span>,
    },
    {
      accessorKey: 'email',
      header: ({ column }) => <DataTableColumnHeader column={column} title={t('common:email', 'Email')} />,
      cell: ({ row }) => <span>{row.getValue('email') || '-'}</span>,
    },
    {
      accessorKey: 'phone',
      header: ({ column }) => <DataTableColumnHeader column={column} title={t('common:phone', 'Phone')} />,
      cell: ({ row }) => <span>{row.getValue('phone') || '-'}</span>,
    },
    {
      accessorKey: 'active',
      header: ({ column }) => <DataTableColumnHeader column={column} title={t('common:status', 'Status')} />,
      cell: ({ row }) => (
        <Badge variant={row.getValue('active') ? 'default' : 'secondary'}>
          {row.getValue('active') ? t('common:active', 'Active') : t('common:inactive', 'Inactive')}
        </Badge>
      ),
    },
    {
      id: 'actions',
      cell: ({ row }) => (
        <DataTableRowActions
          actions={[
            {
              label: t('common:view', 'View'),
              icon: Eye,
              onClick: () => navigate({ to: '/customers/$id', params: { id: row.original.id! } }),
            },
          ]}
        />
      ),
    },
  ], [t, navigate])

  if (isMeLoading) {
    return <div className="text-center py-8 text-muted-foreground">Loading access control...</div>
  }

  if (!roleName || isCustomer) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[300px] border border-dashed rounded-lg p-8 text-center space-y-4">
        <h2 className="text-xl font-bold text-destructive">Access Denied</h2>
        <p className="text-muted-foreground max-w-md">
          Only staff members are allowed to view the customer list.
        </p>
      </div>
    )
  }

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
