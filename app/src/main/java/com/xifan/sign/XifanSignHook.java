package com.xifan.sign;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XifanSignHook implements IXposedHookLoadPackage {

    private static final String TARGET_PKG = "com.kwai.theater";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PKG.equals(lpparam.packageName)) return;

        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onCreate",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    new Thread(() -> {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                        SignServer.start();
                    }, "xifan-sign-delay").start();
                }
            }
        );
    }
}
