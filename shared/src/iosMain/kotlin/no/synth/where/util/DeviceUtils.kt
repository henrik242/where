package no.synth.where.util

import platform.Foundation.NSProcessInfo

actual object DeviceUtils {
    actual fun isEmulator(): Boolean {
        return NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
    }
}
