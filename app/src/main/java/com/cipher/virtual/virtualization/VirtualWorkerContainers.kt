package com.cipher.virtual.virtualization

/**
 * Dedicated multi-process container Activities mapped to worker process slots `:w0` through `:w7`.
 *
 * Each subclass is bound in `AndroidManifest.xml` to a distinct `android:process=":wX"` and
 * `android:taskAffinity`, allowing each cloned workspace slot to run inside its own Linux OS
 * process and isolated [com.cipher.virtual.data.local.WorkspaceContextWrapper] storage tree without
 * touching single-process host DataStore or Room instances from worker processes.
 */
class VirtualWorkerContainerW0 : VirtualAppContainerActivity()
class VirtualWorkerContainerW1 : VirtualAppContainerActivity()
class VirtualWorkerContainerW2 : VirtualAppContainerActivity()
class VirtualWorkerContainerW3 : VirtualAppContainerActivity()
class VirtualWorkerContainerW4 : VirtualAppContainerActivity()
class VirtualWorkerContainerW5 : VirtualAppContainerActivity()
class VirtualWorkerContainerW6 : VirtualAppContainerActivity()
class VirtualWorkerContainerW7 : VirtualAppContainerActivity()

fun resolveWorkerContainerClass(workerSlot: String?): Class<out VirtualAppContainerActivity> =
    when (workerSlot) {
        ":w0" -> VirtualWorkerContainerW0::class.java
        ":w1" -> VirtualWorkerContainerW1::class.java
        ":w2" -> VirtualWorkerContainerW2::class.java
        ":w3" -> VirtualWorkerContainerW3::class.java
        ":w4" -> VirtualWorkerContainerW4::class.java
        ":w5" -> VirtualWorkerContainerW5::class.java
        ":w6" -> VirtualWorkerContainerW6::class.java
        ":w7" -> VirtualWorkerContainerW7::class.java
        else -> VirtualWorkerContainerW0::class.java
    }
