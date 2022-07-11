package com.example.opticalcharacterrecognition.presentation.overview

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.media.ExifInterface
import android.media.Image
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.example.opticalcharacterrecognition.OcrHelper
import com.example.opticalcharacterrecognition.R
import com.example.opticalcharacterrecognition.databinding.FragmentOverviewBinding
import com.example.opticalcharacterrecognition.presentation.OcrViewModel
import com.example.opticalcharacterrecognition.presentation.permissions.PermissionsFragment
import com.google.android.material.chip.Chip
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class OverviewFragment : Fragment(), OcrHelper.OcrListener {

    private val sharedViewModel: OcrViewModel by activityViewModels()
    private lateinit var currentPhotoPath: String
    private var _fragmentOverviewBinding: FragmentOverviewBinding? = null
    private val fragmentOverviewBinding
        get() = _fragmentOverviewBinding!!

    private lateinit var ocrModel: OcrHelper



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate OverView")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView OverView")
        _fragmentOverviewBinding = FragmentOverviewBinding.inflate(
            inflater,
            container,
            false
        )

        fragmentOverviewBinding.labelTextFound.visibility = INVISIBLE
        fragmentOverviewBinding.buttonTakePhoto.setOnClickListener{onClickListener(0)}
        fragmentOverviewBinding.imageSample1.setOnClickListener{onClickListener(1)}
        fragmentOverviewBinding.imageSample2.setOnClickListener{onClickListener(2)}
        fragmentOverviewBinding.imageSample3.setOnClickListener{onClickListener(3)}
        return fragmentOverviewBinding.root
    }

    private fun onClickListener(id: Int) {
        when (id) {
            0 -> {

                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(OverviewFragmentDirections.actionOverviewFragmentToTakePhotoFragment())
            }
            1 -> {
                setViewAndDetect(getSampleImage(R.drawable.sample1))
            }
            2 -> {
                setViewAndDetect(getSampleImage(R.drawable.sample2))
            }
            3 -> {
                setViewAndDetect(getSampleImage(R.drawable.sample3))
            }
        }
    }


    /**
     * setViewAndDetect(bitmap: Bitmap)
     *      Set image to view and call object detection
     */
    private fun setViewAndDetect(bitmap: Bitmap) {
        // Display capture image
        fragmentOverviewBinding.imagePlaceholder.setImageBitmap(bitmap)
        fragmentOverviewBinding.textPlaceholder.visibility = View.INVISIBLE

        ocrModel.process(bitmap)
    }

    /**
     * getSampleImage():
     *      Get image form drawable and convert to bitmap.
     */
    private fun getSampleImage(drawable: Int): Bitmap {

        return BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inMutable = true
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated OverView")
        super.onViewCreated(view, savedInstanceState)

        ocrModel = OcrHelper(requireContext(),
            DETECTION_MODEL_PATH,
            RECOGNITION_MODEL_PATH,
        this)

        if (!sharedViewModel.getRootDirectory().isNullOrEmpty()) {
            Log.d(TAG, "OK")
//            fragmentOverviewBinding.imagePlaceholder.setImageURI(sharedViewModel.getRootDirectory().toUri())
            currentPhotoPath = sharedViewModel.getRootDirectory()
            val bitmap = getCapturedImage()
            fragmentOverviewBinding.imagePlaceholder.setImageBitmap(bitmap)

            fragmentOverviewBinding.textPlaceholder.visibility = INVISIBLE

            ocrModel.process(bitmap)
        } else {
            Log.d(TAG, "NULL EMPTY")
        }
    }

    /**
     * getCapturedImage():
     *      Decodes and crops the captured image from camera.
     */
    private fun getCapturedImage(): Bitmap {
        Log.d(TAG, "path: $currentPhotoPath")
//        currentPhotoPath = "storage/emulated/0/Android/media/com.example.opticalcharacterrecognition/OpticalCharacterRecognition/2022-07-09-11-10-53-921.jpg"
        // Get the dimensions of the View
        val targetW: Int = 320*2//1080//fragmentOverviewBinding.imagePlaceholder.width
        val targetH: Int = 320*2//1191//fragmentOverviewBinding.imagePlaceholder.height
        Log.d(TAG, "width: ${fragmentOverviewBinding.imagePlaceholder.width}")
        Log.d(TAG, "height: ${fragmentOverviewBinding.imagePlaceholder.height}")
//        fragmentOverviewBinding.imagePlaceholder.width
        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            BitmapFactory.decodeFile(currentPhotoPath, this)

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inMutable = true
        }
        val exifInterface = ExifInterface(currentPhotoPath)
        val orientation = exifInterface.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )

        val bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> {
                bitmap
            }
        }
    }

    /**
     * rotateImage():
     *     Decodes and crops the captured image from camera.
     */
    private fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    override fun onResume() {
        Log.d(TAG, "onResume OverView")
        super.onResume()

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(OverviewFragmentDirections.actionOverviewFragmentToPermissionsFragment())
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy OverView")
        _fragmentOverviewBinding = null
        super.onDestroy()
    }

    override fun onError(error: String) {
       Log.d(TAG, "Error OverView")
    }

    override fun onResult(detectedRects: ArrayList<RectF>,
                          detectedText: ArrayList<String>,
                          originBitmap: Bitmap) {
        Log.d(TAG, "onResult OverView")

        activity?.runOnUiThread {
            val imgResult = printDetectionResult(originBitmap, detectedRects, detectedText)
            fragmentOverviewBinding.imagePlaceholder.setImageBitmap(imgResult)
        }
    }

    private fun printDetectionResult(
        bitmap: Bitmap,
        detectedRects: ArrayList<RectF>,
        detectedText: ArrayList<String>
    ): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(outputBitmap)
        val pen = Paint()
        pen.textAlign = Paint.Align.LEFT

        detectedRects.forEach {
            pen.color = Color.RED
            pen.strokeWidth = 18F
            pen.style = Paint.Style.STROKE
            canvas.drawRect(it, pen)
        }

        fragmentOverviewBinding.chipGroup.removeAllViews()
        fragmentOverviewBinding.labelTextFound.visibility = VISIBLE
        if (detectedText.size > 0) {
            fragmentOverviewBinding.labelTextFound.text = getString(R.string.texts_found)
            detectedText.forEach {
                val chip = Chip(requireContext())
                chip.text = it
                chip.chipBackgroundColor = getColorStateListForChip(getRandomColor())
                chip.isClickable = false
                fragmentOverviewBinding.chipGroup.addView(chip)
            }
        } else {
            fragmentOverviewBinding.labelTextFound.text = getString(R.string.no_text_found)
        }
        fragmentOverviewBinding.chipGroup.parent.requestLayout()

        return outputBitmap
    }

    private fun getRandomColor(): Int {
        val random = Random()
        return Color.argb(
            (128),
            (255 * random.nextFloat()).toInt(),
            (255 * random.nextFloat()).toInt(),
            (255 * random.nextFloat()).toInt()
        )
    }

    private fun getColorStateListForChip(color: Int): ColorStateList {
        val states =
            arrayOf(
                intArrayOf(android.R.attr.state_enabled), // enabled
                intArrayOf(android.R.attr.state_pressed) // pressed
            )

        val colors = intArrayOf(color, color)
        return ColorStateList(states, colors)
    }

    companion object {
        private const val TAG = "OCRApp"
        private const val REQUEST_CODE = 100
        private const val DETECTION_MODEL_PATH = "lite-model_east-text-detector_fp16_1.tflite"
        private const val RECOGNITION_MODEL_PATH = "lite-model_keras-ocr_float16_2.tflite"
    }
}