/**
 * Native stub for Console.
 * Native platforms are not primary targets for KreeKt.
 */

package io.kreekt.util

/**
 * Native actual for console object.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual object console {
    actual fun log(message: String) {
        println(message)
    }
    
    actual fun warn(message: String) {
        println("WARN: $message")
    }
    
    actual fun error(message: String) {
        println("ERROR: $message")
    }
}
