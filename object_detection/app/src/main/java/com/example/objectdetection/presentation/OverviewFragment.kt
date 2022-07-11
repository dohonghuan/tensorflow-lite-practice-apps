package com.example.objectdetection.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.example.objectdetection.ObjectDetectionHelper
import com.example.objectdetection.R
import com.example.objectdetection.databinding.FragmentOverviewBinding
import com.example.objectdetection.presentation.permissions.PermissionsFragment
import org.tensorflow.lite.support.common.FileUtil
import java.lang.Exception
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OverviewFragment : Fragment(), ObjectDetectionHelper.DetectorListener {

    private lateinit var cameraExecutor: ExecutorService

    private var _fragmentOverviewBinding: FragmentOverviewBinding? = null

    private val fragmentOverviewBinding
        get() = _fragmentOverviewBinding!!

    private lateinit var bitmapBuffer: Bitmap

    private lateinit var objectDetectionHelper: ObjectDetectionHelper

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView")
        _fragmentOverviewBinding = FragmentOverviewBinding.inflate(inflater, container, false)
        return fragmentOverviewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        objectDetectionHelper = ObjectDetectionHelper(
            requireContext(),
            MODEL_PATH,
            FileUtil.loadLabels(requireContext(), LABEL_PATH),
            this)

        cameraExecutor = Executors.newSingleThreadExecutor()
        Log.d(TAG, "onViewCreated 1")
        fragmentOverviewBinding.viewFinder.post {
            Log.d(TAG, "onViewCreated 2")
            setupCamera()
        }

        Log.d(TAG, "onViewCreated 3")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(OverviewFragmentDirections.actionOverviewFragmentToPermissionsFragment())
        }
    }

    override fun onDestroyView() {
        _fragmentOverviewBinding = null
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupCamera() {
        Log.d(TAG, "setupCamera")

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
                    .setTargetRotation(fragmentOverviewBinding.viewFinder.display.rotation)
                    .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentOverviewBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { image ->
                if (!::bitmapBuffer.isInitialized) {
                    bitmapBuffer = Bitmap.createBitmap(
                        image.width,
                        image.height,
                        Bitmap.Config.ARGB_8888)
                    Log.d(TAG, "width: ${image.width}")
                    Log.d(TAG, "height: ${image.height}")
                }

                image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
                val imageRotation = image.imageInfo.rotationDegrees
                objectDetectionHelper.detect(bitmapBuffer, imageRotation)
            })

            cameraProvider.unbindAll()
            try {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                preview.setSurfaceProvider(fragmentOverviewBinding.viewFinder.surfaceProvider)
            } catch (exc: Exception) {
                Log.d(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    companion object {
        private const val TAG = "ObjectDetectionApp"
        private const val MODEL_PATH = "ssd_mobilenet_v1_1_metadata_1.tflite"
        private const val LABEL_PATH = "ssd_mobilenet_v1_1_labels.txt"
    }

    override fun onError(error: String) {
        Log.d(TAG, "onError callback")
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResult(
        result: List<ObjectDetectionHelper.ObjectPrediction>?,
        imageHeight: Int,
        imageWidth: Int) {
        Log.d(TAG, "onResult callback")
        Log.d(TAG, "Overview Size: ${fragmentOverviewBinding.viewFinder.width}")
        activity?.runOnUiThread {
            fragmentOverviewBinding.overlay.setResult(
                result ?: LinkedList<ObjectDetectionHelper.ObjectPrediction>(),
                imageHeight,
                imageWidth
            )

            fragmentOverviewBinding.overlay.invalidate()
        }
    }
}