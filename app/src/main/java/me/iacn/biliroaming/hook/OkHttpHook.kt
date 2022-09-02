package me.iacn.biliroaming.hook

import me.iacn.biliroaming.BiliBiliPackage.Companion.instance
import me.iacn.biliroaming.hook.api.ApiHook
import me.iacn.biliroaming.hook.api.CardsHook
import me.iacn.biliroaming.hook.api.SeasonRcmdHook
import me.iacn.biliroaming.utils.*
import java.net.HttpURLConnection

class OkHttpHook(classLoader: ClassLoader) : BaseHook(classLoader) {

    private val apiHooks = mutableListOf<ApiHook>()

    init {
        apiHooks.add(SeasonRcmdHook)
        apiHooks.add(CardsHook)
    }

    override fun startHook() {
        if (apiHooks.all { !it.enabled }) return

        instance.realCallClass?.hookAfterMethod(instance.executeCall()) { param ->
            val response = param.result ?: return@hookAfterMethod
            val requestField = instance.realCallRequestField() ?: return@hookAfterMethod
            val urlField = instance.urlField() ?: return@hookAfterMethod
            val request = param.thisObject.getObjectField(requestField) ?: return@hookAfterMethod
            val url = request.getObjectField(urlField)?.toString() ?: return@hookAfterMethod
            for (hook in apiHooks) {
                if (!hook.enabled || !hook.canHandler(url))
                    continue
                val okioClass = instance.okioClass ?: return@hookAfterMethod
                val bufferedSourceClass = instance.bufferedSourceClass ?: return@hookAfterMethod
                val codeField = instance.responseCodeField() ?: return@hookAfterMethod
                val bodyField = instance.responseBodyField() ?: return@hookAfterMethod
                val stringMethod = instance.responseBodyString() ?: return@hookAfterMethod
                val sourceMethod = instance.source() ?: return@hookAfterMethod
                val bufferMethod = instance.sourceBuffer() ?: return@hookAfterMethod
                response.getIntField(codeField).takeIf { it == HttpURLConnection.HTTP_OK }
                    ?: return@hookAfterMethod

                val responseBody = response.getObjectField(bodyField)
                val respString = responseBody?.callMethod(stringMethod)
                    ?.toString() ?: return@hookAfterMethod
                val sourceField = responseBody.javaClass.findFieldByExactType(bufferedSourceClass)
                    ?: return@hookAfterMethod
                val longType = Long::class.javaPrimitiveType!!
                val contentLengthField = responseBody.javaClass.findFieldByExactType(longType)
                    ?: return@hookAfterMethod
                val newResponse = hook.hook(respString)
                val stream = newResponse.byteInputStream()
                val length = stream.available()
                val source = okioClass.callStaticMethod(sourceMethod, stream)
                    ?: return@hookAfterMethod
                val bufferedSource = okioClass.callStaticMethod(bufferMethod, source)
                    ?: return@hookAfterMethod
                sourceField.set(responseBody, bufferedSource)
                contentLengthField.set(responseBody, length)
                break
            }
        }
    }
}