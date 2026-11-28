#ifndef CIPHERVIRTUAL_SYSCALL_INTERCEPTOR_H
#define CIPHERVIRTUAL_SYSCALL_INTERCEPTOR_H

#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <sys/types.h>

namespace ciphervirtual {

/**
 * Represents an isolated file I/O redirection mapping from original target package paths
 * (e.g. /data/data/<pkg>) to isolated per-instance storage directories.
 */
struct RedirectRule {
    std::string sourcePrefix;       // e.g. "/data/data/com.target.app" or "/data/user/0/com.target.app"
    std::string targetPrefix;       // e.g. "/data/data/com.cipher.virtual/files/workspaces/<instanceId>/data"
    std::string instanceId;
    std::string targetPackage;
    int virtualUid;
};

/**
 * Low-level system call interception and memory isolation engine.
 *
 * Implements filesystem path translation and virtual user/process ID isolation
 * required to execute cloned application instances in a non-root Android environment,
 * similar to how Parallel Space and VirtualApp intercept libc calls.
 */
class SyscallInterceptor {
public:
    static SyscallInterceptor& getInstance();

    // Initialization and lifecycle
    void init(const std::string& hostPackage, int hostUid, bool isRootMode);
    void reset();

    // Rule registration
    void registerRule(const RedirectRule& rule);
    void clearRulesForInstance(const std::string& instanceId);
    void activateInstance(const std::string& instanceId);

    // Path translation & system call interception
    std::string translatePath(const std::string& originalPath) const;
    bool shouldIntercept(const std::string& path) const;

    // Virtual Process & UID Spoofing
    void setVirtualUid(int uid);
    int getVirtualUid() const;
    void setVirtualPid(int pid);
    int getVirtualPid() const;

    // Telemetry & metrics
    uint64_t getRedirectCount() const;
    std::string getActiveInstanceId() const;
    std::string dumpStatus() const;

private:
    SyscallInterceptor();
    ~SyscallInterceptor() = default;

    std::string hostPackage_;
    int hostUid_{0};
    bool isRootMode_{false};

    mutable std::mutex mutex_;
    std::vector<RedirectRule> rules_;
    std::string activeInstanceId_;
    std::atomic<int> virtualUid_{10000};
    std::atomic<int> virtualPid_{0};
    mutable std::atomic<uint64_t> redirectCount_{0};
};

} // namespace ciphervirtual

#endif // CIPHERVIRTUAL_SYSCALL_INTERCEPTOR_H
