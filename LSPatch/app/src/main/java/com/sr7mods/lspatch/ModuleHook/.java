package com.sr7mods.lspatch;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * SR7 LSPatch: runtime hooks for org.telegram.messenger
 */
public class ModuleHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "org.telegram.messenger";

    private static final String ABOUT_TEXT =
            "Module by @sr7mods\n" +
            "Method by @risin42\n" +
            "Nagram Repo: https://github.com/risin42/NagramX\n" +
            "Module: https://github.com/sr7mods-assistant/SR7-TeleEnhance\n" +
            "This is a LSPatch Module based on NagramX.";

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        XposedBridge.log("[SR7-LSPatch] Loaded into " + lpparam.packageName);

        // Hook NotificationManager.notify to sanitize Notification icons
        try {
            Class<?> nmClass = NotificationManager.class;
            XposedHelpers.findAndHookMethod(nmClass, "notify", String.class, int.class, Notification.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sanitizeNotificationIcon(lpparam, (Notification) param.args[2]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[SR7-LSPatch] notify(String,int,Notification) hook failed: " + t.getMessage());
        }

        try {
            Class<?> nmClass = NotificationManager.class;
            XposedHelpers.findAndHookMethod(nmClass, "notify", int.class, Notification.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    sanitizeNotificationIcon(lpparam, (Notification) param.args[1]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[SR7-LSPatch] notify(int,Notification) hook failed: " + t.getMessage());
        }

        // Prevent addition of dynamic/pinned shortcuts mentioning "nagram"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                Class<?> smClass = ShortcutManager.class;
                XposedHelpers.findAndHookMethod(smClass, "addDynamicShortcuts", List.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            List<?> list = (List<?>) param.args[0];
                            for (Object obj : list) {
                                try {
                                    Method getId = obj.getClass().getMethod("getId");
                                    Object id = getId.invoke(obj);
                                    if (id != null && id.toString().toLowerCase().contains("nagram")) {
                                        XposedBridge.log("[SR7-LSPatch] Blocking Nagram shortcut: " + id);
                                        param.setResult(null);
                                        return;
                                    }
                                } catch (Throwable ignore) {}
                            }
                        } catch (Throwable ignore) {}
                    }
                });
                XposedHelpers.findAndHookMethod(smClass, "requestPinShortcut", ShortcutInfo.class, Class.forName("android.content.IntentSender"), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object info = param.args[0];
                            Method getId = info.getClass().getMethod("getId");
                            Object id = getId.invoke(info);
                            if (id != null && id.toString().toLowerCase().contains("nagram")) {
                                XposedBridge.log("[SR7-LSPatch] Blocking requestPinShortcut for Nagram id=" + id);
                                param.setResult(false);
                            }
                        } catch (Throwable ignore) {}
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("[SR7-LSPatch] ShortcutManager hooks failed: " + t.getMessage());
            }
        }

        // Try to rename Settings title and update About text (best-effort)
        try {
            String[] possibleSettings = new String[]{ "org.telegram.ui.SettingsActivity", "org.telegram.ui.activities.SettingsActivity", "org.telegram.ui.HomeSettingsActivity" };
            for (String clsName : possibleSettings) {
                try {
                    Class<?> settingsClass = lpparam.classLoader.loadClass(clsName);
                    XposedHelpers.findAndHookMethod(settingsClass, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object activity = param.thisObject;
                                try {
                                    Method setTitle = activity.getClass().getMethod("setTitle", CharSequence.class);
                                    setTitle.invoke(activity, "SR7 Settings");
                                } catch (Throwable ignore) {}

                                // Attempt to replace any TextView containing \"nagram\" with the desired About text
                                try {
                                    Method getWindow = activity.getClass().getMethod("getWindow");
                                    Object window = getWindow.invoke(activity);
                                    Method getDecor = window.getClass().getMethod("getDecorView");
                                    Object decor = getDecor.invoke(window);
                                    replaceAboutTextRecursive(decor);
                                } catch (Throwable ignore) {}

                                XposedBridge.log("[SR7-LSPatch] Attempted SR7 Settings/About update in " + activity.getClass().getName());
                            } catch (Throwable t) {
                                XposedBridge.log("[SR7-LSPatch] settings onCreate afterHook failed: " + t.getMessage());
                            }
                        }
                    });
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            XposedBridge.log("[SR7-LSPatch] Settings hooks failed: " + t.getMessage());
        }
    }

    private void sanitizeNotificationIcon(LoadPackageParam lpparam, Notification notification) {
        if (notification == null) return;
        try {
            try {
                int appIcon = lpparam.appInfo.icon;
                try {
                    Field iconField = Notification.class.getDeclaredField("icon");
                    iconField.setAccessible(true);
                    iconField.setInt(notification, appIcon);
                    XposedBridge.log("[SR7-LSPatch] Replaced notification icon with app icon: " + appIcon);
                } catch (Throwable t) {
                    XposedBridge.log("[SR7-LSPatch] Failed to replace notification icon: " + t.getMessage());
                }
            } catch (Throwable ignore) {}
        } catch (Throwable t) {
            XposedBridge.log("[SR7-LSPatch] sanitizeNotificationIcon error: " + t.getMessage());
        }
    }

    private void replaceAboutTextRecursive(Object view) {
        if (view == null) return;
        try {
            Class<?> cls = view.getClass();
            String className = cls.getName().toLowerCase();
            // If view looks like a TextView (or subclass), check and replace
            if (className.contains("textview")) {
                try {
                    Method getText = cls.getMethod("getText");
                    Object txt = getText.invoke(view);
                    if (txt != null) {
                        String s = txt.toString().toLowerCase();
                        if (s.contains("nagram") || s.contains("nagramx")) {
                            try {
                                Method setText = cls.getMethod("setText", CharSequence.class);
                                setText.invoke(view, ABOUT_TEXT);
                                XposedBridge.log("[SR7-LSPatch] Replaced About text on view: " + cls.getName());
                                return; // replaced
                            } catch (Throwable ignore) {}
                        }
                    }
                } catch (Throwable ignore) {}
            }

            // If view is a ViewGroup-like, iterate children
            try {
                Method getChildCount = cls.getMethod("getChildCount");
                Method getChildAt = cls.getMethod("getChildAt", int.class);
                int count = (int) getChildCount.invoke(view);
                for (int i = 0; i < count; i++) {
                    Object child = getChildAt.invoke(view, i);
                    replaceAboutTextRecursive(child);
                }
            } catch (Throwable ignore) {}
        } catch (Throwable ignore) {}
    }
}
