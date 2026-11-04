// M-Pesa (Daraja) STK Push backend for the DeKUT registration app.
// The Daraja consumer secret stays HERE, never inside the Android app.
const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.database();

const BASE =
  process.env.MPESA_ENV === "production"
    ? "https://api.safaricom.co.ke"
    : "https://sandbox.safaricom.co.ke";

async function getToken() {
  const auth = Buffer.from(
    `${process.env.MPESA_CONSUMER_KEY}:${process.env.MPESA_CONSUMER_SECRET}`
  ).toString("base64");
  const r = await fetch(`${BASE}/oauth/v1/generate?grant_type=client_credentials`, {
    headers: { Authorization: `Basic ${auth}` },
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("Could not get Daraja access token");
  return j.access_token;
}

// POST { idNo, phone: "2547XXXXXXXX", amount }
exports.stkPush = onRequest(async (req, res) => {
  if (req.method !== "POST") return res.status(405).json({ ok: false, error: "POST only" });
  try {
    const { idNo, phone, amount } = req.body || {};
    if (!/^\d{7,8}$/.test(idNo || "")) return res.status(400).json({ ok: false, error: "Bad idNo" });
    if (!/^254[17]\d{8}$/.test(phone || "")) return res.status(400).json({ ok: false, error: "Bad phone" });
    if (!Number.isInteger(amount) || amount < 1) return res.status(400).json({ ok: false, error: "Bad amount" });

    const shortcode = process.env.MPESA_SHORTCODE;
    const timestamp = new Date().toISOString().replace(/[^0-9]/g, "").slice(0, 14);
    const password = Buffer.from(shortcode + process.env.MPESA_PASSKEY + timestamp).toString("base64");
    const token = await getToken();

    const r = await fetch(`${BASE}/mpesa/stkpush/v1/processrequest`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        BusinessShortCode: shortcode,
        Password: password,
        Timestamp: timestamp,
        TransactionType: "CustomerPayBillOnline", // use "CustomerBuyGoodsOnline" for a Till number
        Amount: amount,
        PartyA: phone,
        PartyB: shortcode,
        PhoneNumber: phone,
        CallBackURL: process.env.MPESA_CALLBACK_URL,
        AccountReference: `DEKUT-${idNo}`,
        TransactionDesc: "First year fees",
      }),
    });
    const j = await r.json();
    if (j.ResponseCode !== "0") return res.status(400).json({ ok: false, error: j });

    // remember which student this CheckoutRequestID belongs to
    await db.ref(`checkouts/${j.CheckoutRequestID}`).set(idNo);
    return res.json({ ok: true, checkoutRequestId: j.CheckoutRequestID });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ ok: false, error: String(e.message || e) });
  }
});

// Safaricom calls this with the result of the STK push.
exports.mpesaCallback = onRequest(async (req, res) => {
  try {
    const cb = req.body && req.body.Body && req.body.Body.stkCallback;
    if (cb) {
      const idNo = (await db.ref(`checkouts/${cb.CheckoutRequestID}`).get()).val();
      if (idNo) {
        const meta = {};
        ((cb.CallbackMetadata && cb.CallbackMetadata.Item) || []).forEach((i) => (meta[i.Name] = i.Value));
        await db.ref(`payments/${idNo}`).set({
          status: cb.ResultCode === 0 ? "SUCCESS" : "FAILED",
          message: cb.ResultDesc,
          receipt: meta.MpesaReceiptNumber || null,
          amount: meta.Amount || null,
          phone: meta.PhoneNumber || null,
          time: Date.now(),
        });
      }
    }
  } catch (e) {
    console.error(e);
  }
  res.json({ ResultCode: 0, ResultDesc: "Accepted" });
});
