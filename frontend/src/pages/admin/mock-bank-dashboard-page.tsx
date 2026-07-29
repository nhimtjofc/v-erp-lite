import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Building2, Users, Store, ArrowRightLeft, ShieldCheck, Activity, FastForward } from 'lucide-react';
import { formatMoneyMinor } from '@/shared/utils/currency';
import { client } from '@/shared/api/gen/client.gen';

// Temporary types since SDK generation failed due to Docker issue
interface WalletStatsResponse {
  customerBalance: number;
  escrowBalance: number;
  sellerBalance: number;
}

interface WalletTransactionResponse {
  id: string;
  walletId: string;
  amountMinor: number;
  type: string;
  transactionType: string;
  referenceType: string;
  referenceId: string;
  sequenceNo: number;
  description: string;
  createdAt: string;
}

export function MockBankDashboardPage() {
  const queryClient = useQueryClient();
  const [ffMessage, setFfMessage] = useState('');

  // Fast-forward 7 days mutation
  const fastForwardMutation = useMutation({
    mutationFn: async () => {
      const token = localStorage.getItem('access_token');
      const baseUrl = import.meta.env.VITE_API_BASE_URL || '';
      const res = await fetch(`${baseUrl}/api/wallets/escrow/force-release`, {
        method: 'POST',
        headers: {
          'Authorization': token ? `Bearer ${token}` : '',
          'Content-Type': 'application/json',
        },
      });
      if (!res.ok) {
        const errBody = await res.text().catch(() => '');
        throw new Error(errBody || `Request failed (${res.status})`);
      }
      return res.json() as unknown as { releasedOrders: number; message: string };
    },
    onSuccess: (data) => {
      setFfMessage(data.message);
      queryClient.invalidateQueries({ queryKey: ['mock-bank-stats'] });
      queryClient.invalidateQueries({ queryKey: ['mock-bank-transactions'] });
      setTimeout(() => setFfMessage(''), 5000);
    },
    onError: (err) => {
      setFfMessage(err instanceof Error ? err.message : 'Fast-forward failed');
      setTimeout(() => setFfMessage(''), 5000);
    },
  });
  // Query for stats
  const { data: stats, isLoading: statsLoading } = useQuery<WalletStatsResponse>({
    queryKey: ['mock-bank-stats'],
    queryFn: async () => {
      const res = await client.get<WalletStatsResponse>({ url: '/api/wallets/stats' });
      if (res.error) throw res.error;
      return res.data as WalletStatsResponse;
    },
    refetchInterval: 3000,
  });

  // Query for transactions
  const { data: transactions, isLoading: txLoading } = useQuery<WalletTransactionResponse[]>({
    queryKey: ['mock-bank-transactions'],
    queryFn: async () => {
      const res = await client.get<WalletTransactionResponse[]>({ url: '/api/wallets/transactions/all?limit=50' });
      if (res.error) throw res.error;
      return res.data as WalletTransactionResponse[];
    },
    refetchInterval: 3000,
  });

  return (
    <div className="container mx-auto py-8 px-4 space-y-8 animate-in fade-in duration-500">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight flex items-center gap-2">
            <Building2 className="h-8 w-8 text-primary" />
            Mock Bank & Escrow Dashboard
          </h1>
          <p className="text-muted-foreground mt-1">
            Real-time sandbox environment for monitoring the flow of funds and escrow locks.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            onClick={() => fastForwardMutation.mutate()}
            disabled={fastForwardMutation.isPending}
            className="bg-amber-600 hover:bg-amber-700 text-white font-semibold"
          >
            <FastForward className={`h-4 w-4 mr-1.5 ${fastForwardMutation.isPending ? 'animate-spin' : ''}`} />
            {fastForwardMutation.isPending ? 'Releasing...' : 'Fast-forward 7 days'}
          </Button>
          <Badge variant="outline" className="px-3 py-1 text-sm bg-success/10 text-success border-success/20">
            <span className="relative flex h-2 w-2 mr-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-success opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-success"></span>
            </span>
            Live (Auto-refresh 3s)
          </Badge>
        </div>
      </div>
      {ffMessage && (
        <div className="rounded-md border border-amber-500/30 bg-amber-500/10 px-4 py-2 text-sm font-medium text-amber-700 dark:text-amber-400">
          {ffMessage}
        </div>
      )}

      {/* Overview Cards */}
      <div className="grid gap-6 md:grid-cols-3">
        {/* Customer Wallet */}
        <Card className="border-l-4 border-l-blue-500 shadow-sm relative overflow-hidden group">
          <div className="absolute right-0 top-0 opacity-5 group-hover:opacity-10 transition-opacity p-4">
            <Users className="w-24 h-24" />
          </div>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center">
              <Users className="w-4 h-4 mr-2" />
              Tổng Ví Khách Hàng (Customer)
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-blue-600 dark:text-blue-400">
              {statsLoading ? '...' : formatMoneyMinor(stats?.customerBalance || 0, 'VND')}
            </div>
            <p className="text-xs text-muted-foreground mt-2">
              Tiền khách đã nạp vào ví V-ERP
            </p>
          </CardContent>
        </Card>

        {/* Escrow Wallet */}
        <Card className="border-l-4 border-l-amber-500 shadow-md scale-105 relative overflow-hidden group bg-gradient-to-br from-background to-amber-500/5">
          <div className="absolute right-0 top-0 opacity-5 group-hover:opacity-10 transition-opacity p-4">
            <ShieldCheck className="w-24 h-24 text-amber-500" />
          </div>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-bold text-amber-600 dark:text-amber-500 flex items-center">
              <ShieldCheck className="w-4 h-4 mr-2" />
              Ví Trung Gian (Escrow từ Shop)
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-4xl font-extrabold text-amber-600 dark:text-amber-500">
              {statsLoading ? '...' : formatMoneyMinor(stats?.escrowBalance || 0, 'VND')}
            </div>
            <p className="text-xs font-medium text-amber-700/70 dark:text-amber-400/70 mt-2">
              Tiền bị khoá chờ hoàn tất giao hàng
            </p>
          </CardContent>
        </Card>

        {/* Seller Wallet */}
        <Card className="border-l-4 border-l-emerald-500 shadow-sm relative overflow-hidden group">
          <div className="absolute right-0 top-0 opacity-5 group-hover:opacity-10 transition-opacity p-4">
            <Store className="w-24 h-24" />
          </div>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground flex items-center">
              <Store className="w-4 h-4 mr-2" />
              Tổng Ví Chủ Shop (Seller)
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-3xl font-bold text-emerald-600 dark:text-emerald-400">
              {statsLoading ? '...' : formatMoneyMinor(stats?.sellerBalance || 0, 'VND')}
            </div>
            <p className="text-xs text-muted-foreground mt-2">
              Tổng số dư Shop (khả dụng + đang chờ escrow)
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Live Flow Diagram */}
      <Card className="bg-muted/30 border-dashed">
        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row items-center justify-center gap-4 text-center">
            <div className="flex flex-col items-center">
              <div className="w-16 h-16 rounded-full bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center mb-2">
                <Users className="text-blue-600 dark:text-blue-400" />
              </div>
              <span className="font-medium text-sm">Khách hàng</span>
            </div>
            
            <div className="flex-1 flex flex-col items-center px-4 max-w-xs">
              <span className="text-xs text-muted-foreground mb-1">Thanh toán đơn hàng</span>
              <div className="w-full h-1 bg-gradient-to-r from-blue-400 to-amber-400 rounded-full relative">
                <div className="absolute right-0 -top-1.5 w-4 h-4 rounded-full bg-amber-400 animate-pulse"></div>
              </div>
            </div>

            <div className="flex flex-col items-center">
              <div className="w-20 h-20 rounded-full bg-amber-100 dark:bg-amber-900/30 flex items-center justify-center mb-2 shadow-inner border-2 border-amber-200 dark:border-amber-800">
                <ShieldCheck className="text-amber-600 dark:text-amber-500 w-8 h-8" />
              </div>
              <span className="font-bold text-amber-600 dark:text-amber-500">Ví Escrow</span>
            </div>

            <div className="flex-1 flex flex-col items-center px-4 max-w-xs">
              <span className="text-xs text-muted-foreground mb-1">Hoàn tất giao hàng</span>
              <div className="w-full h-1 bg-gradient-to-r from-amber-400 to-emerald-400 rounded-full relative">
                <div className="absolute right-0 -top-1.5 w-4 h-4 rounded-full bg-emerald-400 animate-pulse"></div>
              </div>
            </div>

            <div className="flex flex-col items-center">
              <div className="w-16 h-16 rounded-full bg-emerald-100 dark:bg-emerald-900/30 flex items-center justify-center mb-2">
                <Store className="text-emerald-600 dark:text-emerald-400" />
              </div>
              <span className="font-medium text-sm">Chủ Shop</span>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Live Ledger */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2">
              <Activity className="w-5 h-5 text-primary" />
              Sổ Cái Giao Dịch (Live Ledger)
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Toàn bộ lịch sử dòng tiền chảy qua hệ thống
            </p>
          </div>
        </CardHeader>
        <CardContent>
          <div className="rounded-md border">
            <table className="w-full text-sm text-left">
              <thead className="bg-muted/50 text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-medium">Thời gian</th>
                  <th className="px-4 py-3 font-medium">Loại GD</th>
                  <th className="px-4 py-3 font-medium text-right">Số tiền</th>
                  <th className="px-4 py-3 font-medium">Nội dung</th>
                  <th className="px-4 py-3 font-medium">Mã tham chiếu</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {txLoading ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                      Đang tải dữ liệu sổ cái...
                    </td>
                  </tr>
                ) : transactions?.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-4 py-8 text-center text-muted-foreground">
                      Chưa có giao dịch nào trong hệ thống.
                    </td>
                  </tr>
                ) : (
                  transactions?.map((tx) => (
                    <tr key={tx.id} className="hover:bg-muted/30 transition-colors">
                      <td className="px-4 py-3 text-muted-foreground">
                        {new Date(tx.createdAt).toLocaleString('vi-VN')}
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className={
                          tx.transactionType === 'TOP_UP' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30' :
                          tx.transactionType === 'PAYMENT' ? 'bg-purple-100 text-purple-700 dark:bg-purple-900/30' :
                          tx.transactionType === 'PAYOUT' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30' :
                          'bg-gray-100 text-gray-700 dark:bg-gray-800'
                        }>
                          {tx.transactionType}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-right font-medium">
                        <span className={tx.type === 'CREDIT' ? 'text-success' : 'text-destructive'}>
                          {tx.type === 'CREDIT' ? '+' : '-'}{formatMoneyMinor(tx.amountMinor, 'VND')}
                        </span>
                      </td>
                      <td className="px-4 py-3 max-w-[250px] truncate" title={tx.description}>
                        {tx.description}
                      </td>
                      <td className="px-4 py-3 text-xs font-mono text-muted-foreground">
                        {tx.referenceType} #{tx.referenceId?.substring(0, 8)}...
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
