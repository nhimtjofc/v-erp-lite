import { useState, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useList3Query } from '@/shared/api'
import type { Customer } from '@/shared/api'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { Badge } from '@/shared/ui/badge'
import { Input } from '@/shared/ui/input'
import { Link } from '@tanstack/react-router'
import { Plus, Search } from 'lucide-react'

export function CustomersPage() {
  const { t } = useTranslation()
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const { data: customers, isLoading } = useList3Query(
    debouncedSearch ? { query: { search: debouncedSearch } } : undefined
  )
  const customerList = customers ?? []

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:customers', 'Customers')}</h1>
          <p className="text-muted-foreground mt-1">{t('common:manageCustomerRecords', 'Manage your customer records')}</p>
        </div>
        <Link to="/customers/new">
          <Button>
            <Plus className="h-4 w-4" />
            {t('common:addCustomer', 'Add Customer')}
          </Button>
        </Link>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center gap-2">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder={t('common:searchCustomers', 'Search customers...')}
                className="pl-8"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {isLoading ? (
            <p className="text-sm text-muted-foreground py-8 text-center">{t('common:loading', 'Loading...')}</p>
          ) : customerList.length === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">{t('common:noCustomers', 'No customers found.')}</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common:name', 'Name')}</TableHead>
                  <TableHead>{t('common:email', 'Email')}</TableHead>
                  <TableHead>{t('common:phone', 'Phone')}</TableHead>
                  <TableHead>{t('common:status', 'Status')}</TableHead>
                  <TableHead>{t('common:created', 'Created')}</TableHead>
                  <TableHead></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {customerList.map((customer) => (
                        <TableRow key={customer.id!}>
                    <TableCell className="font-medium">
                      {[customer.firstName, customer.lastName].filter(Boolean).join(' ') || t('common:unnamed', 'Unnamed')}
                    </TableCell>
                    <TableCell>{customer.email || '-'}</TableCell>
                    <TableCell>{customer.phone || '-'}</TableCell>
                    <TableCell>
                      <Badge variant={customer.active ? 'default' : 'secondary'}>
                        {customer.active ? t('common:active', 'Active') : t('common:inactive', 'Inactive')}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(customer.createdAt!).toLocaleDateString()}
                    </TableCell>
                    <TableCell>
                        <Link to="/customers/$id" params={{ id: customer.id! }}>
                        <Button variant="ghost" size="sm">{t('common:view', 'View')}</Button>
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
