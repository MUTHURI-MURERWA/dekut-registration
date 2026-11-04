// M-Pesa (Daraja) STK Push backend for the DeKUT registration app.
// Keeps the Daraja secret OFF the phone, receives Safaricom's result, and writes it to
// Firebase Realtime Database through its REST API (works because the demo rules are open).
require("dotenv").config();
const express = require("express");

const app = express();
app.use(express.json());

const BASE =
  process.env.MPESA_ENV === "production"
    ? "https://api.safaricom.co.ke"
    : "https://sandbox.safaricom.co.ke";
const DB = (process.env.FIREBASE_DB_URL || "").replace(/\/$/, "");

async function dbPut(path, value) {
  const r = await fetch(`${DB}/${path}.json`, { method: "PUT", body: JSON.stringify(value) });
  if (!r.ok) throw new Error(`Firebase write failed (${r.status})`);
}
async function dbGet(path) {
  const r = await fetch(`${DB}/${path}.json`);
  if (!r.ok) throw new Error(`Firebase read failed (${r.status})`);
  return r.json();
}

async function getToken() {
  const auth = Buffer.from(
    `${process.env.MPESA_CONSUMER_KEY}:${process.env.MPESA_CONSUMER_SECRET}`
  ).toString("base64");
  const r = await fetch(`${BASE}/oauth/v1/generate?grant_type=client_credentials`, {
    headers: { Authorization: `Basic ${auth}` },
  });
  const text = await r.text();
  let j;
  try { j = JSON.parse(text); } catch { throw new Error(`Daraja token error: ${text.slice(0, 200)}`); }
  if (!j.access_token) throw new Error("Could not get Daraja access token – check key/secret");
  return j.access_token;
}

app.get("/", (req, res) => res.send("DeKUT M-Pesa server is running"));

// POST { idNo, phone: "2547XXXXXXXX", amount }
app.post("/stkPush", async (req, res) => {
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
        TransactionType: "CustomerPayBillOnline", // "CustomerBuyGoodsOnline" for a Till number
        Amount: amount,
        PartyA: phone,
        PartyB: shortcode,
        PhoneNumber: phone,
        CallBackURL: `${process.env.PUBLIC_URL}/mpesaCallback`,
        AccountReference: idNo,        // Daraja limit: 12 characters
        TransactionDesc: "School fees", // Daraja limit: 13 characters
      }),
    });
    const text = await r.text();
    let j;
    try { j = JSON.parse(text); } catch { j = { raw: text }; }
    console.log("STK response:", j);
    if (j.ResponseCode !== "0") return res.status(400).json({ ok: false, error: j });

    await dbPut(`checkouts/${j.CheckoutRequestID}`, idNo);
    return res.json({ ok: true, checkoutRequestId: j.CheckoutRequestID });
  } catch (e) {
    console.error(e);
    return res.status(500).json({ ok: false, error: String(e.message || e) });
  }
});

// Safaricom calls this with the result of the STK push.
app.post("/mpesaCallback", async (req, res) => {
  console.log("Callback:", JSON.stringify(req.body));
  try {
    const cb = req.body && req.body.Body && req.body.Body.stkCallback;
    if (cb) {
      const idNo = await dbGet(`checkouts/${cb.CheckoutRequestID}`);
      if (idNo) {
        const meta = {};
        ((cb.CallbackMetadata && cb.CallbackMetadata.Item) || []).forEach((i) => (meta[i.Name] = i.Value));
        await dbPut(`payments/${idNo}`, {
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

app.listen(process.env.PORT || 3000, () => console.log("M-Pesa server listening"));
