/*
 * Copyright (c) 2018 Fondesa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fondesa.kpermissions.request.runtime

import android.app.Fragment
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import com.fondesa.kpermissions.extensions.arePermissionsGranted
import com.fondesa.kpermissions.extensions.flatString

/**
 * Created by antoniolig on 05/01/18.
 */
@RequiresApi(Build.VERSION_CODES.M)
class FragmentRuntimePermissionHandler : Fragment(), RuntimePermissionHandler {

    private var listener: RuntimePermissionHandler.Listener? = null

    private var isProcessingPermissions = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Retain the instance of the Fragment.
        retainInstance = true
    }

    override fun onDetach() {
        super.onDetach()
        // Avoid to retain the reference to the listener that can create a memory leak.
        // A leak can happen if the listener's instance can't be garbage collected due to
        // this Fragment's lifecycle (retainInstance = true).
        listener = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_CODE_PERMISSIONS || permissions.isEmpty()) {
            // Ignore the result if the request code doesn't match or
            // avoid the computation if there aren't processed permissions.
            return
        }

        // Now the Fragment is not processing the permissions anymore.
        isProcessingPermissions = false

        // Get the denied permissions.
        val deniedPermissions = permissions.filterIndexed { index, _ ->
            grantResults[index] == PackageManager.PERMISSION_DENIED
        }

        if (deniedPermissions.isNotEmpty()) {
            val permissionsWithRationale = permissionsThatShouldShowRationale(deniedPermissions.toTypedArray())
            val rationaleHandled = if (permissionsWithRationale.isNotEmpty()) {
                // Show rationale of permissions.
                dispatchPermissionsShouldShowRationale(permissionsWithRationale)
            } else false

            val permanentlyDeniedPermissions = deniedPermissions.minus(permissionsWithRationale).toTypedArray()
            if (!rationaleHandled && permanentlyDeniedPermissions.isNotEmpty()) {
                // Some permissions are permanently denied by the user.
                Log.d(TAG, "permissions permanently denied: ${permanentlyDeniedPermissions.flatString()}")
                dispatchPermissionsPermanentlyDenied(permanentlyDeniedPermissions)
            }
        } else {
            // All permissions are accepted.
            dispatchPermissionsAccepted(permissions)
        }
    }

    override fun handleRuntimePermissions(permissions: Array<out String>,
                                          listener: RuntimePermissionHandler.Listener) {
        val context = activity ?: throw NullPointerException("The activity mustn't be null.")
        // Assign the listener.
        this.listener = listener

        if (isProcessingPermissions) {
            // The Fragment can process only one request at the same time.
            return
        }

        if (!context.arePermissionsGranted(*permissions)) {
            val permissionsWithRationale = permissionsThatShouldShowRationale(permissions)
            val rationaleHandled = if (permissionsWithRationale.isNotEmpty()) {
                // Show rationale of permissions.
                dispatchPermissionsShouldShowRationale(permissionsWithRationale)
            } else false

            if (!rationaleHandled) {
                // Request the permissions.
                requestRuntimePermissions(permissions)
            }
        } else {
            // All permissions are accepted.
            dispatchPermissionsAccepted(permissions)
        }
    }

    override fun requestRuntimePermissions(permissions: Array<out String>) {
        // The Fragment is now processing some permissions.
        isProcessingPermissions = true
        Log.d(TAG, "requesting permissions: ${permissions.flatString()}")
        requestPermissions(permissions, REQ_CODE_PERMISSIONS)
    }

    private fun dispatchPermissionsAccepted(permissions: Array<out String>): Boolean =
            listener?.permissionsAccepted(permissions) ?: false

    private fun dispatchPermissionsPermanentlyDenied(permissions: Array<out String>): Boolean =
            listener?.permissionsPermanentlyDenied(permissions) ?: false

    private fun dispatchPermissionsShouldShowRationale(permissions: Array<out String>): Boolean =
            listener?.permissionsShouldShowRationale(permissions) ?: false

    private fun permissionsThatShouldShowRationale(permissions: Array<out String>): Array<out String> =
            permissions.filter {
                shouldShowRequestPermissionRationale(it)
            }.toTypedArray()

    companion object {
        private val TAG = FragmentRuntimePermissionHandler::class.java.simpleName
        private const val REQ_CODE_PERMISSIONS = 986
    }
}