package com.dekut.register;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/** Activity 1 – student details. */
public class MainActivity extends BaseActivity {

    private EditText etFullNames, etIdNo, etRegNo;
    private RadioGroup rgGender;
    private Spinner spCourse, spDepartment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        watchConnection();

        etFullNames = findViewById(R.id.etFullNames);
        etIdNo = findViewById(R.id.etIdNo);
        etRegNo = findViewById(R.id.etRegNo);
        rgGender = findViewById(R.id.rgGender);
        spCourse = findViewById(R.id.spCourse);
        spDepartment = findViewById(R.id.spDepartment);

        spCourse.setAdapter(ArrayAdapter.createFromResource(this, R.array.courses,
                android.R.layout.simple_spinner_dropdown_item));
        spDepartment.setAdapter(ArrayAdapter.createFromResource(this, R.array.departments,
                android.R.layout.simple_spinner_dropdown_item));

        findViewById(R.id.btnNext).setOnClickListener(v -> register());
        findViewById(R.id.btnSummary).setOnClickListener(v ->
                startActivity(new Intent(this, SummaryActivity.class)));
    }

    private void register() {
        String name = text(etFullNames);
        String regNo = text(etRegNo);

        if (name.isEmpty()) { etFullNames.setError("Required"); return; }
        if (!validId(etIdNo)) return;
        if (regNo.isEmpty()) { etRegNo.setError("Required"); return; }
        int genderId = rgGender.getCheckedRadioButtonId();
        if (genderId == -1) { toast("Please select gender"); return; }
        if (notChosen(spCourse, "a course")) return;
        if (notChosen(spDepartment, "a department")) return;

        String idNo = text(etIdNo);
        String gender = ((RadioButton) findViewById(genderId)).getText().toString();

        Map<String, Object> student = new HashMap<>();
        student.put("fullNames", name);
        student.put("idNo", idNo);
        student.put("regNo", regNo);
        student.put("gender", gender);
        student.put("course", spCourse.getSelectedItem().toString());
        student.put("department", spDepartment.getSelectedItem().toString());
        student.put("status", "PENDING_PAYMENT");
        student.put("createdAt", ServerValue.TIMESTAMP);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("students").child(idNo);
        // The completion listener only fires once the SERVER has acknowledged the write,
        // so offline it fires later, automatically, when connectivity returns.
        ref.updateChildren(student).addOnSuccessListener(x ->
                toast("✔ Registration details for " + idNo + " uploaded to Firebase"));

        if (!isOnline()) {
            toast("Offline: details saved on this device. They will upload when internet returns.");
        }

        Intent i = new Intent(this, LibraryActivity.class);
        i.putExtra("idNo", idNo);
        startActivity(i);
    }
}
