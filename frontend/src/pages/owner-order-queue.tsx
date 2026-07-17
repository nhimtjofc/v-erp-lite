import { useEffect, useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useListOrdersQuery } from '@/shared/api'
import { Badge } from '@/shared/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Clock3 } from 'lucide-react'

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
  const { data: orders, isLoading } = useListOrdersQuery({ query: { status: 'PAID_ESCROW' } })

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <Card className="border-primary/30">
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Clock3 className="h-5 w-5 text-primary" /> Owner decision queue</CardTitle>
        <p className="text-sm text-muted-foreground">Paid orders awaiting your fulfillment commitment, earliest deadline first.</p>
      </CardHeader>
      <CardContent>
        {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p>
          : !orders?.length ? <p className="text-sm text-muted-foreground">No paid orders are waiting for your decision.</p>
            : (
              <div className="divide-y rounded-md border">
                {orders.map((order) => {
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
      </CardContent>
    </Card>
  )
}
