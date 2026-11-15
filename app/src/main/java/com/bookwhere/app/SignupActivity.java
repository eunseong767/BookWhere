package com.bookwhere.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputEditText etNickname;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etPasswordConfirm;
    private MaterialButton btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        TextView tvBack = findViewById(R.id.tv_signup_back);
        etNickname = findViewById(R.id.et_signup_nickname);
        etEmail = findViewById(R.id.et_signup_email);
        etPassword = findViewById(R.id.et_signup_pw);
        etPasswordConfirm = findViewById(R.id.et_signup_pw_confirm);
        btnSubmit = findViewById(R.id.btn_signup_submit);

        tvBack.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> attemptSignup());
    }

    private void attemptSignup() {
        String nickname = getText(etNickname);
        String email = getText(etEmail);
        String password = getText(etPassword);
        String passwordConfirm = getText(etPasswordConfirm);

        if (!validateInputs(nickname, email, password, passwordConfirm)) return;

        btnSubmit.setEnabled(false);

        // 1단계: Firebase Authentication 계정 생성
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Auth 프로필에도 닉네임 동기화 후 Firestore 저장
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(nickname)
                                    .build();
                            user.updateProfile(profileUpdates);

                            saveUserToFirestore(user.getUid(), email, nickname);
                        }
                    } else {
                        btnSubmit.setEnabled(true);
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "회원가입 실패";
                        Toast.makeText(this, "가입 실패: " + errorMsg, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 2단계: Firestore users/{uid} 문서에 email, nickname, createdAt 저장
    private void saveUserToFirestore(String uid, String email, String nickname) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("nickname", nickname);
        userData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("users").document(uid)
                .set(userData)
                .addOnSuccessListener(unused -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this, nickname + "님, 환영합니다!", Toast.LENGTH_SHORT).show();
                    moveToMain();
                })
                .addOnFailureListener(e -> {
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this, "프로필 저장 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private boolean validateInputs(String nickname, String email, String password, String passwordConfirm) {
        if (TextUtils.isEmpty(nickname)) {
            etNickname.setError("닉네임을 입력해주세요.");
            etNickname.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(email)) {
            etEmail.setError("이메일을 입력해주세요.");
            etEmail.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            etPassword.setError("비밀번호를 6자 이상 입력해주세요.");
            etPassword.requestFocus();
            return false;
        }
        if (!password.equals(passwordConfirm)) {
            etPasswordConfirm.setError("비밀번호가 일치하지 않습니다.");
            etPasswordConfirm.requestFocus();
            return false;
        }
        return true;
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void moveToMain() {
        Intent intent = new Intent(SignupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
