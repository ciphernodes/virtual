#include "syscall_interceptor.h"
#include <sstream>
#include <algorithm>

namespace ciphervirtual {

SyscallInterceptor::SyscallInterceptor() = default;

SyscallInterceptor& SyscallInterceptor::getInstance() {
    static SyscallInterceptor instance;
    return instance;
}

void SyscallInterceptor::init(const std::string& hostPackage, int hostUid, bool isRootMode) {
    std::lock_guard<std::mutex> lock(mutex_);
    hostPackage_ = hostPackage;
    hostUid_ = hostUid;
    isRootMode_ = isRootMode;
    redirectCount_ = 0;
}

void SyscallInterceptor::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    rules_.clear();
    activeInstanceId_.clear();
    redirectCount_ = 0;
}

void SyscallInterceptor::registerRule(const RedirectRule& rule) {
    std::lock_guard<std::mutex> lock(mutex_);
    // Remove duplicate or existing rule with same sourcePrefix
    rules_.erase(
        std::remove_if(rules_.begin(), rules_.end(),
            [&rule](const RedirectRule& r) {
                return r.sourcePrefix == rule.sourcePrefix && r.instanceId == rule.instanceId;
            }),
        rules_.end()
    );
    rules_.push_back(rule);
}

void SyscallInterceptor::clearRulesForInstance(const std::string& instanceId) {
    std::lock_guard<std::mutex> lock(mutex_);
    rules_.erase(
        std::remove_if(rules_.begin(), rules_.end(),
            [&instanceId](const RedirectRule& r) {
                return r.instanceId == instanceId;
            }),
        rules_.end()
    );
    if (activeInstanceId_ == instanceId) {
        activeInstanceId_.clear();
    }
}

void SyscallInterceptor::activateInstance(const std::string& instanceId) {
    std::lock_guard<std::mutex> lock(mutex_);
    activeInstanceId_ = instanceId;
    for (const auto& r : rules_) {
        if (r.instanceId == instanceId) {
            virtualUid_ = r.virtualUid;
            break;
        }
    }
}

std::string SyscallInterceptor::translatePath(const std::string& originalPath) const {
    if (originalPath.empty()) return originalPath;

    std::lock_guard<std::mutex> lock(mutex_);
    for (const auto& rule : rules_) {
        // If an instance is explicitly activated, prefer matching rules for that instance
        if (!activeInstanceId_.empty() && rule.instanceId != activeInstanceId_) {
            continue;
        }

        if (originalPath.rfind(rule.sourcePrefix, 0) == 0) {
            std::string sub = originalPath.substr(rule.sourcePrefix.length());
            redirectCount_++;
            return rule.targetPrefix + sub;
        }
    }

    // Secondary pass across all rules if active instance didn't match
    for (const auto& rule : rules_) {
        if (originalPath.rfind(rule.sourcePrefix, 0) == 0) {
            std::string sub = originalPath.substr(rule.sourcePrefix.length());
            redirectCount_++;
            return rule.targetPrefix + sub;
        }
    }

    return originalPath;
}

bool SyscallInterceptor::shouldIntercept(const std::string& path) const {
    std::lock_guard<std::mutex> lock(mutex_);
    for (const auto& rule : rules_) {
        if (path.rfind(rule.sourcePrefix, 0) == 0) {
            return true;
        }
    }
    return false;
}

void SyscallInterceptor::setVirtualUid(int uid) {
    virtualUid_ = uid;
}

int SyscallInterceptor::getVirtualUid() const {
    return virtualUid_.load();
}

void SyscallInterceptor::setVirtualPid(int pid) {
    virtualPid_ = pid;
}

int SyscallInterceptor::getVirtualPid() const {
    return virtualPid_.load();
}

uint64_t SyscallInterceptor::getRedirectCount() const {
    return redirectCount_.load();
}

std::string SyscallInterceptor::getActiveInstanceId() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return activeInstanceId_;
}

std::string SyscallInterceptor::dumpStatus() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::ostringstream oss;
    oss << "SyscallInterceptor[activeInstance=" << activeInstanceId_
        << ", virtualUid=" << virtualUid_
        << ", virtualPid=" << virtualPid_
        << ", isRootMode=" << (isRootMode_ ? "true" : "false")
        << ", redirectCount=" << redirectCount_
        << ", totalRules=" << rules_.size() << "]";
    return oss.str();
}

} // namespace ciphervirtual
