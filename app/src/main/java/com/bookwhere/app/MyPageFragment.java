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

public class MyPageFragment extends Fragment {

    private static final String PREFS_NAME = "bookwhere_mypage_prefs";
    private static final String KEY_NOTIFICATION = "pref_notification";
    private static final String KEY_DARK_MODE = "pref_dark_mode";

    private FirebaseAuth mAuth;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mypage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        TextView tvWelcomeGreeting = view.findViewById(R.id.tv_welcome_greeting);
        TextView tvUserEmail = view.findViewById(R.id.tv_user_email);
        TextView tvUserUid = view.findViewById(R.id.tv_user_uid);
        LinearLayout rowMyInfo = view.findViewById(R.id.row_my_info);
        LinearLayout layoutMyInfoDetail = view.findViewById(R.id.layout_my_info_detail);
        TextView tvMyInfoArrow = view.findViewById(R.id.tv_my_info_arrow);
        LinearLayout rowLogout = view.findViewById(R.id.row_logout);

        MaterialSwitch switchNotification = view.findViewById(R.id.switch_notification);
        MaterialSwitch switchDarkMode = view.findViewById(R.id.switch_dark_mode);

        LinearLayout rowBugReport = view.findViewById(R.id.row_bug_report);
        LinearLayout rowTerms = view.findViewById(R.id.row_terms);
        TextView tvAppVersion = view.findViewById(R.id.tv_app_version);

        // 1. 로그인 사용자 닉네임 및 이메일 표시
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String email = currentUser.getEmail();
            String nickname = resolveNickname(currentUser);

            tvWelcomeGreeting.setText(nickname + "님, 안녕하세요.\n오늘도 가볍게 몰입하세요!");
            tvUserEmail.setText(email != null ? email : "이메일 정보 없음");
            tvUserUid.setText("회원 식별 코드: " + currentUser.getUid());
        } else {
            tvWelcomeGreeting.setText("사용자님, 안녕하세요.\n오늘도 가볍게 몰입하세요!");
            tvUserEmail.setText("로그인이 필요합니다");
        }

        // 내 정보 아코디언 토글
        rowMyInfo.setOnClickListener(v -> {
            if (layoutMyInfoDetail.getVisibility() == View.GONE) {
                layoutMyInfoDetail.setVisibility(View.VISIBLE);
                tvMyInfoArrow.setText("⌃");
            } else {
                layoutMyInfoDetail.setVisibility(View.GONE);
                tvMyInfoArrow.setText("⌄");
            }
        });

        // 2. 알림 설정 스위치
        boolean isNotiEnabled = prefs.getBoolean(KEY_NOTIFICATION, true);
        switchNotification.setChecked(isNotiEnabled);
        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFICATION, isChecked).apply();
            String msg = isChecked ? "도서 구독 알림이 켜졌습니다." : "도서 구독 알림이 꺼졌습니다.";
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });

        // 3. 다크모드 스위치
        boolean isDarkEnabled = prefs.getBoolean(KEY_DARK_MODE, false);
        switchDarkMode.setChecked(isDarkEnabled);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // 4. 버그 제보 (의견 보내기)
        rowBugReport.setOnClickListener(v -> showBugReportDialog());

        // 5. 이용약관
        rowTerms.setOnClickListener(v -> showTermsDialog());

        // 6. 앱 버전 표시
        tvAppVersion.setText("v" + getAppVersionName());

        // 7. 로그아웃
        rowLogout.setOnClickListener(v -> logoutUser());
    }

    /**
     * FirebaseUser의 displayName이 있으면 우선 사용하고,
     * 없으면 이메일(@ 앞부분)을 닉네임으로 반환합니다.
     */
    private String resolveNickname(FirebaseUser user) {
        if (!TextUtils.isEmpty(user.getDisplayName())) {
            return user.getDisplayName();
        }
        String email = user.getEmail();
        if (!TextUtils.isEmpty(email) && email.contains("@")) {
            return email.substring(0, email.indexOf("@"));
        }
        return "사용자";
    }

    private void showBugReportDialog() {
        EditText input = new EditText(requireContext());
        input.setHint("불편하신 점이나 개선 의견을 자유롭게 적어주세요.");
        input.setMinLines(3);

        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(requireContext());
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("버그 제보 및 의견 보내기")
                .setView(container)
                .setPositiveButton("보내기", (dialog, which) -> {
                    String feedback = input.getText().toString().trim();
                    if (TextUtils.isEmpty(feedback)) {
                        Toast.makeText(requireContext(), "내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "소중한 의견이 접수되었습니다. 감사합니다!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showTermsDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("전찾책 서비스 이용약관")
                .setMessage("제1조 (목적)\n본 약관은 '전찾책'이 제공하는 전자책 구독 플랫폼 통합 검색 및 위시리스트 서비스의 이용 조건과 절차를 규정합니다.\n\n"
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