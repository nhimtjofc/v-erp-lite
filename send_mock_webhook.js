const http = require('http');
const crypto = require('crypto');

// The HMAC secret configuration set in your V-ERP application.properties
// e.g.: woocommerce.webhook.secrets.local=my-shared-secret
const secret = 'my-shared-secret';
const tenantId = 'v-erp-app';
const salesChannelId = process.argv[2];

if (!salesChannelId) {
  console.log("------------------------------------------------------------------");
  console.log("Usage: node send_mock_webhook.js <salesChannelId> [wooOrderId]");
  console.log("Example: node send_mock_webhook.js b1000000-0000-0000-0000-000000000001 12345");
  console.log("------------------------------------------------------------------");
  process.exit(1);
}

const wooOrderId = process.argv[3] || "12345";

// Mock WooCommerce order webhook payload
const payload = {
  id: wooOrderId,
  number: wooOrderId,
  status: "processing",
  total: "30.00",
  billing: {
    first_name: "Jane",
    last_name: "Doe",
    email: "jane.doe@example.com",
    phone: "0909090909"
  },
  shipping: {
    first_name: "Jane",
    last_name: "Doe",
    address_1: "456 Mock Street",
    address_2: "",
    city: "Ho Chi Minh City",
    postcode: "70000",
    country: "VN"
  },
  // Ensure that product_id/variation_id match mapped IDs inside your V-ERP database
  line_items: [
    {
      product_id: "ext-prod-coupon", // change this to your actual mapped external product ID
      variation_id: "ext-var-coupon", // change this to your actual mapped external variation ID
      quantity: 1
    }
  ]
};

const body = JSON.stringify(payload);
const signature = crypto.createHmac('sha256', secret).update(body).digest('base64');

const options = {
  hostname: 'localhost',
  port: 8081,
  path: `/api/integrations/woocommerce/v1/orders?tenantId=${tenantId}&salesChannelId=${salesChannelId}`,
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-WC-Webhook-Signature': signature,
    'X-WC-Webhook-Topic': 'order.created'
  }
};

console.log(`Sending order sync webhook for Woo Order ID: ${wooOrderId}...`);
const req = http.request(options, (res) => {
  let resData = '';
  res.on('data', chunk => resData += chunk);
  res.on('end', () => {
    console.log(`HTTP Status: ${res.statusCode}`);
    try {
      console.log("ERP Response:", JSON.stringify(JSON.parse(resData), null, 2));
    } catch (e) {
      console.log("ERP Response (raw):", resData);
    }
  });
});

req.on('error', (e) => {
  console.error(`Request error: ${e.message}`);
});

req.write(body);
req.end();
