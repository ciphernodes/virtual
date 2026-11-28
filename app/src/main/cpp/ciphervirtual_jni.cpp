#include <jni.h>
#include <string>
#include "syscall_interceptor.h"
#include "virtual_sandbox.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeInitVirtualEngine(
    JNIEnv* env,
    jobject /* this */,
    jstring hostPackageStr,
    jint hostUid,
    jboolean isRootMode
) {
    const char* hostPkg = env->GetStringUTFChars(hostPackageStr, nullptr);
    ciphervirtual::SyscallInterceptor::getInstance().init(
        hostPkg ? hostPkg : "",
        hostUid,
        isRootMode == JNI_TRUE
    );
    if (hostPkg) env->ReleaseStringUTFChars(hostPackageStr, hostPkg);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeRegisterRedirectRule(
    JNIEnv* env,
    jobject /* this */,
    jstring instanceIdStr,
    jstring targetPkgStr,
    jstring sourcePrefixStr,
    jstring targetPrefixStr,
    jint virtualUid
) {
    const char* instanceId = env->GetStringUTFChars(instanceIdStr, nullptr);
    const char* targetPkg = env->GetStringUTFChars(targetPkgStr, nullptr);
    const char* srcPrefix = env->GetStringUTFChars(sourcePrefixStr, nullptr);
    const char* tgtPrefix = env->GetStringUTFChars(targetPrefixStr, nullptr);

    ciphervirtual::RedirectRule rule;
    rule.instanceId = instanceId ? instanceId : "";
    rule.targetPackage = targetPkg ? targetPkg : "";
    rule.sourcePrefix = srcPrefix ? srcPrefix : "";
    rule.targetPrefix = tgtPrefix ? tgtPrefix : "";
    rule.virtualUid = virtualUid;

    ciphervirtual::SyscallInterceptor::getInstance().registerRule(rule);

    if (instanceId) env->ReleaseStringUTFChars(instanceIdStr, instanceId);
    if (targetPkg) env->ReleaseStringUTFChars(targetPkgStr, targetPkg);
    if (srcPrefix) env->ReleaseStringUTFChars(sourcePrefixStr, srcPrefix);
    if (tgtPrefix) env->ReleaseStringUTFChars(targetPrefixStr, tgtPrefix);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeActivateInstance(
    JNIEnv* env,
    jobject /* this */,
    jstring instanceIdStr
) {
    const char* instanceId = env->GetStringUTFChars(instanceIdStr, nullptr);
    if (instanceId) {
        ciphervirtual::SyscallInterceptor::getInstance().activateInstance(instanceId);
        env->ReleaseStringUTFChars(instanceIdStr, instanceId);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeRedirectPath(
    JNIEnv* env,
    jobject /* this */,
    jstring originalPathStr
) {
    if (!originalPathStr) return nullptr;
    const char* origPath = env->GetStringUTFChars(originalPathStr, nullptr);
    std::string translated = ciphervirtual::SyscallInterceptor::getInstance().translatePath(origPath ? origPath : "");
    if (origPath) env->ReleaseStringUTFChars(originalPathStr, origPath);
    return env->NewStringUTF(translated.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeGetRedirectCount(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return static_cast<jlong>(ciphervirtual::SyscallInterceptor::getInstance().getRedirectCount());
}

JNIEXPORT jboolean JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeSetVirtualUid(
    JNIEnv* /* env */,
    jobject /* this */,
    jint uid
) {
    ciphervirtual::SyscallInterceptor::getInstance().setVirtualUid(uid);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeGetVirtualUid(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return ciphervirtual::SyscallInterceptor::getInstance().getVirtualUid();
}

JNIEXPORT jboolean JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeCheckRootCapability(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return ciphervirtual::VirtualSandbox::getInstance().checkRootAccess() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeExecuteRootCommand(
    JNIEnv* env,
    jobject /* this */,
    jstring commandStr
) {
    if (!commandStr) return env->NewStringUTF("");
    const char* cmd = env->GetStringUTFChars(commandStr, nullptr);
    std::string result = ciphervirtual::VirtualSandbox::getInstance().executeRootCommand(cmd ? cmd : "");
    if (cmd) env->ReleaseStringUTFChars(commandStr, cmd);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_cipher_virtual_virtualization_NativeVirtualEngine_nativeGetEngineStatus(
    JNIEnv* env,
    jobject /* this */
) {
    std::string status = ciphervirtual::SyscallInterceptor::getInstance().dumpStatus();
    return env->NewStringUTF(status.c_str());
}

} // extern "C"
