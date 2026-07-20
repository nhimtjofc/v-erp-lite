import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useBanksQuery, useMeQuery } from '@/shared/api'
import {
  useSellerWalletQuery,
  useUpdateBankDetailsMutation,
  useWalletTransactionsQuery,
  type SellerWallet,
  type WalletTransaction,
} from '@/shared/api/wallet'
import {
  PAYOUT_STATUS_META,
  usePayoutStatusQuery,
  useRunPayoutScreeningMutation,
  useSellerPayoutsQuery,
} from '@/shared/api/payout'
import { formatMoneyMinor } from '@/shared/utils/currency'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/shared/ui/table'
import { AlertCircle, CalendarClock, Clock, Coins, Landmark, RefreshCw, ShieldCheck, TrendingUp, Wallet } from 'lucide-react'
import { VIETNAM_BANKS } from '@/shared/utils/bank'

const WALLET_ROLES = ['businessOwner']

const TX_TYPE_LABELS: Record<string, string> = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  COMMISSION: 'Commission',
  ESCROW_HOLD: 'Escrow hold',
  ESCROW_RELEASE: 'Escrow release',
  ESCROW_REFUND: 'Escrow refund',
  PAYOUT: 'Bank payout',
}

function formatDate(value?: string): string {
  return value ? new Date(value).toLocaleString() : '—'
}

function BalanceCard({
  icon: Icon,
  label,
  value,
  hint,
}: {
  icon: typeof Wallet
  label: string
  value: string
  hint: string
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <Icon className="h-4 w-4 text-primary" />
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold tracking-tight">{value}</div>
        <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
      </CardContent>
    </Card>
  )
}

function BankDetailsDialog({
  open,
  onOpenChange,
  wallet,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  wallet: SellerWallet
}) {
  const queryClient = useQueryClient()
  const updateBankDetails = useUpdateBankDetailsMutation()
  const { data: banks, isLoading: areBanksLoading, isError: isBanksError } = useBanksQuery()
  const [bankId, setBankId] = useState(wallet.bankId ?? '')
  const [accountNumber, setAccountNumber] = useState(wallet.bankAccountNumber ?? '')
  const [accountHolder, setAccountHolder] = useState(wallet.bankAccountHolder ?? '')
  const [error, setError] = useState('')

  const save = async () => {
    if (!bankId || !accountNumber.trim() || !accountHolder.trim()) {
      setError('All bank detail fields are required.')
      return
    }
    try {
      await updateBankDetails.mutateAsync({
        bankId,
        bankAccountNumber: accountNumber.trim(),
        bankAccountHolder: accountHolder.trim(),
        currency: wallet.currency,
      })
      await queryClient.invalidateQueries({ queryKey: ['seller-wallet'] })
      await queryClient.invalidateQueries({ queryKey: ['seller-payouts'] })
      setError('')
      onOpenChange(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to save bank details.')
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Withdrawal bank account</DialogTitle>
          <DialogDescription>
            Choose the receiving bank for owner payouts. The first payout after any change is always flagged for admin review.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="bank-name">Bank name</Label>
            <Select value={bankName} onValueChange={setBankName}>
              <SelectTrigger id="bank-name">
                <SelectValue placeholder="Select a bank" />
              </SelectTrigger>
              <SelectContent>
                {VIETNAM_BANKS.map((bank) => (
                  <SelectItem key={bank.code} value={bank.code}>
                    {bank.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-2">
            <Label htmlFor="bank-account">Account number</Label>
            <Input id="bank-account" value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="bank-holder">Account holder</Label>
            <Input id="bank-holder" value={accountHolder} onChange={(event) => setAccountHolder(event.target.value)} />
          </div>
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>Cancel</Button>
          <Button onClick={save} disabled={updateBankDetails.isPending}>
            {updateBankDetails.isPending ? 'Saving…' : 'Save details'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

function PayoutStatusWidget({ wallet }: { wallet: SellerWallet }) {
  const [bankOpen, setBankOpen] = useState(false)
  const [screeningMessage, setScreeningMessage] = useState('')
  const [screeningError, setScreeningError] = useState('')
  const queryClient = useQueryClient()
  const { data: payoutStatus, isLoading } = usePayoutStatusQuery(wallet.id)
  const runScreening = useRunPayoutScreeningMutation()
  const bankConfigured = Boolean(wallet.bankName && wallet.bankAccountNumber && wallet.bankAccountHolder)
  const minimumThreshold = Number(payoutStatus?.minimumThresholdMinor ?? 0)
  const availableBalance = Number(wallet.availableBalanceMinor ?? 0)
  const isEligible = Boolean(wallet.id) && bankConfigured && availableBalance >= minimumThreshold

  const handleRunScreening = async () => {
    setScreeningMessage('')
    setScreeningError('')
    try {
      const payout = await runScreening.mutateAsync(wallet.id!)
      const status = payout?.status ?? 'CREATED'
      setScreeningMessage(
        status === 'PAID'
          ? 'Payout passed screening and the mock bank transfer completed.'
          : `Payout screening completed with status ${status}${payout?.flagReason ? `: ${payout.flagReason}` : '.'}`,
      )
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['seller-wallet'] }),
        queryClient.invalidateQueries({ queryKey: ['wallet-transactions'] }),
        queryClient.invalidateQueries({ queryKey: ['seller-payouts'] }),
        queryClient.invalidateQueries({ queryKey: ['payout-status'] }),
        queryClient.invalidateQueries({ queryKey: ['admin-payouts'] }),
      ])
    } catch (cause) {
      setScreeningError(cause instanceof Error ? cause.message : 'Unable to run payout screening.')
    }
  }

  return (
    <>
      <Card>
        <CardHeader className="flex flex-col justify-between gap-3 sm:flex-row sm:items-center">
          <div>
            <CardTitle className="flex items-center gap-2">
              <CalendarClock className="h-5 w-5 text-primary" /> Automated payout status
            </CardTitle>
            <p className="mt-1 text-sm text-muted-foreground">
              Eligible balances are screened automatically; admin only sees exceptions.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setBankOpen(true)}>
              <Landmark className="mr-2 h-4 w-4" /> {bankConfigured ? 'Update bank details' : 'Add bank details'}
            </Button>
            <Button onClick={handleRunScreening} disabled={!isEligible || runScreening.isPending}>
              {runScreening.isPending ? <RefreshCw className="mr-2 h-4 w-4 animate-spin" /> : <ShieldCheck className="mr-2 h-4 w-4" />}
              Run payout check
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <div>
              <p className="text-xs text-muted-foreground">Last payout</p>
              <p className="font-semibold">{isLoading ? 'Loading…' : formatDate(payoutStatus?.lastPayoutAt)}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Next scheduled run</p>
              <p className="font-semibold">{isLoading ? 'Loading…' : formatDate(payoutStatus?.nextScheduledRunAt)}</p>
            </div>
            <div>
              <p className="text-xs text-muted-foreground">Minimum eligible balance</p>
              <p className="font-semibold">
                {formatMoneyMinor(minimumThreshold, wallet.currency ?? 'VND')}
              </p>
            </div>
          </div>
          {!bankConfigured && (
            <p className="text-sm text-muted-foreground">Add bank details before running payout screening.</p>
          )}
          {bankConfigured && availableBalance < minimumThreshold && (
            <p className="text-sm text-muted-foreground">The available balance is below the payout threshold.</p>
          )}
          {screeningMessage && (
            <div className="rounded-md border border-success/30 bg-success/10 p-3 text-sm text-success">{screeningMessage}</div>
          )}
          {screeningError && (
            <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" /> {screeningError}
            </div>
          )}
        </CardContent>
      </Card>
      <BankDetailsDialog open={bankOpen} onOpenChange={setBankOpen} wallet={wallet} />
    </>
  )
}

function PayoutHistory({ wallet }: { wallet: SellerWallet }) {
  const { data: payouts, isLoading } = useSellerPayoutsQuery(wallet.id)
  return (
    <Card>
      <CardHeader><CardTitle>Payout history</CardTitle></CardHeader>
      <CardContent>
        {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p> : (
          <Table>
            <TableHeader><TableRow><TableHead>Date</TableHead><TableHead>Status</TableHead><TableHead className="text-right">Amount</TableHead></TableRow></TableHeader>
            <TableBody>
              {(payouts ?? []).map((payout) => {
                const meta = PAYOUT_STATUS_META[payout.status ?? ''] ?? { label: payout.status ?? 'Unknown', variant: 'secondary' as const }
                return (
                  <TableRow key={payout.id}>
                    <TableCell>{formatDate(payout.paidAt ?? payout.createdAt)}</TableCell>
                    <TableCell><Badge variant={meta.variant}>{meta.label}</Badge></TableCell>
                    <TableCell className="text-right font-semibold">{formatMoneyMinor(Number(payout.amountMinor ?? 0), payout.currency ?? wallet.currency ?? 'VND')}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}

function TransactionHistory({ transactions, currency }: { transactions: WalletTransaction[]; currency: string }) {
  return (
    <Card>
      <CardHeader><CardTitle>Transaction history</CardTitle></CardHeader>
      <CardContent>
        <Table>
          <TableHeader><TableRow><TableHead>Date</TableHead><TableHead>Type</TableHead><TableHead>Description</TableHead><TableHead className="text-right">Amount</TableHead></TableRow></TableHeader>
          <TableBody>
            {transactions.map((transaction) => (
              <TableRow key={transaction.id}>
                <TableCell>{formatDate(transaction.createdAt)}</TableCell>
                <TableCell><Badge variant="secondary">{TX_TYPE_LABELS[transaction.transactionType ?? ''] ?? transaction.transactionType}</Badge></TableCell>
                <TableCell>{transaction.description ?? '—'}</TableCell>
                <TableCell className={`text-right font-semibold ${transaction.type === 'CREDIT' ? 'text-success' : 'text-destructive'}`}>
                  {transaction.type === 'CREDIT' ? '+' : '-'}{formatMoneyMinor(Number(transaction.amountMinor ?? 0), currency)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  )
}

export function SellerWalletPage() {
  const { data: me, isLoading: isMeLoading } = useMeQuery()

  if (isMeLoading) return <div className="py-8 text-center text-muted-foreground">Loading access control…</div>
  if (!me?.role?.name || !WALLET_ROLES.includes(me.role.name)) {
    return <div className="py-8 text-center text-destructive">Only business owners can access the seller wallet.</div>
  }

  return <SellerWalletContent />
}

function SellerWalletContent() {
  const { t } = useTranslation()
  const { data: wallet, isLoading, isError, error } = useSellerWalletQuery()
  const { data: transactions } = useWalletTransactionsQuery(wallet?.id)

  if (isLoading) return <div className="py-8 text-center text-muted-foreground">Loading wallet…</div>
  if (isError || !wallet) return <div className="py-8 text-center text-destructive">{error instanceof Error ? error.message : 'Wallet unavailable.'}</div>

  const currency = wallet.currency ?? 'VND'
  const available = Number(wallet.availableBalanceMinor ?? 0)
  const pending = Number(wallet.pendingBalanceMinor ?? 0)

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <Wallet className="h-7 w-7 text-primary" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('nav:wallet', 'Wallet')}</h1>
          <p className="mt-1 text-muted-foreground">Escrow and withdrawable funds remain visibly separate.</p>
        </div>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <BalanceCard icon={TrendingUp} label="Available balance" value={formatMoneyMinor(available, currency)} hint="Eligible for the next automated payout run" />
        <BalanceCard icon={Clock} label="Pending balance" value={formatMoneyMinor(pending, currency)} hint="Held in escrow until delivery/completion" />
        <BalanceCard icon={Coins} label="Total balance" value={formatMoneyMinor(available + pending, currency)} hint="Available plus pending" />
      </div>
      <PayoutStatusWidget wallet={wallet} />
      <TransactionHistory transactions={transactions ?? []} currency={currency} />
      <PayoutHistory wallet={wallet} />
    </div>
  )
}
