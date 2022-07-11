package com.example.opticalcharacterrecognition.presentation.takephoto

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toFile
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import com.example.opticalcharacterrecognition.MainActivity
import com.example.opticalcharacterrecognition.R
import com.example.opticalcharacterrecognition.databinding.FragmentTakePhotoBinding
import com.example.opticalcharacterrecognition.presentation.OcrViewModel
import com.example.opticalcharacterrecognition.presentation.overview.OverviewFragment
import com.example.opticalcharacterrecognition.presentation.permissions.PermissionsFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TakePhotoFragment : Fragment() {

    private val sharedViewModel: OcrViewModel by activityViewModels()

    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null

    private var _fragmentTakePhotoBinding: FragmentTakePhotoBinding? = null

    private val fragmentTakePhotoBinding
        get() = _fragmentTakePhotoBinding!!

    private lateinit var outputDirectory: File

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView TakePhoto")
        // Inflate the layout for this fragment
        _fragmentTakePhotoBinding = FragmentTakePhotoBinding.inflate(inflater, container, false)
        return fragmentTakePhotoBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated TakePhoto")
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = MainActivity.getOutputDirectory(requireContext())
        fragmentTakePhotoBinding.viewFinder.post {
            startCamera()
        }

        fragmentTakePhotoBinding.cameraCaptureButton.setOnClickListener{
            takePhoto()
        }
    }

    private fun takePhoto() {
        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .build()

        // Setup image capture listener which is triggered after photo has been taken
        imageCapture?.takePicture(
            outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
//                    savedUri.path
                    Log.d(TAG, "Photo capture succeeded: $savedUri")
                    Log.d(TAG, "Photo capture succeeded encoded Path: ${savedUri.encodedPath}")
                    Log.d(TAG, "Photo capture succeeded Path: ${savedUri.path}")
                    savedUri.encodedPath?.let { sharedViewModel.setRootDirectory(it) }
                    // Implicit broadcasts will be ignored for devices running API level >= 24
                    // so if you only target API level 24+ you can remove this statement
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        requireActivity().sendBroadcast(
                            Intent(Camera.ACTION_NEW_PICTURE, savedUri)
                        )
                    }

                    // If the folder selected is an external media directory, this is
                    // unnecessary but otherwise other apps will not be able to access our
                    // images unless we scan them using [MediaScannerConnection]
                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(savedUri.toFile().extension)

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(savedUri.toFile().absolutePath),
                        arrayOf(mimeType)
                    ) { _, uri ->
                        Log.d(TAG, "Image capture scanned into media store: $uri")
                    }
                    Log.d(TAG, "Navigate back")
                    Handler(Looper.getMainLooper()).post {
                        navigateToOverview()
                    }
                }


            })
    }

    private fun navigateToOverview() {
        Navigation.findNavController(requireActivity(), R.id.fragment_container)
            .navigateUp()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            // CameraProvider
            val cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            // CameraSelector - makes assumption that we're only using the back camera
            val cameraSelector =
                CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            // Preview. Only using the 4:3 ratio because this is the closest to our models
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentTakePhotoBinding.viewFinder.display.rotation)
                .build()

            imageCapture = ImageCapture.Builder()
                .build()

            try {
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
                preview.setSurfaceProvider(fragmentTakePhotoBinding.viewFinder.surfaceProvider)
            } catch (exc: Exception) {

            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume TakePhoto")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy TakePhoto")
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "OCRApp"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)
    }
}