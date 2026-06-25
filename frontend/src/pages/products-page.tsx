import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { PackagePlus, Plus, Search, Tags } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { catalogApi } from '@/shared/api/catalog'
import type { Category } from '@/shared/api/catalog'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

function formatMoney(value?: number | null, language?: string) {
  if (value === null || value === undefined) return '-'
  return new Intl.NumberFormat(language, { style: 'currency', currency: 'USD' }).format(value)
}

export function ProductsPage() {
  const { t, i18n } = useTranslation(['catalog', 'common'])
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [newCategory, setNewCategory] = useState('')

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(search), 300)
    return () => clearTimeout(timer)
  }, [search])

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => catalogApi.listCategories(),
  })

  const productsQuery = useQuery({
    queryKey: ['products', debouncedSearch, categoryId],
    queryFn: () => catalogApi.listProducts({
      search: debouncedSearch || undefined,
      categoryId: categoryId || undefined,
    }),
  })

  const createCategory = useMutation({
    mutationFn: (name: string) => catalogApi.createCategory({ name, active: true }),
    onSuccess: () => {
      setNewCategory('')
      queryClient.invalidateQueries({ queryKey: ['categories'] })
    },
  })

  const categories = categoriesQuery.data ?? []
  const products = productsQuery.data ?? []

  const handleCreateCategory = (event: React.FormEvent) => {
    event.preventDefault()
    const name = newCategory.trim()
    if (!name) return
    createCategory.mutate(name)
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('title')}</h1>
          <p className="mt-1 text-muted-foreground">{t('description')}</p>
        </div>
        <Link to="/products/new">
          <Button>
            <PackagePlus className="h-4 w-4" />
            {t('newProduct')}
          </Button>
        </Link>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <Card>
          <CardHeader>
            <div className="flex flex-col gap-3 md:flex-row md:items-center">
              <div className="relative flex-1">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={t('searchPlaceholder')}
                  className="pl-8"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>
              <select
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm md:w-56"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
              >
                <option value="">{t('allCategories')}</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </div>
          </CardHeader>
          <CardContent>
            {productsQuery.isLoading ? (
              <p className="py-8 text-center text-sm text-muted-foreground">{t('common:loading')}</p>
            ) : products.length === 0 ? (
              <p className="py-8 text-center text-sm text-muted-foreground">{t('noProducts')}</p>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('product')}</TableHead>
                    <TableHead>{t('sku')}</TableHead>
                    <TableHead>{t('category')}</TableHead>
                    <TableHead>{t('basePrice')}</TableHead>
                    <TableHead>{t('salePrice')}</TableHead>
                    <TableHead>{t('status')}</TableHead>
                    <TableHead></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {products.map((product) => (
                    <TableRow key={product.id}>
                      <TableCell>
                        <div className="flex items-center gap-3">
                          {product.imageUrl ? (
                            <img
                              src={product.imageUrl}
                              alt=""
                              className="h-10 w-10 rounded-md border object-cover"
                            />
                          ) : (
                            <div className="flex h-10 w-10 items-center justify-center rounded-md border bg-muted">
                              <Tags className="h-4 w-4 text-muted-foreground" />
                            </div>
                          )}
                          <div>
                            <p className="font-medium">{product.name}</p>
                            {product.description && (
                              <p className="line-clamp-1 max-w-xs text-xs text-muted-foreground">
                                {product.description}
                              </p>
                            )}
                          </div>
                        </div>
                      </TableCell>
                      <TableCell>{product.sku}</TableCell>
                      <TableCell>{product.category?.name || '-'}</TableCell>
                      <TableCell>{formatMoney(product.basePrice, i18n.language)}</TableCell>
                      <TableCell>{formatMoney(product.salePrice, i18n.language)}</TableCell>
                      <TableCell>
                        <Badge variant={product.active ? 'default' : 'secondary'}>
                          {product.active ? t('active') : t('inactive')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Link to="/products/$id" params={{ id: product.id! }}>
                          <Button variant="ghost" size="sm">{t('common:edit')}</Button>
                        </Link>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('categories')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <form className="flex gap-2" onSubmit={handleCreateCategory}>
              <div className="flex-1 space-y-1">
                <Label htmlFor="new-category" className="sr-only">{t('categoryName')}</Label>
                <Input
                  id="new-category"
                  placeholder={t('newCategory')}
                  value={newCategory}
                  onChange={(event) => setNewCategory(event.target.value)}
                />
              </div>
              <Button type="submit" size="icon" disabled={createCategory.isPending}>
                <Plus className="h-4 w-4" />
              </Button>
            </form>

            <div className="space-y-2">
              {categoriesQuery.isLoading ? (
                <p className="text-sm text-muted-foreground">{t('common:loading')}</p>
              ) : categories.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t('noCategories')}</p>
              ) : (
                categories.map((category: Category) => (
                  <div key={category.id} className="flex items-center justify-between rounded-md border px-3 py-2">
                    <span className="text-sm font-medium">{category.name}</span>
                    <Badge variant={category.active ? 'default' : 'secondary'}>
                      {category.active ? t('active') : t('inactive')}
                    </Badge>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
