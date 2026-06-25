import { createFileRoute } from '@tanstack/react-router'
import { ProductFormPage } from '@/pages/product-form-page'

export const Route = createFileRoute('/_authenticated/products/$id')({
  component: () => {
    const { id } = Route.useParams()
    return <ProductFormPage productId={id} />
  },
})
