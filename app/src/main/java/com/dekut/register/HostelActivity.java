package com.dekut.register;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/** Activity 3 – hostel selection. */
public class HostelActivity extends BaseActivity {

    private EditText etStudentId;
    private Spinner spLocation, spRoomType;
    private RadioGroup rgShare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hostel);
        watchConnection();

        etStudentId = findViewById(R.id.etStudentId);
        spLocation = findViewById(R.id.spLocation);
        spRoomType = findViewById(R.id.spRoomType);
        rgShare = findViewById(R.id.rgShare);

        spLocation.setAdapter(ArrayAdapter.createFromResource(this, R.array.hostel_locations,
                android.R.layout.simple_spinner_dropdown_item));
        spRoomType.setAdapter(ArrayAdapter.createFromResource(this, R.array.room_types,
                android.R.layout.simple_spinner_dropdown_item));

        String idNo = getIntent().getStringExtra("idNo");
        if (idNo != null) etStudentId.setText(idNo);

        findViewById(R.id.btnNext).setOnClickListener(v -> submit());
        findViewById(R.id.btnSkip).setOnClickListener(v -> goNext());
    }

    private void submit() {
        if (!validId(etStudentId)) return;
        if (notChosen(spLocation, "a hostel location")) return;
        if (notChosen(spRoomType, "a room type")) return;
        if (rgShare.getCheckedRadioButtonId() == -1) { toast("Please choose sharable or not"); return; }

        boolean sharable = rgShare.getCheckedRadioButtonId() == R.id.rbSharable;

        Map<String, Object> h = new HashMap<>();
        h.put("studentId", text(etStudentId));
        h.put("location", spLocation.getSelectedItem().toString());
        h.put("roomType", spRoomType.getSelectedItem().toString());
        h.put("sharable", sharable);

        FirebaseDatabase.getInstance().getReference("hostels").child(text(etStudentId))
                .setValue(h).addOnSuccessListener(x -> toast("✔ Hostel booking uploaded"));

        if (!isOnline()) toast("Offline: hostel booking saved on device, will upload later.");
        goNext();
    }

    private void goNext() {
        Intent i = new Intent(this, PaymentActivity.class);
        i.putExtra("idNo", text(etStudentId));
        startActivity(i);
    }
}
