package net.example.thinksmart.anrsilencer;

import android.os.Message;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Experimental LSPosed legacy-API module for the Lenovo ThinkSmart View
 * LineageOS 15.1 / Android 8.1 build.
 *
 * It hooks AppErrors.handleShowAnrUi(Message) in system_server. For a message
 * whose ProcessRecord is exactly com.android.systemui, it suppresses the
 * dialog and clears the legacy ProcessRecord ANR flags. Other processes are
 * left to Android's original handler.
 */
public final class AnrSilencer implements IXposedHookLoadPackage {
    private static final String TAG = "ThinkSmartAnrSilencer";
    private static final String SYSTEM_SERVER_PACKAGE = "android";
    private static final String APP_ERRORS = "com.android.server.am.AppErrors";
    private static final String TARGET = "com.android.systemui";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_SERVER_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            final Class<?> cls = XposedHelpers.findClass(APP_ERRORS, lpparam.classLoader);
            int hooks = 0;

            for (final Method method : cls.getDeclaredMethods()) {
                if (!"handleShowAnrUi".equals(method.getName())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || !"android.os.Message".equals(params[0].getName())) {
                    continue;
                }

                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object message = param.args[0];
                            Object payload = readField(message, "obj");
                            Object process = findProcessRecord(payload);
                            String processName = getProcessName(process);

                            if (!TARGET.equals(processName)) {
                                return;
                            }

                            // Clear the Android 8.x ProcessRecord flags that were
                            // set when the ANR was reported. Do not kill the process.
                            boolean cleared = writeField(process, "notResponding", Boolean.FALSE);
                            writeField(process, "notRespondingReport", null);

                            XposedBridge.log(TAG + ": suppressed System UI ANR dialog; "
                                    + "clearedNotResponding=" + cleared);

                            // Skip the dialog handler only for System UI.
                            param.setResult(null);
                        } catch (Throwable t) {
                            // Fail open: if we cannot positively identify the process,
                            // let Android's original ANR handler run.
                            XposedBridge.log(TAG + ": hook error; leaving original handler: " + t);
                        }
                    }
                });
                hooks++;
            }

            XposedBridge.log(TAG + ": loaded in system_server; hooks=" + hooks
                    + " (expected 1 on Android 8.1)");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to install hook: " + t);
        }
    }

    private static Object findProcessRecord(Object payload) {
        if (payload == null) return null;

        // AOSP Android 8.0-style payload: HashMap containing "app" -> ProcessRecord.
        if (payload instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) payload;
            Object app = map.get("app");
            if (app != null) return app;
            Object proc = map.get("proc");
            if (proc != null) return proc;
        }

        // Newer/forked payloads may use a Data object with a "proc" field.
        Object proc = readField(payload, "proc");
        if (proc != null) return proc;

        Object app = readField(payload, "app");
        if (app != null) return app;

        // Some ROM forks may pass ProcessRecord directly.
        if (getProcessName(payload) != null) return payload;

        return null;
    }

    private static String getProcessName(Object process) {
        if (process == null) return null;

        Object name = readField(process, "processName");
        if (name instanceof String) return (String) name;

        Object info = readField(process, "info");
        if (info != null) {
            Object processName = readField(info, "processName");
            if (processName instanceof String) return (String) processName;
            Object packageName = readField(info, "packageName");
            if (packageName instanceof String) return (String) packageName;
        }

        return null;
    }

    private static Object readField(Object object, String name) {
        if (object == null) return null;
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static boolean writeField(Object object, String name, Object value) {
        if (object == null) return false;
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                if (Modifier.isFinal(field.getModifiers())) return false;
                field.setAccessible(true);
                field.set(object, value);
                return true;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": unable to set " + name + ": " + t);
                return false;
            }
        }
        return false;
    }
}
