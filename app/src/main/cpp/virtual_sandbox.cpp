#include "virtual_sandbox.h"
#include "syscall_interceptor.h"
#include <cstdio>
#include <memory>
#include <array>
#include <unistd.h>
#include <sys/stat.h>

namespace ciphervirtual {

VirtualSandbox& VirtualSandbox::getInstance() {
    static VirtualSandbox instance;
    return instance;
}

bool VirtualSandbox::checkRootAccess() {
    const char* suPaths[] = {
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su"
    };

    for (const char* path : suPaths) {
        if (access(path, X_OK) == 0) {
            return true;
        }
    }

    // Try executing su -c id
    FILE* pipe = popen("su -c id 2>/dev/null", "r");
    if (!pipe) return false;
    char buffer[128];
    bool hasRoot = false;
    if (fgets(buffer, sizeof(buffer), pipe) != nullptr) {
        std::string out(buffer);
        if (out.find("uid=0") != std::string::npos) {
            hasRoot = true;
        }
    }
    pclose(pipe);
    return hasRoot;
}

std::string VirtualSandbox::executeRootCommand(const std::string& command) {
    std::string safeCmd = "su -c '" + command + "' 2>&1";
    std::array<char, 256> buffer{};
    std::string result;
    std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(safeCmd.c_str(), "r"), pclose);
    if (!pipe) {
        return "ERROR: Failed to open root subshell pipe";
    }
    while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
        result += buffer.data();
    }
    return result;
}

bool VirtualSandbox::setupInstanceEnvironment(const std::string& instanceId, const std::string& targetPkg,
                                             const std::string& dataPath, const std::string& cachePath,
                                             int virtualUid) {
    auto& interceptor = SyscallInterceptor::getInstance();

    // Register /data/data/<targetPkg> -> dataPath
    RedirectRule dataRule;
    dataRule.sourcePrefix = "/data/data/" + targetPkg;
    dataRule.targetPrefix = dataPath;
    dataRule.instanceId = instanceId;
    dataRule.targetPackage = targetPkg;
    dataRule.virtualUid = virtualUid;
    interceptor.registerRule(dataRule);

    // Register /data/user/0/<targetPkg> -> dataPath
    RedirectRule user0Rule;
    user0Rule.sourcePrefix = "/data/user/0/" + targetPkg;
    user0Rule.targetPrefix = dataPath;
    user0Rule.instanceId = instanceId;
    user0Rule.targetPackage = targetPkg;
    user0Rule.virtualUid = virtualUid;
    interceptor.registerRule(user0Rule);

    // Register cache directory redirection
    RedirectRule cacheRule;
    cacheRule.sourcePrefix = "/data/data/" + targetPkg + "/cache";
    cacheRule.targetPrefix = cachePath;
    cacheRule.instanceId = instanceId;
    cacheRule.targetPackage = targetPkg;
    cacheRule.virtualUid = virtualUid;
    interceptor.registerRule(cacheRule);

    interceptor.activateInstance(instanceId);
    return true;
}

} // namespace ciphervirtual
