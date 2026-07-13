import { createFileRoute, Navigate } from '@tanstack/react-router'
import { ProductFormPage } from '@/pages/product-form-page'
import { useMeQuery } from '@/shared/api/gen/@tanstack/react-query.gen'

export const Route = createFileRoute('/_authenticated/products/$id')({
  component: () => {
    const { data: me } = useMeQuery()
    const role = me?.role?.name
    const { id } = Route.useParams()

    if (!role) return null

    if (role !== 'admin' && role !== 'businessOwner' && role !== 'operator') {
      return <Navigate to="/products" replace />
    }

    return <ProductFormPage productId={id} />
  },
})
