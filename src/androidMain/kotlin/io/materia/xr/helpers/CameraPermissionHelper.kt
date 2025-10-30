package io.materia.xr.helpers

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Helper to manage camera permission requests
 */
class CameraPermissionHelper {
    companion object {
        private const val CAMERA_PERMISSION_CODE = 0
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

        /**
         * Check if camera permission is granted
         */
        fun hasCameraPermission(activity: Activity): Boolean {
            return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED
        }

        /**
         * Request camera permission
         */
        fun requestCameraPermission(activity: Activity) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(CAMERA_PERMISSION), CAMERA_PERMISSION_CODE
            )
        }

        /**
         * Check if we should show permission rationale
         */
        fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION)
        }

        /**
         * Launch app settings when permission is permanently denied
         */
        fun launchPermissionSettings(activity: Activity) {
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", activity.packageName, null)
            activity.startActivity(intent)
        }

        /**
         * Check if user permanently denied the permission
         */
        fun isPermissionPermanentlyDenied(activity: Activity): Boolean {
            return !hasCameraPermission(activity) &&
                    !shouldShowRequestPermissionRationale(activity)
        }

        /**
         * Handle permission request result
         */
        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray,
            onGranted: () -> Unit,
            onDenied: () -> Unit
        ) {
            if (requestCode == CAMERA_PERMISSION_CODE) {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
        }
    }
}