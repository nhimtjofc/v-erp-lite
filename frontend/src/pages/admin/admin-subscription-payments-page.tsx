import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Check, CreditCard, X } from 'lucide-react'
import { ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY, subscriptionQueryKey, useAdminSubscriptionPaymentsQuery, useApproveSubscriptionPaymentMutation, useMeQuery, useRejectSubscriptionPaymentMutation, type SubscriptionPayment } from '@/shared/api'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'

export function AdminSubscriptionPaymentsPage() {
  const queryClient = useQueryClient()
  const { data: me, isLoading: meLoading } = useMeQuery()
  const { data: payments = [], isLoading } = useAdminSubscriptionPaymentsQuery()
  const approve = useApproveSubscriptionPaymentMutation()
  const reject = useRejectSubscriptionPaymentMutation()
  const [rejecting, setRejecting] = useState<SubscriptionPayment | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const submitted = payments.filter((payment) => payment.status === 'SUBMITTED')
  const refresh = async (payment: SubscriptionPayment) => { await queryClient.invalidateQueries({ queryKey: ADMIN_SUBSCRIPTION_PAYMENTS_QUERY_KEY }); await queryClient.invalidateQueries({ queryKey: subscriptionQueryKey(payment.tenantId) }) }
  const approvePayment = async (payment: SubscriptionPayment) => { try { setError(''); await approve.mutateAsync(payment.id); await refresh(payment) } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể duyệt thanh toán.') } }
  const rejectPayment = async () => { if (!rejecting || !reason.trim()) return; try { setError(''); await reject.mutateAsync({ id: rejecting.id, rejectionReason: reason.trim() }); await refresh(rejecting); setRejecting(null); setReason('') } catch (cause) { setError(cause instanceof Error ? cause.message : 'Không thể từ chối thanh toán.') } }
  if (meLoading) return <p className="py-8 text-center text-muted-foreground">Đang kiểm tra quyền truy cập...</p>
  if (me?.role?.name !== 'admin') return <p className="py-8 text-center text-destructive">Chỉ quản trị viên hệ thống có thể duyệt thanh toán subscription.</p>
  return <div className="space-y-6"><div className="flex gap-3"><CreditCard className="mt-1 h-7 w-7 text-primary" /><div><h1 className="text-3xl font-bold tracking-tight">Duyệt thanh toán subscription</h1><p className="mt-1 text-muted-foreground">Xác nhận giao dịch Owner trước khi kích hoạt hoặc thay đổi gói của tenant.</p></div></div><div className="grid gap-4 sm:grid-cols-3"><Card><CardContent className="pt-5"><p className="text-sm text-muted-foreground">Cần duyệt</p><p className="text-3xl font-bold">{submitted.length}</p></CardContent></Card><Card><CardContent className="pt-5"><p className="text-sm text-muted-foreground">Giá trị chờ xác nhận</p><p className="text-2xl font-bold">{formatMoneyMinor(submitted.reduce((sum, p) => sum + p.amountMinor, 0))}</p></CardContent></Card><Card><CardContent className="pt-5"><p className="text-sm text-muted-foreground">Tổng yêu cầu</p><p className="text-3xl font-bold">{payments.length}</p></CardContent></Card></div>{error && <p className="text-sm text-destructive">{error}</p>}<Card><CardHeader><CardTitle>Danh sách yêu cầu</CardTitle></CardHeader><CardContent>{isLoading ? <p className="text-muted-foreground">Đang tải...</p> : <Table><TableHeader><TableRow><TableHead>Gửi lúc</TableHead><TableHead>Tenant</TableHead><TableHead>Gói</TableHead><TableHead>Mã giao dịch</TableHead><TableHead>Trạng thái</TableHead><TableHead className="text-right">Số tiền</TableHead><TableHead /></TableRow></TableHeader><TableBody>{payments.map((payment) => <TableRow key={payment.id}><TableCell>{payment.submittedAt ? new Date(payment.submittedAt).toLocaleString('vi-VN') : '—'}</TableCell><TableCell><code className="text-xs">{payment.tenantId}</code></TableCell><TableCell>{payment.planCode} · {payment.billingPeriod === 'ANNUAL' ? 'Năm' : 'Tháng'}</TableCell><TableCell>{payment.paymentReference ?? '—'}</TableCell><TableCell><Badge variant={payment.status === 'APPROVED' ? 'default' : payment.status === 'REJECTED' ? 'destructive' : 'secondary'}>{payment.status}</Badge>{payment.rejectionReason && <p className="mt-1 text-xs text-destructive">{payment.rejectionReason}</p>}</TableCell><TableCell className="text-right font-semibold">{formatMoneyMinor(payment.amountMinor, payment.currency)}</TableCell><TableCell>{payment.status === 'SUBMITTED' && <div className="flex justify-end gap-2"><Button size="sm" onClick={() => approvePayment(payment)} disabled={approve.isPending}><Check className="mr-1 h-4 w-4" />Duyệt</Button><Button size="sm" variant="destructive" onClick={() => setRejecting(payment)}><X className="mr-1 h-4 w-4" />Từ chối</Button></div>}</TableCell></TableRow>)}</TableBody></Table>}</CardContent></Card>{rejecting && <Dialog open onOpenChange={(open) => !open && setRejecting(null)}><DialogContent><DialogHeader><DialogTitle>Từ chối thanh toán</DialogTitle></DialogHeader><div className="space-y-2"><Label htmlFor="reason">Lý do từ chối</Label><Input id="reason" value={reason} onChange={(event) => setReason(event.target.value)} /></div><DialogFooter><Button variant="outline" onClick={() => setRejecting(null)}>Hủy</Button><Button variant="destructive" disabled={!reason.trim() || reject.isPending} onClick={rejectPayment}>Từ chối</Button></DialogFooter></DialogContent></Dialog>}</div>
}
