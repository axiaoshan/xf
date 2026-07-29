package com.xifan.sign;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class SignServer {

    private static final int PORT = 2357;
    private static ServerSocket server;
    private static boolean running = false;
    private static boolean providersRegistered = false;

    public static void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 50, java.net.InetAddress.getByName("0.0.0.0"));
                de.robv.android.xposed.XposedBridge.log("[xifan-sign] http://0.0.0.0:" + PORT);
                while (running) {
                    Socket client = server.accept();
                    handleClient(client);
                }
            } catch (Exception e) {
                de.robv.android.xposed.XposedBridge.log("[xifan-sign] error: " + e);
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

    private static void ensureProviders() {
        if (providersRegistered) return;
        providersRegistered = true;
        try {
            Class<?> spCls = findClass("com.kwai.theater.framework.core.service.ServiceProvider");
            Method putMethod = spCls.getDeclaredMethod("put", Class.class, Object.class);

            String[] implClasses = {
                "com.kwai.theater.component.base.core.KsAdSDKInit$KsAdContextImpl",
                "com.kwai.theater.component.base.core.KsAdSDKInit$SecurityProviderImpl",
                "com.kwai.theater.component.base.core.KsAdSDKInit$SdkConfigImpl",
            };
            String[] ifaceNames = {
                "com.kwai.theater.framework.core.service.provider.KsAdContext",
                "com.kwai.theater.framework.core.service.provider.SecurityProvider",
                "com.kwai.theater.framework.core.service.provider.SdkConfigProvider",
            };

            for (int i = 0; i < implClasses.length; i++) {
                try {
                    Class<?> implCls = findClass(implClasses[i]);
                    Class<?> ifaceCls = findClass(ifaceNames[i]);
                    if (implCls == null || ifaceCls == null) continue;
                    Constructor<?> ctor = implCls.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object instance = ctor.newInstance();
                    putMethod.invoke(null, ifaceCls, instance);
                    de.robv.android.xposed.XposedBridge.log("[xifan-sign] registered " + ifaceNames[i]);
                } catch (Exception e) {
                    de.robv.android.xposed.XposedBridge.log("[xifan-sign] failed to register " + ifaceNames[i] + ": " + e);
                }
            }

            Class<?> hostSvcMgr = findClass("com.kwai.theater.api.host.security.IHostSecurityService");
            Class<?> hssmImpl = findClass("com.kwai.theater.api.component.security.HostSecurityServiceManager");
            if (hostSvcMgr != null && hssmImpl != null) {
                try {
                    Method setUp = hssmImpl.getDeclaredMethod("setUp");
                    setUp.setAccessible(true);
                    setUp.invoke(null);
                    de.robv.android.xposed.XposedBridge.log("[xifan-sign] registered IHostSecurityService");
                } catch (Exception e) {
                    de.robv.android.xposed.XposedBridge.log("[xifan-sign] failed IHostSecurityService: " + e);
                }
            }
        } catch (Exception e) {
            de.robv.android.xposed.XposedBridge.log("[xifan-sign] ensureProviders error: " + e);
        }
    }

    private static String health() {
        try {
            Class<?> kSecCls = findClass("com.kuaishou.android.security.KSecurity");
            Class<?> weaponCls = findClass("com.kuaishou.weapon.i.WeaponHI");

            boolean kSecReady = false;
            String kaw = "";

            if (kSecCls != null) {
                Method isInit = kSecCls.getDeclaredMethod("isInitialize");
                kSecReady = (boolean) isInit.invoke(null);
            }

            Context ctx = getContext();
            if (kSecReady && ctx != null && weaponCls != null) {
                try {
                    Method gMethod = weaponCls.getDeclaredMethod("g", Context.class);
                    Object kawObj = gMethod.invoke(null, ctx);
                    kaw = kawObj != null ? kawObj.toString() : "";
                } catch (Exception ignored) {}
            }

            JSONObject obj = new JSONObject();
            obj.put("ready", kSecReady && kaw.length() > 0);
            obj.put("kSecurity", kSecReady);
            obj.put("kaw", kaw);
            return obj.toString();
        } catch (Exception e) {
            return jsonError(500, "health error: " + e);
        }
    }

    private static String sign(String bodyJson) {
        try {
            ensureProviders();

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
                Object kawObj = gMethod.invoke(null, ctx);
                String kaw = kawObj != null ? kawObj.toString() : "";
                if (kaw.length() > 0) headers.put("Kaw", kaw);

                try {
                    Method aMethod = weaponCls.getDeclaredMethod("a", String.class, String.class);
                    aMethod.setAccessible(true);
                    Object kasObj = aMethod.invoke(null, url + kaw, "");
                    String kas = kasObj != null ? kasObj.toString() : "";
                    if (kas.length() > 0) {
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
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return jsonError(500, "sign error: " + sw.toString());
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
            return jsonError(500, "encrypt error: " + e);
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
            return jsonError(500, "decrypt error: " + e);
        }
    }

    private static String jsonError(int code, String msg) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("error", msg);
            return obj.toString();
        } catch (Exception e) {
            return "{\"error\":\"error\"}";
        }
    }
}
