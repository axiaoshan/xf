package com.xifan.sign;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SignServer {

    private static final int PORT = 2357;
    private static ServerSocket server;
    private static boolean running = false;

    public static void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                System.out.println("[xifan-sign] http://127.0.0.1:" + PORT);
                while (running) {
                    Socket client = server.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                System.out.println("[xifan-sign] 服务异常: " + e);
            }
        }, "xifan-sign-server").start();
    }

    private static void handleClient(Socket client) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String line = reader.readLine();
            if (line == null) { client.close(); return; }

            String[] parts = line.split(" ");
            if (parts.length < 2) { client.close(); return; }
            String method = parts[0];
            String path = parts[1];

            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                reader.read(buf, 0, contentLength);
                body = new String(buf);
            }

            String response;
            if (path.equals("/health") && method.equals("GET")) {
                response = health();
            } else if (path.equals("/sign") && method.equals("POST")) {
                response = sign(body);
            } else if (path.equals("/encrypt") && method.equals("POST")) {
                response = encrypt(body);
            } else if (path.equals("/decrypt") && method.equals("POST")) {
                response = decrypt(body);
            } else {
                response = jsonError(404, "not found");
            }

            OutputStream os = client.getOutputStream();
            byte[] data = response.getBytes("UTF-8");
            os.write(("HTTP/1.1 200 OK\r\n").getBytes("UTF-8"));
            os.write(("Content-Type: application/json; charset=utf-8\r\n").getBytes("UTF-8"));
            os.write(("Content-Length: " + data.length + "\r\n").getBytes("UTF-8"));
            os.write("\r\n".getBytes("UTF-8"));
            os.write(data);
            os.flush();
            client.close();
        } catch (Exception e) {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    private static Context getContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method m = at.getDeclaredMethod("currentApplication");
            return ((Context) m.invoke(null)).getApplicationContext();
        } catch (Exception e) {
            return null;
        }
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name, false, getContext().getClassLoader());
        } catch (Exception e) {
            return null;
        }
    }

    private static String health() {
        try {
            Class<?> kSecCls = findClass("com.kuaishou.android.security.KSecurity");
            Class<?> weaponCls = findClass("com.kuaishou.weapon.i.WeaponHI");

            boolean kSecReady = false;
            boolean wCtxReady = false;
            String kaw = "";

            if (kSecCls != null) {
                Method isInit = kSecCls.getDeclaredMethod("isInitialize");
                kSecReady = (boolean) isInit.invoke(null);
            }

            if (weaponCls != null) {
                java.lang.reflect.Field ctxField = weaponCls.getDeclaredField("mContext");
                ctxField.setAccessible(true);
                Context wCtx = (Context) ctxField.get(null);
                wCtxReady = wCtx != null;

                if (kSecReady && wCtxReady) {
                    try {
                        Method gMethod = weaponCls.getDeclaredMethod("g", Context.class);
                        kaw = (String) gMethod.invoke(null, wCtx);
                    } catch (Exception ignored) {}
                }
            }

            JSONObject obj = new JSONObject();
            obj.put("ready", kSecReady && wCtxReady);
            obj.put("kSecurity", kSecReady);
            obj.put("weaponCtx", wCtxReady);
            obj.put("kaw", kaw != null ? kaw : "");
            return obj.toString();
        } catch (Exception e) {
            return jsonError(500, "health error: " + e.getMessage());
        }
    }

    private static String sign(String bodyJson) {
        try {
            JSONObject req = new JSONObject(bodyJson);
            String url = req.optString("url", "");
            String body = req.optString("body", "");

            Context ctx = getContext();
            HashMap<String, String> headers = new HashMap<>();

            Class<?> encCls = findClass("com.kwai.theater.framework.network.core.encrypt.EncryptHelper");
            Class<?> weaponCls = findClass("com.kuaishou.weapon.i.WeaponHI");

            if (encCls != null) {
                Method addHeaders = encCls.getDeclaredMethod("addHeaderParams", Map.class);
                addHeaders.invoke(null, headers);
            }

            if (weaponCls != null) {
                Method gMethod = weaponCls.getDeclaredMethod("g", Context.class);
                String kaw = (String) gMethod.invoke(null, ctx);
                if (kaw != null) headers.put("Kaw", kaw);

                try {
                    Method aMethod = weaponCls.getDeclaredMethod("a", String.class, String.class);
                    aMethod.setAccessible(true);
                    String kas = (String) aMethod.invoke(null, url + kaw, "");
                    if (kas != null && kas.length() > 0) {
                        headers.put("kas", kas);
                    }
                } catch (Exception ignored) {}
            }

            if (encCls != null) {
                Method sigMethod = encCls.getDeclaredMethod("sigRequest", String.class, Map.class, String.class);
                sigMethod.invoke(null, url, headers, body);
            }

            JSONObject result = new JSONObject();
            JSONObject hdrObj = new JSONObject();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                hdrObj.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
            result.put("headers", hdrObj);
            return result.toString();
        } catch (Exception e) {
            return jsonError(500, "sign error: " + e.getMessage());
        }
    }

    private static String encrypt(String bodyJson) {
        try {
            JSONObject req = new JSONObject(bodyJson);
            String plaintext = req.optString("plaintext", "");

            Class<?> encCls = findClass("com.kwai.theater.framework.network.core.encrypt.EncryptHelper");
            if (encCls != null) {
                Method method = encCls.getDeclaredMethod("getRequestMessage", String.class);
                method.setAccessible(true);
                String message = (String) method.invoke(null, plaintext);
                JSONObject result = new JSONObject();
                result.put("message", message != null ? message : "");
                return result.toString();
            }
            return jsonError(500, "EncryptHelper not found");
        } catch (Exception e) {
            return jsonError(500, "encrypt error: " + e.getMessage());
        }
    }

    private static String decrypt(String bodyJson) {
        try {
            JSONObject req = new JSONObject(bodyJson);
            String message = req.optString("message", "");

            Class<?> encCls = findClass("com.kwai.theater.framework.network.core.encrypt.EncryptHelper");
            if (encCls != null) {
                Method method = encCls.getDeclaredMethod("getResponseData", String.class);
                method.setAccessible(true);
                String data = (String) method.invoke(null, message);
                JSONObject result = new JSONObject();
                result.put("data", data != null ? data : "");
                return result.toString();
            }
            return jsonError(500, "EncryptHelper not found");
        } catch (Exception e) {
            return jsonError(500, "decrypt error: " + e.getMessage());
        }
    }

    private static String jsonError(int code, String msg) {
        return "{\"error\":\"" + msg.replace("\"", "\\\"") + "\"}";
    }
}
