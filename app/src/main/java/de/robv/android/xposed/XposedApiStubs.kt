package de.robv.android.xposed

import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Compile-time Xposed API definitions compatible with LSPosed / XposedBridge runtime classloader.
 * At runtime in a hooked process, LSPosed's parent-first classloader provides the real
 * de.robv.android.xposed.* classes from XposedBridge.jar.
 */
interface IXposedHookLoadPackage {
    @Throws(Throwable::class)
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam)
}

abstract class XC_MethodHook {
    @Throws(Throwable::class)
    protected open fun beforeHookedMethod(param: MethodHookParam) {}

    @Throws(Throwable::class)
    protected open fun afterHookedMethod(param: MethodHookParam) {}

    class MethodHookParam {
        @JvmField var thisObject: Any? = null
        @JvmField var args: Array<Any?> = emptyArray()
        var result: Any? = null
        var throwable: Throwable? = null
    }
}

abstract class XC_MethodReplacement : XC_MethodHook() {
    @Throws(Throwable::class)
    override fun beforeHookedMethod(param: MethodHookParam) {
        try {
            param.result = replaceHookedMethod(param)
        } catch (t: Throwable) {
            param.throwable = t
        }
    }

    @Throws(Throwable::class)
    protected abstract fun replaceHookedMethod(param: MethodHookParam): Any?

    companion object {
        @JvmStatic
        fun returnConstant(result: Any?): XC_MethodReplacement {
            return object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? = result
            }
        }
    }
}

object XposedBridge {
    @JvmStatic
    fun log(text: String) {
        android.util.Log.i("LSPosed-StroboScope", text)
    }

    @JvmStatic
    fun log(t: Throwable) {
        android.util.Log.e("LSPosed-StroboScope", "Xposed error", t)
    }
}

object XposedHelpers {
    @JvmStatic
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any
    ): Any? {
        return null
    }
}
