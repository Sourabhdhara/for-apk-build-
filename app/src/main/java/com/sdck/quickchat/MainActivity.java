package com.sdck.quickchat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "https://sdck.pythonanywhere.com";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_CHOOSER_REQUEST_CODE = 200;
    private static final int CAMERA_REQUEST_CODE = 300;

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;

    // File upload callbacks
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    // Permissions needed for the app
    private static final String[] REQUIRED_PERMISSIONS;
    static {
        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.CAMERA);
        perms.add(Manifest.permission.RECORD_AUDIO);
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        perms.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }
        REQUIRED_PERMISSIONS = perms.toArray(new String[0]);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView      = findViewById(R.id.webView);
        swipeRefresh = findViewById(R.id.swipeRefresh);

        setupWebView();
        requestAllPermissions();

        swipeRefresh.setColorSchemeColors(0xFF6C63FF);
        swipeRefresh.setOnRefreshListener(() -> {
            webView.reload();
            swipeRefresh.setRefreshing(false);
        });

        webView.loadUrl(APP_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Core
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // File access
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
        }

        // Cache
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAppCacheEnabled(true);

        // Viewport / zoom
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // User agent — identifies as mobile browser
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE +
            "; " + Build.MODEL + ") AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 QuickChatApp/1.0"
        );

        webView.setWebViewClient(new QuickChatWebViewClient());
        webView.setWebChromeClient(new QuickChatWebChromeClient());
        WebView.setWebContentsDebuggingEnabled(false);
    }

    // ─────────────────────────────────────────
    //  WebViewClient — handles page navigation
    // ─────────────────────────────────────────
    private class QuickChatWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            // Keep navigation inside the app for our domain
            if (url.startsWith("https://sdck.pythonanywhere.com") ||
                url.startsWith("http://sdck.pythonanywhere.com")) {
                return false; // let WebView handle it
            }
            // Open external links in browser
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            swipeRefresh.setRefreshing(false);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode,
                                    String description, String failingUrl) {
            // Show a friendly offline page
            String offlinePage = "<html><body style='"
                + "margin:0;display:flex;align-items:center;justify-content:center;"
                + "height:100vh;background:#1a1a2e;font-family:sans-serif;color:#fff;flex-direction:column;gap:16px'>"
                + "<svg width='64' height='64' fill='none' stroke='#6c63ff' stroke-width='2'>"
                + "<circle cx='32' cy='32' r='28'/>"
                + "<line x1='20' y1='20' x2='44' y2='44'/>"
                + "</svg>"
                + "<h2 style='margin:0'>No Connection</h2>"
                + "<p style='color:rgba(255,255,255,0.5);margin:0'>Check your internet and pull down to retry</p>"
                + "</body></html>";
            view.loadData(offlinePage, "text/html", "UTF-8");
        }
    }

    // ─────────────────────────────────────────
    //  WebChromeClient — camera, mic, files, geo
    // ─────────────────────────────────────────
    private class QuickChatWebChromeClient extends WebChromeClient {

        // ── Microphone / Camera permission from JS (getUserMedia) ──
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            String[] resources = request.getResources();
            List<String> granted = new ArrayList<>();
            for (String res : resources) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res) &&
                    ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    granted.add(res);
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res) &&
                    ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    granted.add(res);
                }
                // Protected media (screen capture etc.)
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(res)) {
                    granted.add(res);
                }
            }
            if (!granted.isEmpty()) {
                request.grant(granted.toArray(new String[0]));
            } else {
                // Request missing permissions then retry
                requestAllPermissions();
                request.deny();
            }
        }

        // ── Geolocation ──
        @Override
        public void onGeolocationPermissionsShowPrompt(
                String origin, GeolocationPermissions.Callback callback) {
            boolean hasLocation = ContextCompat.checkSelfPermission(
                MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            callback.invoke(origin, hasLocation, false);
            if (!hasLocation) requestAllPermissions();
        }

        // ── File chooser (upload profile pic, attachments) ──
        @Override
        public boolean onShowFileChooser(WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams) {
            // Cancel any previous callback
            if (MainActivity.this.filePathCallback != null) {
                MainActivity.this.filePathCallback.onReceiveValue(null);
            }
            MainActivity.this.filePathCallback = filePathCallback;

            showFileChooserDialog(fileChooserParams);
            return true;
        }

        // ── JS alert / confirm / prompt dialogs ──
        @Override
        public boolean onJsAlert(WebView view, String url,
                String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> result.confirm())
                .setOnCancelListener(d -> result.cancel())
                .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url,
                String message, JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton("OK",     (d, w) -> result.confirm())
                .setNegativeButton("Cancel", (d, w) -> result.cancel())
                .setOnCancelListener(d -> result.cancel())
                .show();
            return true;
        }

        // ── Console log passthrough (helps debugging) ──
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            return true;
        }
    }

    // ─────────────────────────────────────────
    //  File chooser — camera + gallery + files
    // ─────────────────────────────────────────
    private void showFileChooserDialog(WebChromeClient.FileChooserParams params) {
        String[] acceptTypes = params.getAcceptTypes();
        boolean isImage = false, isVideo = false, isAudio = false, isAny = true;

        if (acceptTypes != null && acceptTypes.length > 0) {
            isAny = false;
            for (String type : acceptTypes) {
                if (type.startsWith("image")) isImage = true;
                if (type.startsWith("video")) isVideo = true;
                if (type.startsWith("audio")) isAudio = true;
                if (type.equals("*/*") || type.isEmpty()) isAny = true;
            }
        }

        List<String> options = new ArrayList<>();
        if (isImage || isAny) options.add("📷  Take Photo");
        if (isVideo || isAny) options.add("🎥  Record Video");
        if (isImage || isAny) options.add("🖼️  Choose from Gallery");
        if (isAudio || isAny) options.add("🎵  Choose Audio File");
        options.add("📄  Choose Any File");

        new AlertDialog.Builder(this)
            .setTitle("Attach File")
            .setItems(options.toArray(new CharSequence[0]), (dialog, which) -> {
                String choice = options.get(which);
                if (choice.startsWith("📷")) {
                    openCamera(false);
                } else if (choice.startsWith("🎥")) {
                    openCamera(true);
                } else if (choice.startsWith("🖼️")) {
                    openGallery("image/*");
                } else if (choice.startsWith("🎵")) {
                    openGallery("audio/*");
                } else {
                    openGallery("*/*");
                }
            })
            .setNegativeButton("Cancel", (d, w) -> {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            })
            .setOnCancelListener(d -> {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }
            })
            .show();
    }

    private void openCamera(boolean video) {
        try {
            File photoFile = createImageFile();
            cameraImageUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider", photoFile);

            Intent intent = new Intent(video
                ? MediaStore.ACTION_VIDEO_CAPTURE
                : MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_REQUEST_CODE);
        } catch (IOException e) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
                filePathCallback = null;
            }
        }
    }

    private void openGallery(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(
            Intent.createChooser(intent, "Choose File"),
            FILE_CHOOSER_REQUEST_CODE);
    }

    private File createImageFile() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile("QC_" + stamp, ".jpg", storageDir);
    }

    // ─────────────────────────────────────────
    //  Activity results (file / camera)
    // ─────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (filePathCallback == null) return;

        Uri[] results = null;

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE && cameraImageUri != null) {
                results = new Uri[]{cameraImageUri};
            } else if (requestCode == FILE_CHOOSER_REQUEST_CODE && data != null) {
                if (data.getClipData() != null) {
                    // Multiple files
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        cameraImageUri = null;
    }

    // ─────────────────────────────────────────
    //  Permission handling
    // ─────────────────────────────────────────
    private void requestAllPermissions() {
        List<String> missing = new ArrayList<>();
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(perm);
            }
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Reload so the page can retry any blocked API calls
        if (requestCode == PERMISSION_REQUEST_CODE) {
            webView.reload();
        }
    }

    // ─────────────────────────────────────────
    //  Back button — navigate inside WebView
    // ─────────────────────────────────────────
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }
}
