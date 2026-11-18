package com.example.assignment3_rickmortyexplorer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

private fun Context.findActivity(): ComponentActivity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    throw IllegalStateException("Activity not found")
}

private val PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

// Ecrã novo: pré-visualização da câmara + captura. Devolve a foto (Base64) e as coordenadas.
@Composable
fun CameraScreen(
    locationName: String,
    onPhotoReady: (String, Double?, Double?) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    BackHandler(onBack = onClose)

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        cameraGranted = result[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) { permissionLauncher.launch(PERMISSIONS) }

    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        // Resolução moderada: poupa memória
        val selector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 960),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()
        ImageCapture.Builder().setResolutionSelector(selector).build()
    }

    // Liga a câmara (traseira; se não existir, a da frente) quando há permissão
    DisposableEffect(cameraGranted) {
        var provider: ProcessCameraProvider? = null
        if (cameraGranted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    try {
                        p.bindToLifecycle(
                            activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                    } catch (e: Exception) {
                        p.unbindAll()
                        p.bindToLifecycle(
                            activity, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture
                        )
                    }
                } catch (e: Exception) {
                    status = "Camera error: ${e.message}"
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { provider?.unbindAll() }
    }

    fun takePhoto() {
        if (busy) return
        busy = true
        status = "Taking photo..."
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val base64 = try {
                        image.toUprightBitmap().toBase64Jpeg()
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (base64 == null) {
                        status = "Could not process the photo"
                        busy = false
                        return
                    }
                    status = "Getting location..."
                    fetchLocation(context) { loc ->
                        onPhotoReady(base64, loc?.latitude, loc?.longitude)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    status = "Capture error: ${exception.message}"
                    busy = false
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Text(
            text = "Take a photo of: $locationName",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (cameraGranted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is needed")
                    Button(onClick = { permissionLauncher.launch(PERMISSIONS) }) {
                        Text("Grant permission")
                    }
                }
            }
        }

        status?.let { Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { takePhoto() },
                enabled = cameraGranted && !busy,
                modifier = Modifier.weight(1f)
            ) { Text("Capture") }
        }
    }
}

// Obtém latitude/longitude (devolve null se não houver permissão ou localização em 8 s)
@SuppressLint("MissingPermission")
fun fetchLocation(context: Context, onResult: (Location?) -> Unit) {
    val fine = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        onResult(null)
        return
    }

    var delivered = false
    val deliver: (Location?) -> Unit = { loc ->
        if (!delivered) {
            delivered = true
            onResult(loc)
        }
    }
    Handler(Looper.getMainLooper()).postDelayed({ deliver(null) }, 8000)

    try {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority =
            if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        client.getCurrentLocation(priority, CancellationTokenSource().token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    deliver(loc)
                } else {
                    client.lastLocation
                        .addOnSuccessListener { deliver(it) }
                        .addOnFailureListener { deliver(null) }
                }
            }
            .addOnFailureListener { deliver(null) }
    } catch (e: Exception) {
        deliver(null)
    }
}