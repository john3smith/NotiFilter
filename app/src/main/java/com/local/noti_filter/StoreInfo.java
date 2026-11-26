package com.local.noti_filter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.ViewGroup;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Offline privacy and permission disclosure. No remote content or JavaScript. */
public final class StoreInfo {
    private StoreInfo() { }
    public static void addButton(Activity activity, ViewGroup parent, Consumer<AlertDialog> showDialog) {
        Button button = new Button(activity);
        button.setText("개인정보·권한 안내");
        button.setAllCaps(false);
        button.setOnClickListener(view -> show(activity, showDialog));
        parent.addView(button, new ViewGroup.LayoutParams(-1, -2));
    }
    public static void show(Activity activity, Consumer<AlertDialog> showDialog) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String text;
        try (InputStream input = activity.getAssets().open("store-privacy.txt");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            text = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException error) {
            text = "개인정보 안내를 불러올 수 없습니다. 앱 배포 페이지의 안내를 확인해 주세요.";
        }
        text += "\n\n판매자: " + activity.getString(R.string.store_publisher_name)
                + "\n고객지원: " + activity.getString(R.string.store_support_email);
        TextView content = new TextView(activity);
        int padH = ModernDialog.dp(activity, 24);
        int padV = ModernDialog.dp(activity, 12);
        content.setPadding(padH, padV, padH, padV);
        content.setTextSize(14);
        content.setTextColor(ModernDialog.BODY);
        content.setLineSpacing(ModernDialog.dp(activity, 3), 1f);
        content.setText(text);
        content.setTextIsSelectable(true);
        Linkify.addLinks(content, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        content.setMovementMethod(LinkMovementMethod.getInstance());
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        AlertDialog.Builder dialog = ModernDialog.builder(activity, "개인정보·권한 안내",
                        "기기 내부에서만 알림을 처리하며 외부로 전송하지 않습니다.")
                .setView(scroll).setPositiveButton("닫기", null);
        String url = activity.getString(R.string.store_privacy_url);
        if (url.startsWith("https://")) dialog.setNeutralButton("웹 방침 보기", (d, which) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (ActivityNotFoundException ignored) { android.widget.Toast.makeText(activity, "링크를 열 브라우저가 없습니다.", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        dialog.setNegativeButton("오픈소스 고지", (d, which) -> {
            d.dismiss();
            showNotices(activity, showDialog);
        });
        showDialog.accept(dialog.create());
    }
    private static void showNotices(Activity activity, Consumer<AlertDialog> showDialog) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        String text;
        try (InputStream input = activity.getAssets().open("store-notices.txt");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            text = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (java.io.IOException error) { text = "고지를 불러올 수 없습니다. 배포 저장소의 store 폴더를 확인해 주세요."; }
        TextView view = new TextView(activity); view.setText(text); view.setTextSize(13);
        view.setTextColor(ModernDialog.BODY);
        view.setLineSpacing(ModernDialog.dp(activity, 2), 1f);
        int padH = ModernDialog.dp(activity, 24);
        int padV = ModernDialog.dp(activity, 12);
        view.setPadding(padH, padV, padH, padV); view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(activity); scroll.addView(view);
        showDialog.accept(ModernDialog.builder(activity, "오픈소스 고지", null).setView(scroll)
                .setPositiveButton("닫기", null).create());
    }

}
