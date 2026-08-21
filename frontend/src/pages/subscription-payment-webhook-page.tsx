import { useEffect, useState } from 'react'
import { Link, useParams, useRouter } from '@tanstack/react-router'
import QRCode from 'qrcode'
import { CheckCircle2, ChevronLeft, CircleX, LoaderCircle, QrCode, Send } from 'lucide-react'
import { MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY, useMockSubscriptionPaymentWebhookMutation, useMySubscriptionPaymentsQuery } from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { useQueryClient } from '@tanstack/react-query'

const T = {
  success: 'G\u1eedi webhook th\u00e0nh c\u00f4ng',
  failed: 'G\u1eedi webhook th\u1ea5t b\u1ea1i',
  title: 'Tr\u00ecnh m\u00f4 ph\u1ecfng webhook VietQR',
  back: 'Ch\u1ecdn ph\u01b0\u01a1ng th\u1ee9c kh\u00e1c',
  loading: '\u0110ang t\u1ea3i phi\u00ean thanh to\u00e1n...',
  loadError: 'Kh\u00f4ng th\u1ec3 t\u1ea3i phi\u00ean thanh to\u00e1n.',
  missing: 'Kh\u00f4ng t\u00ecm th\u1ea5y phi\u00ean thanh to\u00e1n.',
}

export function SubscriptionPaymentWebhookPage() {
  const { id } = useParams({ from: '/_authenticated/subscription/checkout/$id/webhook' })
  const router = useRouter()
  const queryClient = useQueryClient()
  const { data: payments = [], isLoading, error: paymentsError } = useMySubscriptionPaymentsQuery()
  const mockWebhook = useMockSubscriptionPaymentWebhookMutation()
  const [qr, setQr] = useState('')
  const [error, setError] = useState('')
  const payment = payments.find((item) => item.id === id)
  useEffect(() => { if (payment) void QRCode.toDataURL(`v-erp://subscription/vietqr?payment=${payment.id}&amount=${payment.amountMinor}`, { width: 260, margin: 1 }).then(setQr) }, [payment])
  const send = async (status: 'SUCCESS' | 'FAILED') => { if (!payment) return; setError(''); try { await mockWebhook.mutateAsync({ id: payment.id, gateway: 'VIETQR', status }); await queryClient.invalidateQueries({ queryKey: MY_SUBSCRIPTION_PAYMENTS_QUERY_KEY }); router.navigate({ to: '/subscription' }) } catch (cause) { setError(cause instanceof Error ? cause.message : 'Kh\u00f4ng th\u1ec3 g\u1eedi webhook.') } }
  if (isLoading) return <p className="py-8 text-center text-muted-foreground">{T.loading}</p>
  if (paymentsError) return <p className="py-8 text-center text-destructive">{T.loadError}</p>
  if (!payment || payment.status !== 'PENDING') return <p className="py-8 text-center text-destructive">{T.missing}</p>
  return <div className="mx-auto max-w-4xl space-y-6 pb-12"><Link to="/subscription/checkout/$id" params={{ id }} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"><ChevronLeft className="h-4 w-4" />{T.back}</Link><div><h1 className="text-3xl font-bold">{T.title}</h1><p className="mt-1 text-muted-foreground">Webhook th\u00e0nh c\u00f4ng s\u1ebd chuy\u1ec3n y\u00eau c\u1ea7u sang Admin duy\u1ec7t.</p></div><div className="grid gap-6 md:grid-cols-2"><Card><CardHeader className="text-center"><QrCode className="mx-auto h-7 w-7 text-primary" /><CardTitle className="mt-2">Qu\u00e9t m\u00e3 VietQR</CardTitle></CardHeader><CardContent className="text-center">{qr ? <img src={qr} alt="VietQR" className="mx-auto h-56 w-56 rounded-xl border bg-white p-3" /> : <LoaderCircle className="mx-auto h-10 w-10 animate-spin" />}<p className="mt-4 text-2xl font-bold text-primary">{formatMoneyMinor(payment.amountMinor, payment.currency)}</p></CardContent></Card><Card className="border-primary/20 bg-slate-950 text-slate-100"><CardHeader><CardTitle className="font-mono text-base text-emerald-400">MOCK BANK WEBHOOK SIMULATOR</CardTitle></CardHeader><CardContent className="space-y-4 font-mono text-xs"><div className="rounded-lg border border-slate-700 p-3 text-slate-300"><p>EVENT: payment.succeeded</p><p>GATEWAY: VIETQR</p><p>PAYMENT: SUB-{payment.id.slice(0, 8).toUpperCase()}</p><p>AMOUNT: {formatMoneyMinor(payment.amountMinor, payment.currency)}</p></div>{error && <p className="font-sans text-sm text-red-300">{error}</p>}<div className="grid gap-2"><Button onClick={() => send('SUCCESS')} disabled={mockWebhook.isPending}><Send className="mr-2 h-4 w-4" />{T.success}</Button><Button variant="destructive" onClick={() => send('FAILED')} disabled={mockWebhook.isPending}><CircleX className="mr-2 h-4 w-4" />{T.failed}</Button></div></CardContent></Card></div></div>
}
