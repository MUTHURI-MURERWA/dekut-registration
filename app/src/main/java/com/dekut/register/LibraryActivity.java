package com.dekut.register;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/** Activity 2 – library book request. */
public class LibraryActivity extends BaseActivity {

    private EditText etStudentId, etIsbn, etAuthor, etTitle, etBorrowed, etReturn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        watchConnection();

        etStudentId = findViewById(R.id.etStudentId);
        etIsbn = findViewById(R.id.etIsbn);
        etAuthor = findViewById(R.id.etAuthor);
        etTitle = findViewById(R.id.etTitle);
        etBorrowed = findViewById(R.id.etBorrowed);
        etReturn = findViewById(R.id.etReturn);

        String idNo = getIntent().getStringExtra("idNo");
        if (idNo != null) etStudentId.setText(idNo);

        etBorrowed.setOnClickListener(v -> pickDate(etBorrowed));
        etReturn.setOnClickListener(v -> pickDate(etReturn));

        findViewById(R.id.btnNext).setOnClickListener(v -> submit());
        findViewById(R.id.btnSkip).setOnClickListener(v -> goNext());
    }

    private void submit() {
        if (!validId(etStudentId)) return;
        if (text(etIsbn).isEmpty()) { etIsbn.setError("Required"); return; }
        if (text(etAuthor).isEmpty()) { etAuthor.setError("Required"); return; }
        if (text(etTitle).isEmpty()) { etTitle.setError("Required"); return; }
        if (text(etBorrowed).isEmpty()) { etBorrowed.setError("Pick a date"); return; }
        if (text(etReturn).isEmpty()) { etReturn.setError("Pick a date"); return; }
        if (text(etReturn).compareTo(text(etBorrowed)) < 0) {
            etReturn.setError("Return date can't be before the borrow date");
            return;
        }

        Map<String, Object> book = new HashMap<>();
        book.put("isbn", text(etIsbn));
        book.put("author", text(etAuthor));
        book.put("title", text(etTitle));
        book.put("dateBorrowed", text(etBorrowed));
        book.put("dateReturn", text(etReturn));
        book.put("studentId", text(etStudentId));

        DatabaseReference ref = FirebaseDatabase.getInstance()
                .getReference("books").child(text(etStudentId)).push();
        ref.setValue(book).addOnSuccessListener(x -> toast("✔ Book request uploaded"));

        if (!isOnline()) toast("Offline: book request saved on device, will upload later.");
        goNext();
    }

    private void goNext() {
        Intent i = new Intent(this, HostelActivity.class);
        i.putExtra("idNo", text(etStudentId));
        startActivity(i);
    }
}
