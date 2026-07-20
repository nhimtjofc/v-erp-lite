(async () => {
  const params = new URLSearchParams();
  params.append('client_id', 'v-erp-app');
  params.append('username', 'owner@v-erp.com');
  params.append('password', 'password');
  params.append('grant_type', 'password');
  
  const tokenRes = await fetch('http://localhost:8181/realms/v-erp-lite/protocol/openid-connect/token', { method: 'POST', body: params });
  const tokenData = await tokenRes.json();
  const token = tokenData.access_token;
  
  const putRes = await fetch('http://localhost:8081/api/wallets/me/bank-details', {
    method: 'PUT',
    headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    body: JSON.stringify({ bankName: 'BIDV', bankAccountNumber: '123', bankAccountHolder: 'ABC' })
  });
  
  const putText = await putRes.text();
  console.log('STATUS:', putRes.status);
  console.log('BODY:', putText);
})();
