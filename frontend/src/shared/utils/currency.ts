export function formatUSD(amount: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(amount)
}

/** Currencies whose smallest (minor) unit equals the major unit — exponent 0. */
const ZERO_DECIMAL_CURRENCIES = new Set(['VND', 'JPY', 'KRW', 'CLP', 'ISK', 'XOF', 'XAF'])

/** Number of decimal places (currency exponent) for a given ISO currency code. */
export function currencyExponent(currency: string): number {
  return ZERO_DECIMAL_CURRENCIES.has(currency?.toUpperCase()) ? 0 : 2
}

/**
 * Format an amount stored in minor units (e.g. cents, đồng) as localized currency.
 * `1025` + `USD` -> `$10.25`; `10000` + `VND` -> `₫10,000`.
 */
export function formatMoneyMinor(amountMinor: number, currency: string): string {
  const exponent = currencyExponent(currency)
  const major = amountMinor / Math.pow(10, exponent)
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      minimumFractionDigits: exponent,
      maximumFractionDigits: exponent,
    }).format(major)
  } catch {
    // Unknown/invalid ISO code — fall back to a plain number + suffix.
    return `${major.toLocaleString()} ${currency}`
  }
}
