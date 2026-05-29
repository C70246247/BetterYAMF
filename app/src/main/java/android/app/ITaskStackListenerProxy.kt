package android.app

import net.bytebuddy.ByteBuddy
import net.bytebuddy.android.AndroidClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.AllArguments
import net.bytebuddy.implementation.bind.annotation.Origin
import net.bytebuddy.implementation.bind.annotation.RuntimeType
import net.bytebuddy.matcher.ElementMatchers
import java.io.File
import java.lang.reflect.Method

object ITaskStackListenerProxy {
    val byteBuddyStrategy = AndroidClassLoadingStrategy.Wrapping(File("/data/system/reYAMF").also { it.mkdirs() })
    fun newInstance(
        classLoader: ClassLoader,
        intercept: (Array<Any?>, Method) -> Any?
    ): ITaskStackListener {
        return ByteBuddy()
            .subclass(ITaskStackListener.Stub::class.java)
            .method(ElementMatchers.any())
            .intercept(MethodDelegation.to(object {
                @RuntimeType
                fun intercept(
                    @AllArguments allArguments: Array<Any?>,
                    @Origin method: Method
                ) {
                    intercept(allArguments, method)
                }
            }))
            .make()
            .load(classLoader, byteBuddyStrategy)
            .loaded
            .getDeclaredConstructor()
            .newInstance()
    }
}