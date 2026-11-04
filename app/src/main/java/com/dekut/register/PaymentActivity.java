package com.dekut.register;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Activity 4 – M-Pesa fees payment.
 * The app asks our backend to trigger an STK push. Safaricom calls the backend back with the
 * result, the backend writes it to /payments/{idNo}, and this screen reacts to that node.
 * Only then is the student's registration marked CONFIRMED.
 */
public class PaymentActivity extends BaseActivity {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private EditText etStudentId, etPhone, etAmount;
    private Button btnPay;
    private ProgressBar progress;
    private TextView tvStatus;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DatabaseReference payRef;
    private ValueEventListener payListener;
    private boolean waiting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);
        watchConnection();

        etStudentId = findViewById(R.id.etStudentId);
        etPhone = findViewById(R.id.etPhone);
        etAmount = findViewById(R.id.etAmount);
        btnPay = findViewById(R.id.btnPay);
        progress = findViewById(R.id.progress);
        tvStatus = findViewById(R.id.tvStatus);

        String idNo = getIntent().getStringExtra("idNo");
        if (idNo != null) etStudentId.setText(idNo);

        btnPay.setOnClickListener(v -> startPayment());
    }

    private void startPayment() {
        if (!validId(etStudentId)) return;
        final String idNo = text(etStudentId);

        final String phone = normalizePhone(text(etPhone));
        if (phone == null) { etPhone.setError("Use 07XXXXXXXX or 2547XXXXXXXX"); return; }

        final int amount;
        try {
            amount = Integer.parseInt(text(etAmount));
        } catch (NumberFormatException e) { etAmount.setError("Enter an amount"); return; }
        if (amount < 1) { etAmount.setError("Minimum is Kshs 1"); return; }

        if (!isOnline()) {
            tvStatus.setText("No internet connection.\nM-Pesa needs internet. Your registration details are "
                    + "saved and will upload automatically – come back and pay once you are online.");
            return;
        }

        setBusy(true);
        tvStatus.setText("Sending M-Pesa prompt to " + phone + "…");

        payRef = FirebaseDatabase.getInstance().getReference("payments").child(idNo);
        Map<String, Object> pending = new HashMap<>();
        pending.put("status", "PENDING");
        // reset any old result first so we never react to a stale SUCCESS
        payRef.setValue(pending).addOnSuccessListener(x -> {
            listenForResult(idNo);
            callBackend(idNo, phone, amount);
        }).addOnFailureListener(e -> fail("Could not reach the database: " + e.getMessage()));
    }

    private void callBackend(String idNo, String phone, int amount) {
        JSONObject body = new JSONObject();
        try {
            body.put("idNo", idNo);
            body.put("phone", phone);
            body.put("amount", amount);
        } catch (JSONException e) { fail("Bad request"); return; }

        Request req = new Request.Builder()
                .url(Config.STK_PUSH_URL)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> fail("Could not reach the payment server: " + e.getMessage()));
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response resp) throws IOException {
                final String s = resp.body() != null ? resp.body().string() : "";
                final boolean ok = resp.isSuccessful();
                runOnUiThread(() -> {
                    if (!ok) fail("M-Pesa prompt failed: " + s);
                    else {
                        tvStatus.setText("Check your phone and enter your M-Pesa PIN…");
                        waiting = true;
                        handler.postDelayed(() -> {
                            if (waiting) fail("No response from M-Pesa. If money was deducted it will "
                                    + "still confirm; otherwise tap the button to try again.");
                        }, 90_000);
                    }
                });
            }
        });
    }

    private void listenForResult(final String idNo) {
        if (payListener != null) payRef.removeEventListener(payListener);
        payListener = new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot s) {
                String status = s.child("status").getValue(String.class);
                if ("SUCCESS".equals(status)) {
                    confirm(idNo, s.child("receipt").getValue(String.class));
                } else if ("FAILED".equals(status)) {
                    String msg = s.child("message").getValue(String.class);
                    fail("Payment not completed: " + (msg != null ? msg : "cancelled or failed"));
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError e) { fail(e.getMessage()); }
        };
        payRef.addValueEventListener(payListener);
    }

    private void confirm(final String idNo, String receipt) {
        waiting = false;
        if (payListener != null) payRef.removeEventListener(payListener);
        setBusy(false);

        Map<String, Object> upd = new HashMap<>();
        upd.put("status", "CONFIRMED");
        upd.put("receipt", receipt);
        FirebaseDatabase.getInstance().getReference("students").child(idNo).updateChildren(upd);

        tvStatus.setText("✔ Payment received. Registration confirmed.");
        new AlertDialog.Builder(this)
                .setTitle("Registration confirmed 🎉")
                .setMessage("M-Pesa receipt: " + (receipt != null ? receipt : "—")
                        + "\n\nYou will now see your registration summary.")
                .setCancelable(false)
                .setPositiveButton("View summary", (d, w) -> {
                    Intent i = new Intent(this, SummaryActivity.class);
                    i.putExtra("idNo", idNo);
                    startActivity(i);
                    finish();
                }).show();
    }

    private void fail(String msg) {
        waiting = false;
        setBusy(false);
        tvStatus.setText(msg);
    }

    private void setBusy(boolean busy) {
        btnPay.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    /** 07XXXXXXXX / 01XXXXXXXX / +2547XXXXXXXX → 2547XXXXXXXX, else null. */
    private static String normalizePhone(String raw) {
        String p = raw.replace(" ", "").replace("+", "");
        if (p.matches("0[17]\\d{8}")) return "254" + p.substring(1);
        if (p.matches("254[17]\\d{8}")) return p;
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        waiting = false;
        handler.removeCallbacksAndMessages(null);
        if (payRef != null && payListener != null) payRef.removeEventListener(payListener);
    }
}
