package com.example.android.camera2.slowmo

import androidx.lifecycle.ViewModel
import java.io.File

class CameraStoreViewModel: ViewModel() {
  var lastRecorded: String? = null
  var lastOpFile: File? = null
}