#ifndef CIPHERVIRTUAL_VIRTUAL_SANDBOX_H
#define CIPHERVIRTUAL_VIRTUAL_SANDBOX_H

#include <string>
#include <vector>

namespace ciphervirtual {

/**
 * Manages sandboxed application runtime environments and root privilege execution bridge.
 */
class VirtualSandbox {
public:
    static VirtualSandbox& getInstance();

    // Root execution bridge
    bool checkRootAccess();
    std::string executeRootCommand(const std::string& command);

    // Sandbox container setup
    bool setupInstanceEnvironment(const std::string& instanceId, const std::string& targetPkg,
                                 const std::string& dataPath, const std::string& cachePath,
                                 int virtualUid);

private:
    VirtualSandbox() = default;
    ~VirtualSandbox() = default;
};

} // namespace ciphervirtual

#endif // CIPHERVIRTUAL_VIRTUAL_SANDBOX_H
