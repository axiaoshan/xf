package com.xifan.sign;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XifanSignHook implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.kwai.theater";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("[xifan-sign] handleLoadPackage: " + lpparam.packageName);

        XposedHelpers.findAndHookMethod(
            "com.kwai.theater.KSApplication",
            lpparam.classLoader,
            "onCreate",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log("[xifan-sign] KSApplication.onCreate, starting server");
                    SignServer.start();
                }
            }
        );
    }
}
