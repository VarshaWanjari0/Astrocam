package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.TextureView
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var astroCamera: AstroCamera

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            setupCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        astroCamera = AstroCamera(this)
        astroCamera.startBackgroundThread()

        setContent {
            MyApplicationTheme {
                AstroCamUI(astroCamera)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        astroCamera.stopBackgroundThread()
    }

    private fun setupCamera() {
        // Handled in Compose AndroidView
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstroCamUI(camera: AstroCamera) {
    var showSettings by remember { mutableStateOf(false) }

    val bgMain = Color(0xFF050505)
    val bgControls = Color(0xFF0A0A0A)
    val bgZinc900 = Color(0xFF18181B)
    val bgZinc800 = Color(0xFF27272A)
    val colorIndigo500 = Color(0xFF6366F1)
    val colorIndigo400 = Color(0xFF818CF8)
    val colorRed500 = Color(0xFFEF4444)
    val borderWhite5 = Color.White.copy(alpha = 0.05f)
    val borderWhite10 = Color.White.copy(alpha = 0.1f)

    Scaffold(
        containerColor = bgMain,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bgMain)
        ) {
            // Viewfinder Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    camera.openCamera(st, w, h)
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // HUD Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top HUD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // Stacking Status
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .border(1.dp, borderWhite10, RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "STACKING STATUS",
                                color = colorIndigo400,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).background(if(camera.capturing.value) colorRed500 else Color.Gray, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    camera.status.value,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Badges
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .border(1.dp, borderWhite10, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription=null, tint=Color.White, modifier=Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("NR ACTIVE", color = Color.White, fontSize = 12.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                    .border(1.dp, borderWhite10, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.GridView, contentDescription=null, tint=Color.White, modifier=Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("HOT PIXEL MAP", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // Level / Horizon Line
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.width(192.dp).height(1.dp).background(Color.White.copy(alpha = 0.2f))) {
                            Box(modifier = Modifier.align(Alignment.Center).width(4.dp).height(8.dp).background(colorIndigo500))
                        }
                    }

                    // Bottom HUD labels
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, borderWhite5, RoundedCornerShape(12.dp))
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ISO", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("${camera.iso.value}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SHUTTER", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            val expSec = camera.exposureTimeNs.value / 1_000_000_000f
                            Text("${String.format("%.1fs", expSec)}", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FOCUS", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                            Text(if(camera.focusDistance.value == 0f) "INF" else "${camera.focusDistance.value}", fontSize = 14.sp, color = colorIndigo400, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Controls Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgControls)
                    .border(1.dp, borderWhite5) // border-t
                    .padding(24.dp)
                    .padding(bottom = 8.dp)
            ) {
                // Manual Settings Dropdown
                Box(modifier = Modifier.padding(bottom = 24.dp)) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgZinc900, RoundedCornerShape(16.dp))
                                .border(1.dp, colorIndigo500.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .clickable { showSettings = !showSettings }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = colorIndigo400)
                                Spacer(Modifier.width(12.dp))
                                Text("Manual Adjustments", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                            Icon(if (showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Color.White)
                        }

                        if (showSettings) {
                            Spacer(Modifier.height(16.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgZinc900.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .border(1.dp, borderWhite5, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                // Focus Distance
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("FOCUS DISTANCE", fontSize = 12.sp, color = Color(0xFFA1A1AA))
                                        Text(if(camera.focusDistance.value == 0f) "Infinity" else "${camera.focusDistance.value}", fontSize = 12.sp, color = colorIndigo400)
                                    }
                                    Slider(
                                        value = camera.focusDistance.value,
                                        onValueChange = { camera.focusDistance.value = it; camera.updatePreview() },
                                        valueRange = 0f..10f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colorIndigo500,
                                            activeTrackColor = colorIndigo500,
                                            inactiveTrackColor = bgZinc800
                                        )
                                    )
                                }
                                
                                Spacer(Modifier.height(8.dp))
                                
                                // ISO and Shutter
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(bgZinc800, RoundedCornerShape(12.dp))
                                            .border(1.dp, borderWhite5, RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text("ISO SENSITIVITY", fontSize = 10.sp, color = Color(0xFF71717A), modifier = Modifier.padding(bottom = 4.dp))
                                        Slider(
                                            value = camera.iso.value.toFloat(),
                                            onValueChange = { camera.iso.value = it.toInt(); camera.updatePreview() },
                                            valueRange = 100f..3200f,
                                            steps = 30,
                                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = colorIndigo400)
                                        )
                                        Text("${camera.iso.value}", color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(bgZinc800, RoundedCornerShape(12.dp))
                                            .border(1.dp, borderWhite5, RoundedCornerShape(12.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text("SHUTTER (SEC)", fontSize = 10.sp, color = Color(0xFF71717A), modifier = Modifier.padding(bottom = 4.dp))
                                        Slider(
                                            value = camera.exposureTimeNs.value.toFloat(),
                                            onValueChange = { camera.exposureTimeNs.value = it.toLong(); camera.updatePreview() },
                                            valueRange = 1_000_000f..10_000_000_000f, // 1ms to 10s
                                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = colorIndigo400)
                                        )
                                        val strSec = String.format("%.1f", camera.exposureTimeNs.value / 1_000_000_000f)
                                        Text("$strSec", color = Color.White, fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.CenterHorizontally))
                                    }
                                }
                            }
                        }
                    }
                }

                // Shutter & Primary Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Timer Selection
                    Button(
                        onClick = {
                             camera.timerSeconds.value = if (camera.timerSeconds.value == 0) 3 else if (camera.timerSeconds.value == 3) 5 else if (camera.timerSeconds.value == 5) 10 else 0
                        },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = bgZinc900),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderWhite5),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Timer, contentDescription = "Timer", tint = Color.White, modifier = Modifier.size(20.dp).padding(bottom = 2.dp))
                            Text("${camera.timerSeconds.value}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorIndigo400)
                        }
                    }

                    // Capture Button
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (camera.capturing.value) {
                            // Pulse ring equivalent
                            Box(modifier = Modifier.size(96.dp).border(2.dp, colorIndigo500.copy(alpha=0.5f), CircleShape))
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White, CircleShape)
                                .clickable { camera.captureAstro(); showSettings = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .border(2.dp, Color.Black, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (camera.capturing.value) {
                                    Box(modifier = Modifier.size(12.dp).background(colorRed500, RoundedCornerShape(2.dp)))
                                }
                            }
                        }
                    }

                    // Frame Count / Stacking Options
                    Button(
                        onClick = {
                            camera.stackFrames.value = if (camera.stackFrames.value == 5) 10 else if (camera.stackFrames.value == 10) 20 else if (camera.stackFrames.value == 20) 50 else 5
                        },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = bgZinc900),
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderWhite5),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Layers, contentDescription = "Frames", tint = Color.White, modifier = Modifier.size(20.dp).padding(bottom = 2.dp))
                            Text("x${camera.stackFrames.value}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
            
            // Bottom Nav (Minimal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .border(1.dp, borderWhite5) // border-t
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = colorIndigo500)
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color(0xFF52525B)) // zinc-600
                Icon(Icons.Default.AutoFixNormal, contentDescription = "Enhance", tint = Color(0xFF52525B))
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF52525B))
            }
        }
    }
}

