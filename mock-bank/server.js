const express = require('express');
const crypto = require('crypto');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;
const JAVA_BACKEND_URL = process.env.JAVA_BACKEND_URL || 'http://localhost:8081';
const MOCK_SECRET = 'mock_secret_v_erp_lite_2025';

app.use(express.json());
app.use(cors());

// Helpers
function hmacSha256(data, secret) {
  return crypto.createHmac('sha256', secret).update(data, 'utf8').digest('hex');
}

function normalizeCurrency(currency) {
  return currency ? currency.toUpperCase() : 'VND';
}

function signPayload(intentId, transactionId, amountMinor) {
  const payload = `${intentId}:${transactionId}:${amountMinor}:${MOCK_SECRET}`;
  return hmacSha256(payload, MOCK_SECRET);
}

// Generate transaction ID similar to backend
function generateTransactionId() {
  const timestamp = Date.now();
  const randomStr = crypto.randomUUID().substring(0, 8);
  return `MOCK_${timestamp}_${randomStr}`;
}

// 1. Initiate Charge
app.post('/payments/mockbank/initiate-charge', (req, res) => {
  const { paymentIntentId, amountMinor, currency: rawCurrency } = req.body;

  if (!paymentIntentId) {
    return res.status(400).json({ error: 'paymentIntentId is required' });
  }

  const currency = normalizeCurrency(rawCurrency);
  const transactionId = generateTransactionId();
  const signature = signPayload(paymentIntentId, transactionId, amountMinor);

  const result = {
    providerTransactionId: transactionId,
    status: 'PENDING',
    message: 'Mock charge initiated successfully. Awaiting webhook simulation.',
    metadata: {
      intent_id: paymentIntentId,
      gateway_transaction_id: transactionId,
      amount_minor: amountMinor.toString(),
      currency,
      signature
    }
  };

  res.json(result);
});

// 2. Initiate Refund
app.post('/payments/mockbank/initiate-refund', (req, res) => {
  const { gatewayTransactionId, amountMinor, currency: rawCurrency, idempotencyKey } = req.body;

  if (!gatewayTransactionId) {
    return res.status(400).json({ error: 'gatewayTransactionId is required' });
  }

  const currency = normalizeCurrency(rawCurrency);
  
  // Refund ID generation matching Java: "REF_" + UUID(nameUUIDFromBytes)
  // Simplified here since it's a mock
  const stableKey = `MOCK:${gatewayTransactionId}:${amountMinor}:${currency}:${idempotencyKey || crypto.randomUUID()}`;
  
  // Create deterministic UUID based on stableKey (UUIDv5 equivalent)
  // For simplicity, just hash the key and format as a mock UUID-like string
  const hash = crypto.createHash('sha1').update(stableKey).digest('hex');
  const mockUuid = `${hash.substring(0, 8)}-${hash.substring(8, 12)}-${hash.substring(12, 16)}-${hash.substring(16, 20)}-${hash.substring(20, 32)}`;
  const transactionId = `REF_${mockUuid}`;

  const result = {
    providerTransactionId: transactionId,
    status: 'SUCCESS',
    message: 'Mock refund processed successfully.',
    metadata: {
      gateway_transaction_id: gatewayTransactionId
    }
  };

  res.json(result);
});

// 3. Initiate Payout
app.post('/payments/mockbank/initiate-payout', (req, res) => {
  const { bankName, bankAccountNumber, bankAccountHolder, amountMinor, currency } = req.body;

  if (!bankAccountNumber) {
    return res.status(400).json({ error: 'bankAccountNumber is required' });
  }

  const transactionId = `PAY_${crypto.randomUUID().replace(/-/g, '').substring(0, 16)}`;

  const result = {
    providerTransactionId: transactionId,
    status: 'SUCCESS',
    message: 'Mock payout transfer completed successfully.',
    metadata: {
      bank_name: bankName || 'MOCK_BANK',
      bank_account: bankAccountNumber
    }
  };

  res.json(result);
});

// 4. Simulate Transferred Webhook
app.post('/payments/mockbank/simulate-transferred', async (req, res) => {
  const { paymentIntentId, gatewayTransactionId, status: rawStatus, amountMinor, currency: rawCurrency } = req.body;

  if (!paymentIntentId || !gatewayTransactionId) {
    return res.status(400).json({ error: 'paymentIntentId and gatewayTransactionId are required' });
  }

  const status = rawStatus ? rawStatus.trim().toUpperCase() : 'SUCCESS';
  const currency = normalizeCurrency(rawCurrency);

  try {
    // Generate inner signature
    const innerSig = signPayload(paymentIntentId, gatewayTransactionId, amountMinor);

    // Construct payload JSON exactly as Java expects
    const payloadObj = {
      paymentIntentId,
      gatewayTransactionId,
      status,
      amountMinor,
      currency,
      signature: innerSig
    };
    
    const jsonPayload = JSON.stringify(payloadObj);

    // Calculate HMAC-SHA256 header signature over the entire payload bytes
    const headerSignature = hmacSha256(jsonPayload, MOCK_SECRET);

    // POST to webhook endpoint locally
    const webhookUrl = `${JAVA_BACKEND_URL}/api/payments/webhook/MOCK`;
    
    const response = await fetch(webhookUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Webhook-Signature': headerSignature
      },
      body: jsonPayload
    });

    const responseText = await response.text();

    if (response.ok) {
      res.json({
        status: 'SIMULATION_SUCCESS',
        webhookResponseCode: response.status,
        webhookResponseBody: responseText
      });
    } else {
      res.status(response.status).json({
        status: 'SIMULATION_FAILED',
        webhookResponseCode: response.status,
        webhookResponseBody: responseText
      });
    }
  } catch (err) {
    res.status(500).json({
      status: 'SIMULATION_ERROR',
      message: err.message
    });
  }
});

app.listen(PORT, () => {
  console.log(`Mock Bank service running on http://localhost:${PORT}`);
  console.log(`Webhook requests will be sent to ${JAVA_BACKEND_URL}`);
});
