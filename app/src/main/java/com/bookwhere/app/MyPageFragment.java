package com.bookwhere.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MyPageFragment extends Fragment {

    private static final String PREFS_NAME = "bookwhere_mypage_prefs";
    private static final String KEY_NOTIFICATION = "pref_notification";
    private static final String KEY_DARK_MODE = "pref_dark_mode";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    private TextView tvWelcomeGreeting;
    private TextView tvUserEmail;
    private TextView tvUserUid;
    private LinearLayout rowMyInfo;
    private LinearLayout layoutMyInfoDetail;
    private TextView tvMyInfoArrow;
    private LinearLayout rowLogout;

    private MaterialSwitch switchNotification;
    private MaterialSwitch switchDarkMode;

    private LinearLayout rowBugReport;
    private LinearLayout rowTerms;
    private TextView tvAppVersion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mypage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews(view);
        bindUserInfo();
        setupListeners();
    }

    private void initViews(View view) {
        tvWelcomeGreeting = view.findViewById(R.id.tv_welcome_greeting);
        tvUserEmail = view.findViewById(R.id.tv_user_email);
        tvUserUid = view.findViewById(R.id.tv_user_uid);
        rowMyInfo = view.findViewById(R.id.row_my_info);
        layoutMyInfoDetail = view.findViewById(R.id.layout_my_info_detail);
        tvMyInfoArrow = view.findViewById(R.id.tv_my_info_arrow);
        rowLogout = view.findViewById(R.id.row_logout);

        switchNotification = view.findViewById(R.id.switch_notification);
        switchDarkMode = view.findViewById(R.id.switch_dark_mode);

        rowBugReport = view.findViewById(R.id.row_bug_report);
        rowTerms = view.findViewById(R.id.row_terms);
        tvAppVersion = view.findViewById(R.id.tv_app_version);
    }

    private void bindUserInfo() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String uid = currentUser.getUid();
            String email = currentUser.getEmail();

            // 1차로 Auth에 캐싱된 displayName이 있으면 먼저 표시 (깜빡임 방지)
            String initialName = !TextUtils.isEmpty(currentUser.getDisplayName())
                    ? currentUser.getDisplayName() : "사용자";
            tvWelcomeGreeting.setText(initialName + "님, 안녕하세요.\n오늘도 가볍게 몰입하세요!");
            tvUserEmail.setText(!TextUtils.isEmpty(email) ? email : "이메일 정보 없음");
            tvUserUid.setText("회원 식별 코드: " + uid);

            // 2차로 Firestore users/{uid} 문서에서 저장된 nickname 조회
            db.collection("users").document(uid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!isAdded()) return;
                        if (documentSnapshot.exists()) {
                            String savedNickname = documentSnapshot.getString("nickname");
                            if (!TextUtils.isEmpty(savedNickname)) {
                                tvWelcomeGreeting.setText(savedNickname + "님, 안녕하세요.\n오늘도 가볍게 몰입하세요!");
                            }
                        }
                    });
        } else {
            tvWelcomeGreeting.setText("사용자님, 안녕하세요.\n오늘도 가볍게 몰입하세요!");
            tvUserEmail.setText("로그인이 필요합니다");
            tvUserUid.setText("회원 식별 코드: -");
        }

        switchNotification.setChecked(prefs.getBoolean(KEY_NOTIFICATION, true));
        switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        tvAppVersion.setText("v" + getAppVersionName());
    }

    private void setupListeners() {
        rowMyInfo.setOnClickListener(v -> {
            boolean isHidden = layoutMyInfoDetail.getVisibility() == View.GONE;
            layoutMyInfoDetail.setVisibility(isHidden ? View.VISIBLE : View.GONE);
            tvMyInfoArrow.setText(isHidden ? "⌃" : "⌄");
        });

        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFICATION, isChecked).apply();
            Toast.makeText(
                    requireContext(),
                    isChecked ? "알림이 켜졌습니다." : "알림이 꺼졌습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
        });

        rowBugReport.setOnClickListener(v -> showBugReportDialog());
        rowTerms.setOnClickListener(v -> showTermsDialog());
        rowLogout.setOnClickListener(v -> logoutUser());
    }

    private void showBugReportDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("불편하신 점이나 개선 의견을 적어주세요.");
        input.setMinLines(3);

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("버그 제보 (의견 보내기)")
                .setView(container)
                .setPositiveButton("보내기", (dialog, which) -> {
                    String content = input.getText().toString().trim();
                    if (TextUtils.isEmpty(content)) {
                        Toast.makeText(requireContext(), "내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "소중한 의견이 접수되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showTermsDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("이용약관")
                .setMessage("제1조 (목적)\n본 약관은 '전자책어디(E-book Where)'가 제공하는 전자책 구독 플랫폼 통합 검색 및 위시리스트 서비스의 이용 조건과 절차를 규정합니다.\n\n"
                        + "제2조 (서비스 내용)\n회원은 도서 검색, 플랫폼별 구독 가능 여부 조회, 관심 도서 저장 기능을 이용할 수 있습니다.\n\n"
                        + "제3조 (계정 관리)\n회원은 본인의 이메일 계정 정보를 안전하게 관리할 책임이 있습니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    private String getAppVersionName() {
        try {
            PackageInfo pInfo = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0.0";
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(requireContext(), "로그아웃되었습니다.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
}