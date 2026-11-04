package com.dekut.register;

import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Shows the registration summary for one student or for every student,
 * and prints it (or saves it as PDF) through Android's print framework.
 * Works offline from the local Firebase cache.
 */
public class SummaryActivity extends BaseActivity {

    private WebView wv;
    private EditText etIdNo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);
        watchConnection();

        wv = findViewById(R.id.wv);
        etIdNo = findViewById(R.id.etIdNo);
        show("<p style='color:#777'>Choose <b>This student</b> or <b>All students</b>.</p>");

        findViewById(R.id.btnOne).setOnClickListener(v -> {
            if (validId(etIdNo)) load(text(etIdNo));
        });
        findViewById(R.id.btnAll).setOnClickListener(v -> load(null));
        findViewById(R.id.btnPrint).setOnClickListener(v -> print());

        String idNo = getIntent().getStringExtra("idNo");
        if (idNo != null) {
            etIdNo.setText(idNo);
            load(idNo);
        }
    }

    private void load(final String idOrNull) {
        FirebaseDatabase.getInstance().getReference()
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override public void onDataChange(@NonNull DataSnapshot root) {
                        show(build(root, idOrNull));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError e) {
                        show("<p>Could not load data: " + esc(e.getMessage()) + "</p>");
                    }
                });
    }

    private String build(DataSnapshot root, String idOrNull) {
        StringBuilder h = new StringBuilder();
        String stamp = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
        h.append("<h1>DeKUT – First Year Registration Summary</h1><div class='muted'>Generated ")
                .append(stamp).append("</div>");

        DataSnapshot students = root.child("students");
        if (idOrNull != null) {
            if (!students.child(idOrNull).exists()) {
                h.append("<p>No student found with ID <b>").append(esc(idOrNull)).append("</b>.</p>");
            } else {
                card(h, root, idOrNull);
            }
        } else {
            h.append("<h2>All registered students (").append(students.getChildrenCount()).append(")</h2>");
            if (!students.hasChildren()) h.append("<p>No students registered yet.</p>");
            for (DataSnapshot s : students.getChildren()) card(h, root, s.getKey());
        }
        return h.toString();
    }

    private void card(StringBuilder h, DataSnapshot root, String id) {
        DataSnapshot s = root.child("students").child(id);
        String status = val(s, "status");
        boolean confirmed = "CONFIRMED".equals(status);

        h.append("<div class='card'><h2>").append(esc(val(s, "fullNames"))).append("</h2>");
        h.append("<span class='badge ").append(confirmed ? "ok" : "pend").append("'>")
                .append(confirmed ? "CONFIRMED" : "PENDING PAYMENT").append("</span><table>");
        row(h, "ID Number", val(s, "idNo"));
        row(h, "Registration No.", val(s, "regNo"));
        row(h, "Gender", val(s, "gender"));
        row(h, "Course", val(s, "course"));
        row(h, "Department", val(s, "department"));
        h.append("</table>");

        DataSnapshot hs = root.child("hostels").child(id);
        h.append("<h3>Hostel</h3>");
        if (hs.exists()) {
            h.append("<table>");
            row(h, "Location", val(hs, "location"));
            row(h, "Room type", val(hs, "roomType"));
            row(h, "Sharable", Boolean.TRUE.equals(hs.child("sharable").getValue(Boolean.class)) ? "Yes" : "No");
            h.append("</table>");
        } else h.append("<p class='muted'>No hostel booked.</p>");

        h.append("<h3>Library</h3>");
        DataSnapshot bs = root.child("books").child(id);
        if (bs.exists()) {
            h.append("<table><tr><th>ISBN</th><th>Title</th><th>Author</th><th>Borrowed</th><th>Return</th></tr>");
            for (DataSnapshot b : bs.getChildren()) {
                h.append("<tr><td>").append(esc(val(b, "isbn"))).append("</td><td>")
                        .append(esc(val(b, "title"))).append("</td><td>")
                        .append(esc(val(b, "author"))).append("</td><td>")
                        .append(esc(val(b, "dateBorrowed"))).append("</td><td>")
                        .append(esc(val(b, "dateReturn"))).append("</td></tr>");
            }
            h.append("</table>");
        } else h.append("<p class='muted'>No books requested.</p>");

        h.append("<h3>Fees</h3>");
        DataSnapshot p = root.child("payments").child(id);
        if ("SUCCESS".equals(val(p, "status"))) {
            h.append("<table>");
            row(h, "M-Pesa receipt", val(p, "receipt"));
            row(h, "Amount (Kshs)", val(p, "amount"));
            row(h, "Paid from", val(p, "phone"));
            h.append("</table>");
        } else h.append("<p class='muted'>No payment received yet.</p>");
        h.append("</div>");
    }

    private void row(StringBuilder h, String k, String v) {
        h.append("<tr><td class='k'>").append(esc(k)).append("</td><td>").append(esc(v)).append("</td></tr>");
    }

    private static String val(DataSnapshot s, String key) {
        Object o = s.child(key).getValue();
        return o == null ? "—" : String.valueOf(o);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void show(String body) {
        String css = "body{font-family:sans-serif;padding:10px;color:#222}h1{font-size:19px;color:#0D47A1;margin:0}"
                + "h2{font-size:17px;margin:12px 0 4px}h3{font-size:14px;margin:12px 0 4px;color:#0D47A1}"
                + ".muted{color:#777;font-size:12px}table{border-collapse:collapse;width:100%;margin-top:4px}"
                + "td,th{border:1px solid #bbb;padding:4px 6px;font-size:13px;text-align:left}"
                + "td.k{width:38%;background:#f2f5fa;font-weight:bold}.card{margin:14px 0 22px;page-break-inside:avoid}"
                + ".badge{display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;color:#fff;margin-bottom:6px}"
                + ".ok{background:#2E7D32}.pend{background:#EF6C00}";
        wv.loadDataWithBaseURL(null, "<html><head><meta name='viewport' content='width=device-width'>"
                + "<style>" + css + "</style></head><body>" + body + "</body></html>", "text/html", "UTF-8", null);
    }

    private void print() {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        String job = "DeKUT_Registration_" + System.currentTimeMillis();
        pm.print(job, wv.createPrintDocumentAdapter(job), new PrintAttributes.Builder().build());
    }
}
