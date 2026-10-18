package app.xarena.esports;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ApplicationExitInfo;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.*;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/* XARENA safe shell: one WebView, no push/alarm/notifier/Firebase. */
public class MainActivity extends Activity {
    static final String HOME = "file:///android_asset/www/index.html";
    static final String MANIFEST = "https://xarenaprivate.github.io/xarena/xarena-version.json";
    static final int FILE_REQ = 5001, MIC_REQ = 5002;
    WebView web;
    ValueCallback<Uri[]> fileCb;
    PermissionRequest pendingPerm;
    SharedPreferences sp;

    int versionCode() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode; } catch (Exception e) { return 0; } }
    String versionName() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; } }

    void note(String k, String v) { try { sp.edit().putString(k, v).apply(); } catch (Throwable t) {} }

    void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try { StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
                    sp.edit().putString("java_crash", "Thread " + t.getName() + " at " + System.currentTimeMillis() + "\n" + sw).commit(); } catch (Throwable x) {}
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /* On next launch: show what ended the previous session (Java crash and/or Android's exit reason). */
    void showPreviousSession() {
        try {
            StringBuilder r = new StringBuilder();
            String jc = sp.getString("java_crash", null);
            if (jc != null) { r.append("JAVA CRASH:\n").append(jc).append("\n"); sp.edit().remove("java_crash").apply(); }
            String rg = sp.getString("renderer", null);
            if (rg != null) { r.append("WEBVIEW: ").append(rg).append("\n"); sp.edit().remove("renderer").apply(); }
            if (Build.VERSION.SDK_INT >= 30) {
                android.app.ActivityManager m = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                List<ApplicationExitInfo> l = m.getHistoricalProcessExitReasons(getPackageName(), 0, 5);
                long seen = sp.getLong("exit_seen", 0); long newest = seen;
                for (ApplicationExitInfo i : l) {
                    if (i.getTimestamp() <= seen) continue;
                    newest = Math.max(newest, i.getTimestamp());
                    int rs = i.getReason();
                    if (rs == ApplicationExitInfo.REASON_USER_REQUESTED || rs == ApplicationExitInfo.REASON_EXIT_SELF || rs == ApplicationExitInfo.REASON_USER_STOPPED) continue;
                    r.append("ANDROID EXIT: reason=").append(reasonName(rs)).append(" importance=").append(i.getImportance())
                     .append(" status=").append(i.getStatus()).append(" at ").append(new java.util.Date(i.getTimestamp()))
                     .append("\n  desc=").append(i.getDescription()).append(" pssKb=").append(i.getPss()).append(" rssKb=").append(i.getRss()).append("\n");
                }
                sp.edit().putLong("exit_seen", newest).apply();
            }
            String js = sp.getString("js_errors", "");
            if (r.length() == 0) return;
            final String report = "XARENA " + versionName() + " (" + versionCode() + ") " + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.RELEASE + "\n" + r + (js.isEmpty() ? "" : "\nLast JS errors:\n" + js);
            new AlertDialog.Builder(this).setTitle("Last session ended unexpectedly").setMessage(report)
                .setPositiveButton("Share", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, report);
                    startActivity(Intent.createChooser(i, "Send report")); } })
                .setNeutralButton("Copy", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("report", report));
                    Toast.makeText(MainActivity.this, "Copied", Toast.LENGTH_SHORT).show(); } })
                .setNegativeButton("Close", null).show();
        } catch (Throwable t) {}
    }
    static class ActivityManager_ {}
    static String reasonName(int r) {
        switch (r) { case 1: return "EXIT_SELF"; case 2: return "SIGNALED(killed by system signal)"; case 3: return "LOW_MEMORY(killed for RAM)";
            case 4: return "CRASH(Java)"; case 5: return "CRASH_NATIVE"; case 6: return "ANR"; case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE"; case 9: return "EXCESSIVE_RESOURCE_USAGE"; case 10: return "USER_REQUESTED"; case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED"; case 13: return "OTHER"; case 14: return "FREEZER"; case 15: return "PACKAGE_STATE_CHANGE"; case 16: return "PACKAGE_UPDATED"; }
        return "UNKNOWN(" + r + ")";
    }

    class Bridge {
        @JavascriptInterface public String getVersionName() { return versionName() + " (build " + versionCode() + ")"; }
        @JavascriptInterface public void saveAuth(String a, String b) {}
        @JavascriptInterface public void clearAuth() {}
        @JavascriptInterface public void logJsError(String m) {
            try { String cur = sp.getString("js_errors", ""); cur = (cur + "\n" + m); if (cur.length() > 3000) cur = cur.substring(cur.length() - 3000); note("js_errors", cur); } catch (Throwable t) {}
        }
        @JavascriptInterface public void checkUpdate() { runOnUiThread(new Runnable() { public void run() { checkForUpdate(true); } }); }
        @JavascriptInterface public String saveImage(String b64, String name) {
            try {
                byte[] data = Base64.decode(b64, Base64.DEFAULT);
                if (name == null || name.isEmpty()) name = "xarena.png";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues(); v.put(MediaStore.Images.Media.DISPLAY_NAME, name); v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XARENA");
                    Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    OutputStream o = getContentResolver().openOutputStream(u); o.write(data); o.close();
                } else {
                    File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "XARENA"); d.mkdirs();
                    FileOutputStream o = new FileOutputStream(new File(d, name)); o.write(data); o.close();
                }
                return "ok";
            } catch (Throwable t) { return "ERR " + t.getMessage(); }
        }
    }

    void checkForUpdate(final boolean manual) {
        new Thread(new Runnable() { public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(MANIFEST + "?t=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream())); StringBuilder sb = new StringBuilder(); String ln;
                while ((ln = br.readLine()) != null) sb.append(ln);
                final JSONObject j = new JSONObject(sb.toString());
                final int code = j.optInt("versionCode", j.optInt("code", 0));
                runOnUiThread(new Runnable() { public void run() {
                    try {
                        if (code > versionCode()) {
                            new AlertDialog.Builder(MainActivity.this).setTitle("Update available: v" + j.optString("version"))
                                .setMessage(j.optString("notes", "") + "\n\nOpens the download in your browser, then tap the downloaded file to install.")
                                .setPositiveButton("Download", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(j.optString("url")))); } })
                                .setNegativeButton("Later", null).show();
                        } else if (manual) Toast.makeText(MainActivity.this, "You have the latest version (" + versionName() + ")", Toast.LENGTH_LONG).show();
                    } catch (Throwable t) {}
                } });
            } catch (Throwable t) { if (manual) runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "Could not check for updates.", Toast.LENGTH_SHORT).show(); } }); }
        } }).start();
    }

    @Override protected void onCreate(Bundle saved) {
        sp = getSharedPreferences("xarena_shell", MODE_PRIVATE);
        installCrashHandler();
        super.onCreate(saved);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0A0104"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                note("renderer", "renderer gone, didCrash=" + d.didCrash() + " (false = killed by Android for memory)");
                try { ((android.view.ViewGroup) v.getParent()).removeView(v); v.destroy(); } catch (Throwable t) {}
                Toast.makeText(MainActivity.this, "WebView restarted: " + (d.didCrash() ? "crashed" : "killed for memory"), Toast.LENGTH_LONG).show();
                recreate(); return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                String sch = req.getUrl().getScheme(), host = req.getUrl().getHost();
                if ("file".equals(sch)) return false;
                if (host != null && host.endsWith(".supabase.co")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, req.getUrl())); } catch (Throwable t) {}
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest rq) {
                boolean audio = false;
                for (String r : rq.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
                if (!audio) { rq.deny(); return; }
                if (checkSelfPermission("android.permission.RECORD_AUDIO") == 0) {
                    final String[] g = {PermissionRequest.RESOURCE_AUDIO_CAPTURE};
                    runOnUiThread(new Runnable() { public void run() { rq.grant(g); } });
                } else { pendingPerm = rq; runOnUiThread(new Runnable() { public void run() { requestPermissions(new String[]{"android.permission.RECORD_AUDIO"}, MIC_REQ); } }); }
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(i, FILE_REQ);
                } catch (Throwable t) { fileCb = null; return false; }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "XarenaApp");
        setContentView(web);
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME);
        showPreviousSession();
        Toast.makeText(this, "XARENA v" + versionName() + " (build " + versionCode() + ")", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() { public void run() { checkForUpdate(false); } }, 4000);
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); try { web.saveState(o); } catch (Throwable t) {} }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != FILE_REQ || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK && data != null) {
            if (data.getClipData() != null) { out = new Uri[data.getClipData().getItemCount()]; for (int i = 0; i < out.length; i++) out[i] = data.getClipData().getItemAt(i).getUri(); }
            else if (data.getData() != null) out = new Uri[]{data.getData()};
        }
        fileCb.onReceiveValue(out); fileCb = null;
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        if (req == MIC_REQ && pendingPerm != null) {
            if (r.length > 0 && r[0] == 0) pendingPerm.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); else pendingPerm.deny();
            pendingPerm = null;
        }
    }

    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
