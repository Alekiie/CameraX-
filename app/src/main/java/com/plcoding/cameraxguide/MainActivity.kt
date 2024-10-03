@file:OptIn(ExperimentalMaterial3Api::class)

package com.plcoding.cameraxguide

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plcoding.cameraxguide.ui.theme.CameraXGuideTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var selectedImage: Bitmap? by mutableStateOf(null)
    private var isFrontCamera by mutableStateOf(false) // Track camera state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(this, CAMERAX_PERMISSIONS, 0)
        }
        setContent {
            CameraXGuideTheme {
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
                    }
                }
                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 0.dp,
                    sheetContent = {
                        ImageGrid(
                            images = bitmaps,
                            onImageClick = { bitmap ->
                                selectedImage = bitmap // Set the selected image
                            }
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Icon button to switch camera
                        IconButton(
                            onClick = {
                                isFrontCamera = !isFrontCamera // Toggle camera state
                                controller.cameraSelector = if (isFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                            },
                            modifier = Modifier.offset(16.dp, 16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "Switch camera")
                        }

                        // Icon button to open the image gallery
                        IconButton(
                            onClick = {
                                scope.launch { scaffoldState.bottomSheetState.expand() }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Photo, contentDescription = "Open gallery")
                        }

                        // Button to take photo
                        IconButton(
                            onClick = {
                                takePhoto(controller = controller, onPhotoTaken = viewModel::onTakePhoto)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Take photo")
                        }

                        // Show full-screen image if one is selected
                        if (selectedImage != null) {
                            FullScreenImageViewer(
                                images = bitmaps,
                                selectedImage = selectedImage!!,
                                onDismiss = { selectedImage = null } // Reset selected image
                            )
                        }
                    }
                }
            }
        }
    }

    // Grid layout for displaying images
    @Composable
    fun ImageGrid(
        images: List<Bitmap>,
        onImageClick: (Bitmap) -> Unit,
        modifier: Modifier = Modifier
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier.padding(16.dp)
        ) {
            items(images) { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .padding(4.dp)
                        .clickable { onImageClick(image) }
                )
            }
        }
    }

    // Full-screen image viewer with sliding functionality
    @Composable
    fun FullScreenImageViewer(
        images: List<Bitmap>,
        selectedImage: Bitmap,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Create a state to manage the currently displayed image index
        val currentIndex = remember { mutableStateOf(images.indexOf(selectedImage)) }

        Box(modifier.fillMaxSize()) {
            // Display the current image in full-screen
            Image(
                bitmap = images[currentIndex.value].asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )

            // Add dismiss button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
            }

            // Implement swipe functionality to change images
            // Note: Consider using a library for better swipe gestures
            Modifier.pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (dragAmount.x > 0) {
                        // Swipe right to show previous image
                        if (currentIndex.value > 0) {
                            currentIndex.value--
                        }
                    } else if (dragAmount.x < 0) {
                        // Swipe left to show next image
                        if (currentIndex.value < images.size - 1) {
                            currentIndex.value++
                        }
                    }
                }
            }
        }
    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )

                    onPhotoTaken(rotatedBitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("Camera", "Couldn't take photo: ", exception)
                }
            }
        )
    }

    private fun hasRequiredPermissions(): Boolean {
        return CAMERAX_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                applicationContext,
                it
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}
