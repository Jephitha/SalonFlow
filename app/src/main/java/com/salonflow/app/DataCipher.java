package com.salonflow.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

class DataCipher {
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "salonflow-data-key";
    private static final String DATA_KEY_SALT = "SalonFlowDataProtectionKeyV1";

    private final Context context;
    private SecretKey pinKey;

    DataCipher(Context context) {
        this.context = context.getApplicationContext();
    }

    void setPinKey(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            pinKey = null;
            return;
        }
        try {
            pinKey = deriveKey(pin, DATA_KEY_SALT.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e("DataCipher", "Failed to derive PIN key", e);
            pinKey = null;
        }
    }

    void clearPinKey() {
        pinKey = null;
    }

    boolean hasPinKey() {
        return pinKey != null;
    }

    String encryptString(String value) {
        if (value == null || value.trim().isEmpty()) return value == null ? "" : value;
        try {
            SecretKey key = pinKey != null ? pinKey : keystoreKey();
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = pack(iv, encrypted);
            return "enc:" + Base64.encodeToString(packed, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e("DataCipher", "encryptString failed", e);
            throw new RuntimeException("Encryption failed for value of length " + value.length(), e);
        }
    }

    String decryptString(String value) {
        if (value == null || value.trim().isEmpty()) return value == null ? "" : value;
        if (!value.startsWith("enc:")) return value;
        byte[] packed;
        try {
            packed = Base64.decode(value.substring(4), Base64.NO_WRAP);
        } catch (Exception e) {
            return value;
        }
        // Try PIN key first, then Keystore fallback
        if (pinKey != null) {
            try {
                return decryptWithKey(packed, pinKey);
            } catch (Exception ignored) {}
        }
        try {
            return decryptWithKey(packed, keystoreKey());
        } catch (Exception e) {
            Log.e("DataCipher", "decryptString failed (cross-device data)", e);
            return "[encrypted data from another device]";
        }
    }

    void encryptFile(File inFile, File outFile) throws Exception {
        SecretKey key = pinKey != null ? pinKey : keystoreKey();
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream fin = new FileInputStream(inFile);
             FileOutputStream fout = new FileOutputStream(outFile)) {
            fout.write((byte) iv.length);
            fout.write(iv);
            try (CipherOutputStream cout = new CipherOutputStream(fout, cipher)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fin.read(buf)) != -1) {
                    cout.write(buf, 0, len);
                }
            }
        }
    }

    void decryptFile(File inFile, File outFile) throws Exception {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream fin = new FileInputStream(inFile);
             FileOutputStream fout = new FileOutputStream(outFile)) {
            int ivSize = fin.read();
            if (ivSize < 0) throw new Exception("Unexpected end of file reading IV length");
            byte[] iv = new byte[ivSize];
            int read = fin.read(iv);
            if (read != ivSize) throw new Exception("Failed to read IV");
            // Try PIN key first, then Keystore
            Exception lastError = null;
            if (pinKey != null) {
                try {
                    Cipher cipher = Cipher.getInstance(TRANSFORM);
                    cipher.init(Cipher.DECRYPT_MODE, pinKey, new GCMParameterSpec(128, iv));
                    try (CipherInputStream cin = new CipherInputStream(fin, cipher)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = cin.read(buf)) != -1) {
                            fout.write(buf, 0, len);
                        }
                    }
                    return;
                } catch (Exception e) {
                    lastError = e;
                    // Fall through to Keystore
                }
            }
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), new GCMParameterSpec(128, iv));
            try (CipherInputStream cin = new CipherInputStream(fin, cipher)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = cin.read(buf)) != -1) {
                    fout.write(buf, 0, len);
                }
            }
        }
    }

    private String decryptWithKey(byte[] packed, SecretKey key) throws Exception {
        int ivSize = packed[0] & 0xFF;
        byte[] iv = new byte[ivSize];
        byte[] encrypted = new byte[packed.length - 1 - ivSize];
        System.arraycopy(packed, 1, iv, 0, ivSize);
        System.arraycopy(packed, 1 + ivSize, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    static void encryptWithPin(File inFile, File outFile, String pin) throws Exception {
        byte[] salt = new byte[16];
        java.security.SecureRandom rng = new java.security.SecureRandom();
        rng.nextBytes(salt);
        SecretKey key = deriveKey(pin, salt);
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fout = new FileOutputStream(outFile)) {
            fout.write(salt);
            fout.write((byte) iv.length);
            fout.write(iv);
            try (FileInputStream fin = new FileInputStream(inFile);
                 CipherOutputStream cout = new CipherOutputStream(fout, cipher)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fin.read(buf)) != -1) {
                    cout.write(buf, 0, len);
                }
            }
        }
    }

    static void decryptWithPin(File inFile, File outFile, String pin) throws Exception {
        try (FileInputStream fin = new FileInputStream(inFile)) {
            byte[] salt = new byte[16];
            int read = fin.read(salt);
            if (read != 16) throw new Exception("Invalid backup file: missing salt");
            int ivSize = fin.read();
            if (ivSize < 0) throw new Exception("Invalid backup file: missing IV length");
            byte[] iv = new byte[ivSize];
            read = fin.read(iv);
            if (read != ivSize) throw new Exception("Invalid backup file: missing IV");
            SecretKey key = deriveKey(pin, salt);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fout = new FileOutputStream(outFile);
                 CipherInputStream cin = new CipherInputStream(fin, cipher)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = cin.read(buf)) != -1) {
                    fout.write(buf, 0, len);
                }
            }
        }
    }

    static void encryptWithCachedKey(File inFile, File outFile, byte[] keyBytes, byte[] salt) throws Exception {
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream fout = new FileOutputStream(outFile)) {
            fout.write(salt);
            fout.write((byte) iv.length);
            fout.write(iv);
            try (FileInputStream fin = new FileInputStream(inFile);
                 CipherOutputStream cout = new CipherOutputStream(fout, cipher)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fin.read(buf)) != -1) {
                    cout.write(buf, 0, len);
                }
            }
        }
    }

    static byte[] deriveKeyBytes(String pin, byte[] salt) throws Exception {
        return deriveKey(pin, salt).getEncoded();
    }

    private static SecretKey deriveKey(String pin, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 100000, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] pack(byte[] iv, byte[] payload) {
        byte[] out = new byte[1 + iv.length + payload.length];
        out[0] = (byte) iv.length;
        System.arraycopy(iv, 0, out, 1, iv.length);
        System.arraycopy(payload, 0, out, 1 + iv.length, payload.length);
        return out;
    }

    private SecretKey keystoreKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
}
