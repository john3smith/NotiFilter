package com.local.noti_filter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Main-screen background photo: a normalized private copy plus a persisted scale mode. */
final class BackgroundPhoto {
    enum Mode {
        FIT_XY("fit_xy", "화면 채우기", ImageView.ScaleType.FIT_XY),
        CENTER_CROP("center_crop", "원본비율 크게 채우기", ImageView.ScaleType.CENTER_CROP),
        FIT_CENTER("fit_center", "원본비율 작게 채우기", ImageView.ScaleType.FIT_CENTER);

        final String key;
        final String label;
        final ImageView.ScaleType scaleType;
        Mode(String key, String label, ImageView.ScaleType scaleType) {
            this.key = key; this.label = label; this.scaleType = scaleType;
        }
        static Mode fromKey(String key) {
            for (Mode mode : values()) if (mode.key.equals(key)) return mode;
            return DEFAULT_MODE;
        }
    }

    static final Mode DEFAULT_MODE = Mode.CENTER_CROP;
    /** Picked documents are copied with this cap before decoding. */
    static final long MAX_SOURCE_BYTES = 40L * 1024 * 1024;
    /** Longest stored edge; bounds disk use and every later decode. */
    private static final int MAX_EDGE = 2048;
    private static final String PREFS = "background_photo";
    private static final String KEY_MODE = "mode";
    private static final String KEY_VERSION = "version";
    private static final Object LOCK = new Object();

    /** Failure with a message that can be shown to the user as-is. */
    static final class PhotoException extends IOException {
        PhotoException(String message) { super(message); }
        PhotoException(String message, Throwable cause) { super(message, cause); }
    }

    private BackgroundPhoto() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File directory(Context context) { return new File(context.getFilesDir(), "background"); }
    static File photoFile(Context context) { return new File(directory(context), "photo.img"); }
    private static File stagingDirectory(Context context) { return new File(directory(context), "staging"); }

    static boolean hasPhoto(Context context) { return photoFile(context).isFile(); }
    static Mode mode(Context context) { return Mode.fromKey(prefs(context).getString(KEY_MODE, DEFAULT_MODE.key)); }
    /** Changes on every committed edit so display caches can tell images apart. */
    static long version(Context context) { return prefs(context).getLong(KEY_VERSION, 0); }

    static final int DEFAULT_FRAME_TRANSPARENCY = 25;
    static final int DEFAULT_BUTTON_TRANSPARENCY = 15;
    private static final String KEY_FRAME_TRANSPARENCY = "frame_transparency";
    private static final String KEY_BUTTON_TRANSPARENCY = "button_transparency";

    private static int percent(int value) { return Math.max(0, Math.min(100, value)); }
    /** Transparency (0-100 %) of card, group and sort-panel background colors. */
    static int frameTransparency(Context context) {
        return percent(prefs(context).getInt(KEY_FRAME_TRANSPARENCY, DEFAULT_FRAME_TRANSPARENCY));
    }
    /** Transparency (0-100 %) of main-screen button and icon background colors. */
    static int buttonTransparency(Context context) {
        return percent(prefs(context).getInt(KEY_BUTTON_TRANSPARENCY, DEFAULT_BUTTON_TRANSPARENCY));
    }
    static void setFrameTransparency(Context context, int value) {
        prefs(context).edit().putInt(KEY_FRAME_TRANSPARENCY, percent(value)).apply();
    }
    static void setButtonTransparency(Context context, int value) {
        prefs(context).edit().putInt(KEY_BUTTON_TRANSPARENCY, percent(value)).apply();
    }

    /** Scales only the color's alpha channel; callers never fade whole views or their text. */
    static int withTransparency(int color, int transparency) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * (100 - percent(transparency)) / 100f);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Removes abandoned staged copies; call only when no dialog may still reference one. */
    static void clearStaging(Context context) {
        File[] files = stagingDirectory(context).listFiles();
        if (files != null) for (File file : files) //noinspection ResultOfMethodCallIgnored
            file.delete();
    }

    static boolean isStaged(Context context, File file) {
        return file != null && file.isFile() && stagingDirectory(context).equals(file.getParentFile());
    }

    /**
     * Copies, bounds, orients and re-encodes the picked image into a private staged file.
     * Runs off the main thread; the current background is never touched here.
     */
    static File prepare(Context context, Uri uri) throws IOException {
        File staging = stagingDirectory(context);
        if (!staging.isDirectory() && !staging.mkdirs()) throw new PhotoException("사진을 저장할 공간을 만들지 못했습니다.");
        File source = File.createTempFile("source", ".tmp", staging);
        File output = null;
        Bitmap bitmap = null;
        try {
            copyBounded(context, uri, source);
            bitmap = decode(source);
            output = File.createTempFile("photo", ".img", staging);
            try (FileOutputStream out = new FileOutputStream(output)) {
                boolean alpha = bitmap.hasAlpha();
                if (!bitmap.compress(alpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 90, out))
                    throw new PhotoException("사진을 저장하지 못했습니다.");
                out.getFD().sync();
            }
            File result = output;
            output = null;
            return result;
        } catch (PhotoException expected) {
            throw expected;
        } catch (IOException failed) {
            throw new PhotoException("사진을 저장하지 못했습니다. 저장 공간을 확인해 주세요.", failed);
        } finally {
            if (bitmap != null) bitmap.recycle();
            //noinspection ResultOfMethodCallIgnored
            source.delete();
            if (output != null) //noinspection ResultOfMethodCallIgnored
                output.delete();
        }
    }

    private static InputStream open(Context context, Uri uri) throws PhotoException {
        InputStream opened;
        try {
            opened = context.getContentResolver().openInputStream(uri);
        } catch (SecurityException | IllegalArgumentException | IOException denied) {
            throw new PhotoException("사진을 열 수 없습니다. 다시 선택해 주세요.", denied);
        }
        if (opened == null) throw new PhotoException("사진을 열 수 없습니다. 다시 선택해 주세요.");
        return opened;
    }

    private static void copyBounded(Context context, Uri uri, File target) throws IOException {
        try (InputStream in = open(context, uri); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            for (int read; (read = in.read(buffer)) != -1; ) {
                total += read;
                if (total > MAX_SOURCE_BYTES) throw new PhotoException("사진 파일이 너무 큽니다. 40MB 이하의 사진을 선택해 주세요.");
                if (Thread.currentThread().isInterrupted()) throw new PhotoException("사진 준비가 취소되었습니다.");
                out.write(buffer, 0, read);
            }
            if (total == 0) throw new PhotoException("사진을 읽을 수 없습니다. 다른 사진을 선택해 주세요.");
        }
    }

    private static Bitmap decode(File source) throws PhotoException {
        Bitmap decoded;
        try {
            decoded = Build.VERSION.SDK_INT >= 28 ? decodeModern(source) : decodeLegacy(source);
        } catch (OutOfMemoryError tooBig) {
            throw new PhotoException("사진이 너무 커서 불러올 수 없습니다. 다른 사진을 선택해 주세요.");
        } catch (IOException | RuntimeException unreadable) {
            throw new PhotoException("사진을 읽을 수 없습니다. 다른 사진을 선택해 주세요.", unreadable);
        }
        if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0)
            throw new PhotoException("사진을 읽을 수 없습니다. 다른 사진을 선택해 주세요.");
        try {
            return scaleToBound(decoded);
        } catch (OutOfMemoryError tooBig) {
            decoded.recycle();
            throw new PhotoException("사진이 너무 커서 불러올 수 없습니다. 다른 사진을 선택해 주세요.");
        }
    }

    /** ImageDecoder applies EXIF orientation itself; callers check SDK 28. */
    @android.annotation.SuppressLint("UseRequiresApi")
    @android.annotation.TargetApi(28)
    private static Bitmap decodeModern(File source) throws IOException {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source), (decoder, info, ignored) -> {
            int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetSampleSize(Math.max(1, (int) Math.ceil(longest / (double) MAX_EDGE)));
        });
    }

    private static Bitmap decodeLegacy(File source) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getPath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        int longest = Math.max(bounds.outWidth, bounds.outHeight);
        options.inSampleSize = 1;
        while (longest / (options.inSampleSize * 2) >= MAX_EDGE) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeFile(source.getPath(), options);
        if (bitmap == null) return null;
        Matrix matrix = orientation(source);
        if (matrix == null) return bitmap;
        Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (oriented != bitmap) bitmap.recycle();
        return oriented;
    }

    // Platform ExifInterface (API 24+) avoids adding an AndroidX dependency for API 26-27.
    @android.annotation.SuppressLint("ExifInterface")
    private static Matrix orientation(File source) {
        int value;
        try {
            value = new android.media.ExifInterface(source.getPath())
                    .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException | RuntimeException noExif) {
            return null;
        }
        Matrix m = new Matrix();
        switch (value) {
            case android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL: m.setScale(-1, 1); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_180: m.setRotate(180); break;
            case android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL: m.setScale(1, -1); break;
            case android.media.ExifInterface.ORIENTATION_TRANSPOSE: m.setRotate(90); m.postScale(-1, 1); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_90: m.setRotate(90); break;
            case android.media.ExifInterface.ORIENTATION_TRANSVERSE: m.setRotate(-90); m.postScale(-1, 1); break;
            case android.media.ExifInterface.ORIENTATION_ROTATE_270: m.setRotate(-90); break;
            default: return null;
        }
        return m;
    }

    private static Bitmap scaleToBound(Bitmap bitmap) {
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= MAX_EDGE) return bitmap;
        float scale = MAX_EDGE / (float) longest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * scale)),
                Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    /**
     * Applies a staged photo or removal together with the mode. The rename replaces the
     * current file atomically, so a failure leaves the previous photo in place.
     */
    @android.annotation.SuppressLint("ApplySharedPref") // The caller reports the persisted result.
    static boolean commit(Context context, File staged, boolean remove, Mode mode) {
        synchronized (LOCK) {
            File photo = photoFile(context);
            if (staged != null) {
                if (!isStaged(context, staged) || !staged.renameTo(photo)) return false;
            } else if (remove && photo.exists() && !photo.delete()) {
                return false;
            }
            SharedPreferences.Editor editor = prefs(context).edit();
            boolean cleared = staged == null && remove;
            editor.putString(KEY_MODE, (cleared ? DEFAULT_MODE : mode).key);
            // A mode-only change keeps the decoded display bitmap valid.
            if (staged != null || remove) editor.putLong(KEY_VERSION, version(context) + 1);
            editor.commit();
            return true;
        }
    }

    /** Sampled decode of the stored copy sized to cover the given screen; null if unreadable. */
    static Bitmap loadForDisplay(Context context, int width, int height) {
        File photo = photoFile(context);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(photo.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            // Keep the longest edge at least screen-sized so cropping modes stay sharp.
            int target = Math.max(1, Math.max(width, height));
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / (options.inSampleSize * 2) >= target) options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(photo.getPath(), options);
        } catch (OutOfMemoryError | RuntimeException unreadable) {
            return null;
        }
    }
}
