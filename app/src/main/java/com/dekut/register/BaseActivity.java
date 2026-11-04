package com.dekut.register;

import android.app.DatePickerDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Calendar;
import java.util.Locale;

/** Shared helpers: live online/offline banner, date picker, validation bits. */
public abstract class BaseActivity extends AppCompatActivity {

    private DatabaseReference connRef;
    private ValueEventListener connListener;

    /** Call after setContentView. Shows a green/red banner driven by Firebase's own connection state. */
    protected void watchConnection() {
        final TextView tv = findViewById(R.id.tvConn);
        if (tv == null) return;
        connRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                tv.setText(connected
                        ? "● ONLINE – data is syncing to Firebase"
                        : "● OFFLINE – data is saved on this device and will upload when internet returns");
                tv.setBackgroundColor(connected ? 0xFF2E7D32 : 0xFFC62828);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        connRef.addValueEventListener(connListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connRef != null && connListener != null) connRef.removeEventListener(connListener);
    }

    protected boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities c = cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    protected static String text(EditText e) {
        return e.getText().toString().trim();
    }

    protected void toast(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }

    /** Returns true (and shows a message) if the spinner is still on its "Select…" placeholder. */
    protected boolean notChosen(Spinner sp, String what) {
        if (sp.getSelectedItemPosition() == 0) {
            toast("Please select " + what);
            return true;
        }
        return false;
    }

    protected void pickDate(final EditText target) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) ->
                target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    protected boolean validId(EditText et) {
        if (!text(et).matches("\\d{7,8}")) {
            et.setError("Enter a valid ID number (7–8 digits)");
            return false;
        }
        return true;
    }
}
