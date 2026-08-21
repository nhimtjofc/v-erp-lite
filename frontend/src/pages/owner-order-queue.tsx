import { useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useListOrdersQuery } from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Clock3, CreditCard } from 'lucide-react'

function remaining(deadline: string | undefined, now: number) {
  if (!deadline) return { label: 'Deadline unavailable', urgent: true }
  const milliseconds = new Date(deadline).getTime() - now
  if (milliseconds <= 0) return { label: 'Overdue — auto-decline pending', urgent: true }
  const totalMinutes = Math.floor(milliseconds / 60_000)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return { label: `${hours}h ${minutes}m remaining`, urgent: hours < 4 }
}

export function OwnerOrderQueue() {
  const [now, setNow] = useState(Date.now())
  const { data: paidOrders, isLoading: paidLoading } = useListOrdersQuery({ query: { status: 'PAID_ESCROW' } })
  const { data: pendingOrders, isLoading: pendingLoading } = useListOrdersQuery({ query: { status: 'PENDING_PAYMENT' } })

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  const loading = paidLoading || pendingLoading

  return (
    <Card className="border-primary/30">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Clock3 className="h-5 w-5 text-primary" /> New order requests</CardTitle>
        <p className="text-sm text-muted-foreground">Orders awaiting your attention — paid orders need a decision, new orders await payment.</p>
      </CardHeader>
      <CardContent className="space-y-4">
        <div>
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <CreditCard className="h-3.5 w-3.5" />
            Awaiting payment
          </h4>
          {loading
            ? <p className="text-sm text-muted-foreground">Loading…</p>
            : !pendingOrders?.length
              ? <p className="text-sm text-muted-foreground pl-5">No new orders awaiting payment.</p>
              : (
                <div className="divide-y rounded-md border">
                  {pendingOrders.map((order) => (
                    <Link key={order.id} to="/orders/$id" params={{ id: order.id! }} className="flex items-center justify-between gap-4 p-3 transition-colors hover:bg-muted/50">
                      <div><p className="font-semibold">{order.orderNumber}</p><p className="text-sm text-muted-foreground">{order.shippingName}</p></div>
                      <Badge variant="secondary">Awaiting payment</Badge>
                    </Link>
                  ))}
                </div>
              )}
        </div>

        <div>
          <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <Clock3 className="h-3.5 w-3.5" />
            Awaiting decision
          </h4>
          {loading
            ? <p className="text-sm text-muted-foreground">Loading…</p>
            : !paidOrders?.length
              ? <p className="text-sm text-muted-foreground pl-5">No paid orders are waiting for your decision.</p>
              : (
                <div className="divide-y rounded-md border">
                  {paidOrders.map((order) => {
                    const countdown = remaining(order.ownerDecisionDeadline, now)
                    return (
                      <Link key={order.id} to="/orders/$id" params={{ id: order.id! }} className="flex items-center justify-between gap-4 p-3 transition-colors hover:bg-muted/50">
                        <div><p className="font-semibold">{order.orderNumber}</p><p className="text-sm text-muted-foreground">{order.shippingName}</p></div>
                        <Badge variant={countdown.urgent ? 'destructive' : 'secondary'}>{countdown.label}</Badge>
                      </Link>
                    )
                  })}
                </div>
              )}
        </div>
      </CardContent>
    </Card>
  )
}
