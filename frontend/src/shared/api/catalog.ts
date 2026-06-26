export interface Category {
  id?: string
  tenantId?: string
  name: string
  description?: string
  parent?: Pick<Category, 'id' | 'name'> | null
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface Tag {
  id?: string
  tenantId?: string
  name: string
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface ProductOption {
  name: string
  values: string[]
}

export interface ProductVariant {
  id?: string
  tenantId?: string
  sku: string
  price: number
  stockQuantity: number
  lowStockThreshold?: number
  optionValues: Record<string, string>
}

export interface Product {
  id?: string
  tenantId?: string
  category?: Pick<Category, 'id' | 'name'> | null
  sku: string
  name: string
  description?: string
  imageUrl?: string
  basePrice: number
  salePrice?: number | null
  saleStartsAt?: string | null
  saleEndsAt?: string | null
  active?: boolean
  createdAt?: string
  updatedAt?: string
  version?: number
  stockQuantity?: number
  lowStockThreshold?: number
  options?: ProductOption[]
  variants?: ProductVariant[]
  tags?: Tag[]
}

interface ProductFilters {
  search?: string
  categoryId?: string
  tagId?: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Content-Type', 'application/json')
  const token = localStorage.getItem('access_token')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${API_BASE}/api${path}`, {
    ...init,
    headers,
  })

  if (!response.ok) {
    const message = await response.text()
    throw new Error(message || `Request failed with ${response.status}`)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

function withQuery(path: string, params: Record<string, string | undefined>) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value) query.set(key, value)
  })
  const suffix = query.toString()
  return suffix ? `${path}?${suffix}` : path
}

export const catalogApi = {
  listCategories: (search?: string) => request<Category[]>(withQuery('/categories', { search })),
  createCategory: (category: Partial<Category>) => request<Category>('/categories', {
    method: 'POST',
    body: JSON.stringify(category),
  }),
  listProducts: (filters: ProductFilters = {}) => request<Product[]>(
    withQuery('/products', { search: filters.search, categoryId: filters.categoryId, tagId: filters.tagId })
  ),
  getProduct: (id: string) => request<Product>(`/products/${id}`),
  createProduct: (product: Partial<Product>) => request<Product>('/products', {
    method: 'POST',
    body: JSON.stringify(product),
  }),
  updateProduct: (id: string, product: Partial<Product>) => request<Product>(`/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(product),
  }),
  deleteProduct: (id: string) => request<void>(`/products/${id}`, {
    method: 'DELETE',
  }),
  listTags: (search?: string) => request<Tag[]>(withQuery('/tags', { search })),
  createTag: (tag: Partial<Tag>) => request<Tag>('/tags', {
    method: 'POST',
    body: JSON.stringify(tag),
  }),
  deleteTag: (id: string) => request<void>(`/tags/${id}`, {
    method: 'DELETE',
  }),
}

