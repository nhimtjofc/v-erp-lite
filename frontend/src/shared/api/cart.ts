import type { Product, ProductVariant } from './catalog'

export interface Customer {
  id?: string
  tenantId?: string
  firstName?: string
  lastName?: string
  email?: string
  phone?: string
  address?: string
  active?: boolean
}

export interface CartItem {
  id?: string
  product: Product
  variant?: ProductVariant | null
  quantity: number
  unitPriceMinor: number
}

export interface Cart {
  id?: string
  tenantId?: string
  customer: Customer
  status: string
  notes?: string
  items: CartItem[]
  createdAt?: string
  updatedAt?: string
  version?: number
}

export interface OrderItem {
  id?: string
  product: Product
  variant?: ProductVariant | null
  quantity: number
  unitPriceMinor: number
  subtotal: number
}

export interface Order {
  id?: string
  tenantId?: string
  customer: Customer
  status: string
  totalAmount: number
  discountPercent: number
  discountAmount: number
  vatPercent: number
  vatAmount: number
  shippingFeeMinor: number
  finalAmountMinor: number
  notes?: string
  items: OrderItem[]
  createdAt?: string
  updatedAt?: string
  version?: number
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

export const cartApi = {
  listCustomers: (search?: string) => request<Customer[]>(withQuery('/customers', { search })),
  getCustomer: (id: string) => request<Customer>(`/customers/${id}`),

  listCarts: (customerId?: string, status?: string) => request<Cart[]>(
    withQuery('/carts', { customerId, status })
  ),
  getOrCreateActiveCart: (customerId: string) => request<Cart>(`/carts/active/${customerId}`),
  getCart: (id: string) => request<Cart>(`/carts/${id}`),
  addCartItem: (cartId: string, productId: string, quantity?: number, variantId?: string) =>
    request<Cart>(`/carts/${cartId}/items`, {
      method: 'POST',
      body: JSON.stringify({ productId, variantId, quantity: quantity ?? 1 }),
    }),
  updateCartItem: (cartId: string, itemId: string, quantity: number) =>
    request<Cart>(`/carts/${cartId}/items/${itemId}`, {
      method: 'PUT',
      body: JSON.stringify({ quantity }),
    }),
  removeCartItem: (cartId: string, itemId: string) =>
    request<Cart>(`/carts/${cartId}/items/${itemId}`, {
      method: 'DELETE',
    }),
  checkoutCart: (cartId: string, data?: { discountPercent?: number; vatPercent?: number; shippingFeeMinor?: number; notes?: string }) =>
    request<Order>(`/carts/${cartId}/checkout`, {
      method: 'POST',
      body: JSON.stringify(data ?? {}),
    }),
  deleteCart: (id: string) => request<void>(`/carts/${id}`, { method: 'DELETE' }),

  listOrders: (search?: string, status?: string) => request<Order[]>(
    withQuery('/orders', { search, status })
  ),
  getOrder: (id: string) => request<Order>(`/orders/${id}`),
  updateOrderStatus: (id: string, status: string) =>
    request<Order>(`/orders/${id}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
  updateOrder: (id: string, data: { notes?: string; discountPercent?: number; vatPercent?: number; shippingFeeMinor?: number }) =>
    request<Order>(`/orders/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
}
