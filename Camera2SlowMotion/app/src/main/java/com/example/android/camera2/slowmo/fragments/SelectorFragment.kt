/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2.slowmo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.camera.utils.GenericListAdapter
import com.example.android.camera2.slowmo.BuildConfig
import com.example.android.camera2.slowmo.CameraStoreViewModel
import com.example.android.camera2.slowmo.R

/**
 * In this [Fragment] we let users pick a camera, size and FPS to use for high
 * speed video recording
 */
class SelectorFragment : Fragment() {

  private lateinit var viewModel: CameraStoreViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
      viewModel = ViewModelProvider(requireActivity())[CameraStoreViewModel::class.java]
      return RecyclerView(requireContext())
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view as RecyclerView
        view.apply {
            Log.d("TAG", "LAST RECORDED VIDEO IS THIS!! " + viewModel.lastRecorded)
            layoutManager = LinearLayoutManager(requireContext())

            val cameraManager =
                    requireContext().getSystemService(Context.CAMERA_SERVICE) as CameraManager

            val cameraList = enumerateHighSpeedCameras(cameraManager).toMutableList()
            if (viewModel.lastOpFile != null) {
              Log.d("TAG", "ADDING BUTTON TO THIS!! " + viewModel.lastRecorded)
              cameraList += CameraInfo("Last recorded", "b", Size(100, 100), 100)
            }
            Log.d("Camera list ", cameraList.toString())
            val layoutId = android.R.layout.simple_list_item_1
            adapter = GenericListAdapter(cameraList, itemLayoutId = layoutId) { view, item, _ ->
                view.findViewById<TextView>(android.R.id.text1).text = item.title
                view.setOnClickListener {
                   if(item.title == "Last recorded") {
                       startActivity(Intent().apply {
                         action = Intent.ACTION_VIEW
                         type = MimeTypeMap.getSingleton()
                           .getMimeTypeFromExtension(viewModel.lastRecorded)
                         val authority = "${BuildConfig.APPLICATION_ID}.provider"
                         data = FileProvider.getUriForFile(view.context, authority, viewModel.lastOpFile!!)
                         flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                           Intent.FLAG_ACTIVITY_CLEAR_TOP
                       })
                   } else {
                     Navigation.findNavController(requireActivity(), R.id.fragment_container)
                       .navigate(SelectorFragmentDirections.actionSelectorToCamera(
                         item.cameraId, item.size.width, item.size.height, item.fps))
                   }
                }
            }
        }
    }

    companion object {

        private data class CameraInfo(
                val title: String,
                val cameraId: String,
                val size: Size,
                val fps: Int)

        /** Converts a lens orientation enum into a human-readable string */
        private fun lensOrientationString(value: Int) = when(value) {
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        /** Lists all high speed cameras and supported resolution and FPS combinations */
        @SuppressLint("InlinedApi")
        private fun enumerateHighSpeedCameras(cameraManager: CameraManager): List<CameraInfo> {
            val availableCameras: MutableList<CameraInfo> = mutableListOf()

            // Iterate over the list of cameras and add those with high speed video recording
            //  capability to our output. This function only returns those cameras that declare
            //  constrained high speed video recording, but some cameras may be capable of doing
            //  unconstrained video recording with high enough FPS for some use cases and they will
            //  not necessarily declare constrained high speed video capability.
            cameraManager.cameraIdList.forEach { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val orientation = lensOrientationString(
                        characteristics.get(CameraCharacteristics.LENS_FACING)!!)

                // Query the available capabilities and output formats
                val capabilities = characteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)!!
                val cameraConfig = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

                // Return cameras that support constrained high video capability
                if (capabilities.contains(CameraCharacteristics
                                .REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
                    // For each camera, list its compatible sizes and FPS ranges
                    cameraConfig.highSpeedVideoSizes.forEach { size ->
                        cameraConfig.getHighSpeedVideoFpsRangesFor(size).forEach { fpsRange ->
                            val fps = fpsRange.upper
                            val info = CameraInfo(
                                    "$orientation ($id) $size $fps FPS", id, size, fps)

                            // Only report the highest FPS in the range, avoid duplicates
                            if (!availableCameras.contains(info)) availableCameras.add(info)
                        }
                    }
                }

            }

            return availableCameras
        }
    }
}
