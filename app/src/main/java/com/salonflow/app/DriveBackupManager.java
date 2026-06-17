package com.salonflow.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.AlarmManager;
import android.app.PendingIntent;

import org.json.JSONArray;
import org.json.JSONObject;

public class DriveBackupManager {
    private static final String TAG = "DriveBackup";
    private static final String DRIVE_FOLDER_NAME = "SalonFlowBackups";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";
    private static final String DRIVE_API = "https://www.googleapis.com/drive/v3";
    private static final String DRIVE_UPLOAD = "https://www.googleapis.com/upload/drive/v3";

    private final Context context;
    private final android.app.Activity activity;
    private final GoogleSignInClient signInClient;

    public interface DriveCallback {
        void onResult(boolean success, String message);
    }

    public interface FileListCallback {
        void onResult(List<DriveFileInfo> files, String error);
    }

    public static class DriveFileInfo implements Comparable<DriveFileInfo> {
        public final String id;
        public final String name;
        public final long modifiedTime;

        DriveFileInfo(String id, String name, long modifiedTime) {
            this.id = id;
            this.name = name;
            this.modifiedTime = modifiedTime;
        }

        @Override
        public int compareTo(DriveFileInfo o) {
            return Long.compare(o.modifiedTime, modifiedTime);
        }
    }

    public DriveBackupManager(Context context) {
        this.context = context.getApplicationContext();
        this.activity = context instanceof android.app.Activity ? (android.app.Activity) context : null;
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(context, options);
    }

    public GoogleSignInAccount getSignedInAccount() {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    public boolean isSignedIn() {
        return getSignedInAccount() != null;
    }

    public Intent getSignInIntent() {
        return signInClient.getSignInIntent();
    }

    public void signOut() {
        signInClient.signOut();
    }

    public String getSignedInEmail() {
        GoogleSignInAccount account = getSignedInAccount();
        return account != null ? account.getEmail() : null;
    }

    public void uploadBackup(File databaseFile, String pin, DriveCallback callback) {
        new Thread(() -> {
            File temp = null;
            try {
                String token = getAccessToken();
                if (token == null) {
                    callback.onResult(false, "Not signed in to Google Drive");
                    return;
                }

                temp = File.createTempFile("drive-upload-", ".tmp", context.getCacheDir());
                DataCipher.encryptWithPin(databaseFile, temp, pin);

                String folderId = ensureFolderExists(token);

                String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new java.util.Date());
                String fileName = "salonflow-" + stamp + ".db.pin";
                String existingId = findFile(token, folderId, fileName);
                if (existingId != null) deleteFile(token, existingId);

                uploadFile(token, folderId, fileName, temp);
                callback.onResult(true, "Backup uploaded to Drive");
            } catch (Exception e) {
                Log.e(TAG, "Upload failed", e);
                callback.onResult(false, "Upload failed: " + e.getMessage());
            } finally {
                if (temp != null) temp.delete();
            }
        }).start();
    }

    public void uploadBackupCached(File databaseFile, byte[] keyBytes, byte[] keySalt, DriveCallback callback) {
        new Thread(() -> {
            File temp = null;
            try {
                String token = getAccessToken();
                if (token == null) {
                    callback.onResult(false, "Not signed in to Google Drive");
                    return;
                }

                temp = File.createTempFile("drive-upload-", ".tmp", context.getCacheDir());
                DataCipher.encryptWithCachedKey(databaseFile, temp, keyBytes, keySalt);

                String folderId = ensureFolderExists(token);

                String stamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new java.util.Date());
                String fileName = "salonflow-" + stamp + ".db.pin";
                String existingId = findFile(token, folderId, fileName);
                if (existingId != null) deleteFile(token, existingId);

                uploadFile(token, folderId, fileName, temp);
                deleteOldDriveBackups(token, folderId, fileName);
                callback.onResult(true, "Backup uploaded to Drive");
            } catch (Exception e) {
                Log.e(TAG, "Upload (cached) failed", e);
                callback.onResult(false, e.getMessage());
            } finally {
                if (temp != null) temp.delete();
            }
        }).start();
    }

    public static void scheduleRetry(Context context, int attempt) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, DriveRetryReceiver.class);
        intent.putExtra("retry_attempt", attempt);
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 3000 + attempt, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long trigger = System.currentTimeMillis() + 5 * 60 * 1000;
        am.set(AlarmManager.RTC_WAKEUP, trigger, pi);
    }

    public void listBackups(FileListCallback callback) {
        new Thread(() -> {
            try {
                String token = getAccessToken();
                if (token == null) {
                    callback.onResult(null, "Not signed in to Google Drive");
                    return;
                }

                String folderId = ensureFolderExists(token);

                String query = URLEncoder.encode("'" + folderId + "' in parents and trashed = false", "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(
                        DRIVE_API + "/files?q=" + query + "&orderBy=modifiedTime desc&fields=files(id,name,modifiedTime)"
                ).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code != 200) {
                    callback.onResult(null, "Drive API error: " + code);
                    return;
                }

                String body = readStream(conn.getInputStream());
                List<DriveFileInfo> files = parseFileList(body);
                callback.onResult(files, null);
            } catch (Exception e) {
                Log.e(TAG, "List failed", e);
                callback.onResult(null, "List failed: " + e.getMessage());
            }
        }).start();
    }

    public void downloadBackup(String fileId, File destDatabaseFile, String pin, DriveCallback callback) {
        new Thread(() -> {
            File temp = null;
            try {
                String token = getAccessToken();
                if (token == null) {
                    callback.onResult(false, "Not signed in to Google Drive");
                    return;
                }

                temp = File.createTempFile("drive-download-", ".tmp", context.getCacheDir());

                HttpURLConnection conn = (HttpURLConnection) new URL(
                        DRIVE_API + "/files/" + fileId + "?alt=media"
                ).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code != 200) {
                    callback.onResult(false, "Download failed: " + code);
                    return;
                }

                File parent = temp.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (InputStream in = conn.getInputStream();
                     OutputStream out = new FileOutputStream(temp)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                }

                DataCipher.decryptWithPin(temp, destDatabaseFile, pin);
                callback.onResult(true, "Backup restored from Drive");
            } catch (Exception e) {
                Log.e(TAG, "Download failed", e);
                callback.onResult(false, "Download failed: " + e.getMessage());
            } finally {
                if (temp != null) temp.delete();
            }
        }).start();
    }

    public void deleteDriveBackup(String fileId, DriveCallback callback) {
        new Thread(() -> {
            try {
                String token = getAccessToken();
                if (token == null) {
                    callback.onResult(false, "Not signed in");
                    return;
                }
                HttpURLConnection conn = (HttpURLConnection) new URL(
                        DRIVE_API + "/files/" + fileId
                ).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestMethod("DELETE");
                conn.getResponseCode();
                callback.onResult(true, "Deleted from Drive");
            } catch (Exception e) {
                Log.e(TAG, "Delete failed", e);
                callback.onResult(false, "Delete failed: " + e.getMessage());
            }
        }).start();
    }

    private String getAccessToken() throws Exception {
        GoogleSignInAccount account = getSignedInAccount();
        if (account == null) return null;
        AccountManager am = AccountManager.get(context);
        Account acct = new Account(account.getEmail(), "com.google");
        try {
            AccountManagerFuture<Bundle> future = am.getAuthToken(
                    acct, "oauth2:" + DRIVE_SCOPE, null,
                    activity, null, null
            );
            Bundle bundle = future.getResult();
            String token = bundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (token == null) {
                throw new Exception("Got null token from AccountManager");
            }
            return token;
        } catch (android.accounts.OperationCanceledException e) {
            throw new Exception("You cancelled the Drive permission request. Try again and grant access.");
        } catch (android.accounts.AuthenticatorException e) {
            String em = e.getMessage() != null ? e.getMessage() : "";
            if (em.contains("third_party") || em.contains("DEVELOPER_ERROR") || em.contains("12500")) {
                throw new Exception("Google Drive requires OAuth setup. Create a Google Cloud project, enable the Drive API, and add an OAuth 2.0 Android client ID with your app's package name and SHA-1 fingerprint. See HANDOVER.md for details.");
            }
            throw new Exception("Google Play Services auth error: " + em);
        }
    }

    private String ensureFolderExists(String token) throws Exception {
        String query = URLEncoder.encode(
                "name='" + DRIVE_FOLDER_NAME + "' and mimeType='application/vnd.google-apps.folder' and trashed=false",
                "UTF-8"
        );
        HttpURLConnection conn = (HttpURLConnection) new URL(
                DRIVE_API + "/files?q=" + query + "&fields=files(id)"
        ).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestMethod("GET");

        int code = conn.getResponseCode();
        Log.d(TAG, "Folder search response: " + code);
        if (code != 200) {
            throw new Exception(
                    "Drive API search error (" + code + "): " + readStream(conn.getErrorStream())
            );
        }

        String body = readStream(conn.getInputStream());
        Log.d(TAG, "Folder search body: " + body);
        String existing = extractFirstFileId(body);
        if (existing != null) return existing;

        String metadata = "{\"name\":\"" + DRIVE_FOLDER_NAME + "\",\"mimeType\":\"application/vnd.google-apps.folder\"}";
        conn = (HttpURLConnection) new URL(DRIVE_API + "/files?fields=id").openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(metadata.getBytes("UTF-8"));
        }

        code = conn.getResponseCode();
        Log.d(TAG, "Folder creation response: " + code);
        if (code != 200 && code != 201) {
            throw new Exception(
                    "Drive API folder creation error (" + code + "): " + readStream(conn.getErrorStream())
            );
        }

        body = readStream(conn.getInputStream());
        Log.d(TAG, "Folder creation body: " + body);
        String id = extractId(body);
        if (id == null) {
            throw new Exception("Folder created but response had no id field: " + body);
        }
        return id;
    }

    private String findFile(String token, String folderId, String name) throws Exception {
        String query = URLEncoder.encode(
                "name='" + name + "' and '" + folderId + "' in parents and trashed=false",
                "UTF-8"
        );
        HttpURLConnection conn = (HttpURLConnection) new URL(
                DRIVE_API + "/files?q=" + query + "&fields=files(id)"
        ).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestMethod("GET");
        if (conn.getResponseCode() != 200) {
            throw new Exception(
                    "Drive API search error (" + conn.getResponseCode() + "): " + readStream(conn.getErrorStream())
            );
        }
        return extractFirstFileId(readStream(conn.getInputStream()));
    }

    private void deleteFile(String token, String fileId) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(
                DRIVE_API + "/files/" + fileId
        ).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestMethod("DELETE");
        conn.getResponseCode();
    }

    private void uploadFile(String token, String folderId, String fileName, File file) throws Exception {
        String boundary = "Boundary_" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(
                DRIVE_UPLOAD + "/files?uploadType=multipart"
        ).openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            String metadata = "{\"name\":\"" + fileName + "\",\"parents\":[\"" + folderId + "\"]}";
            os.write(("--" + boundary + "\r\n").getBytes());
            os.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".getBytes());
            os.write(metadata.getBytes("UTF-8"));
            os.write("\r\n".getBytes());

            os.write(("--" + boundary + "\r\n").getBytes());
            os.write("Content-Type: application/octet-stream\r\n\r\n".getBytes());
            try (FileInputStream fin = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fin.read(buf)) != -1) os.write(buf, 0, len);
            }
            os.write(("\r\n--" + boundary + "--\r\n").getBytes());
        }

        int code = conn.getResponseCode();
        if (code != 200 && code != 201) {
            String errBody = readStream(conn.getErrorStream());
            throw new Exception("Upload failed (" + code + "): " + errBody);
        }
    }

    private List<DriveFileInfo> parseFileList(String body) {
        List<DriveFileInfo> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        try {
            JSONArray arr = new JSONObject(body).optJSONArray("files");
            if (arr == null) return result;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", null);
                String name = obj.optString("name", null);
                if (id == null || name == null) continue;
                long time = 0;
                String modified = obj.optString("modifiedTime", null);
                if (modified != null) {
                    try {
                        time = sdf.parse(modified).getTime();
                    } catch (ParseException ignored) {}
                }
                result.add(new DriveFileInfo(id, name, time));
            }
        } catch (Exception e) {
            Log.e(TAG, "parseFileList failed", e);
        }
        return result;
    }

    private String extractId(String body) {
        try {
            String id = new JSONObject(body).optString("id", null);
            if (id != null && !id.isEmpty()) return id;
        } catch (Exception ignored) {}
        return null;
    }

    private String extractFirstFileId(String body) {
        try {
            JSONArray files = new JSONObject(body).optJSONArray("files");
            if (files != null && files.length() > 0) {
                return files.getJSONObject(0).optString("id", null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void deleteOldDriveBackups(String token, String folderId, String keepFileName) {
        try {
            String query = URLEncoder.encode(
                    "'" + folderId + "' in parents and trashed = false",
                    "UTF-8"
            );
            HttpURLConnection conn = (HttpURLConnection) new URL(
                    DRIVE_API + "/files?q=" + query + "&fields=files(id,name)"
            ).openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return;

            String body = readStream(conn.getInputStream());
            JSONArray files = new JSONObject(body).optJSONArray("files");
            if (files == null) return;

            for (int i = 0; i < files.length(); i++) {
                JSONObject obj = files.getJSONObject(i);
                String id = obj.optString("id", null);
                String name = obj.optString("name", null);
                if (id != null && name != null && !name.equals(keepFileName)) {
                    deleteFile(token, id);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete old Drive backups", e);
        }
    }

    private String readStream(InputStream in) throws Exception {
        if (in == null) return "(no error body)";
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }
}
