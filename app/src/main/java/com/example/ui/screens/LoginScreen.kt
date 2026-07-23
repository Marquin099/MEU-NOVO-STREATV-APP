package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.StreaTVLogo
import com.example.ui.viewmodel.IPTVViewModel
import com.example.ui.viewmodel.SyncState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: IPTVViewModel
) {
    val serverUrl = "http://streatv.elementfx.com"
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val syncState by viewModel.syncState.collectAsState()

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Hide keyboard and clear focus when starting to sync/loading
    LaunchedEffect(syncState) {
        if (syncState is SyncState.Syncing) {
            try {
                keyboardController?.hide()
                focusManager.clearFocus()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Focus state trackers for styling high contrast glowing TV borders
    var userFocused by remember { mutableStateOf(false) }
    var passFocused by remember { mutableStateOf(false) }
    var loginFocused by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Smoothly scroll form when focused so keyboard never covers input fields
    LaunchedEffect(userFocused, passFocused, loginFocused) {
        if (userFocused) {
            delay(100)
            scrollState.animateScrollTo(0)
        } else if (passFocused || loginFocused) {
            delay(100)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Focus requesters for explicit D-pad Arrow Key flow (up/down)
    val userFocusRequester = remember { FocusRequester() }
    val passFocusRequester = remember { FocusRequester() }
    val loginFocusRequester = remember { FocusRequester() }

    // Interaction source for detecting clicks / presses (for the push down click effect)
    val buttonInteractionSource = remember { MutableInteractionSource() }
    val isButtonPressed by buttonInteractionSource.collectIsPressedAsState()

    // Auto-focus the username input field initially when the screen mounts after window animations settle
    LaunchedEffect(Unit) {
        delay(500)
        try {
            userFocusRequester.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Smooth Zoom scale animations for high-contrast D-pad navigation feel (8% zoom)
    val userScale by animateFloatAsState(
        targetValue = if (userFocused) 1.08f else 1.0f,
        animationSpec = tween(200),
        label = "user_scale"
    )

    val passScale by animateFloatAsState(
        targetValue = if (passFocused) 1.08f else 1.0f,
        animationSpec = tween(200),
        label = "pass_scale"
    )

    // Button scale combines 8% focus zoom and 6% press push-down click effect
    val buttonTargetScale = when {
        isButtonPressed -> if (loginFocused) 1.02f else 0.95f
        loginFocused -> 1.08f
        else -> 1.0f
    }
    
    val buttonScale by animateFloatAsState(
        targetValue = buttonTargetScale,
        animationSpec = tween(150),
        label = "button_scale"
    )

    // Replicate the beautiful dark background with cyan-teal and purple glow leaks from the image!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E)) // Deep dark premium blue-black base
            .drawBehind {
                // Top-right cyan light leak glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.25f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.25f),
                    radius = size.width * 0.55f
                )
                // Bottom-right deep purple light leak glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7B1FA2).copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.width * 0.5f
                    ),
                    center = Offset(size.width * 0.95f, size.height * 0.95f),
                    radius = size.width * 0.5f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 16.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Column: Modern Title and Login Form
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title and Subtitle matching "Inppario" styling in the image
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "StreaTV",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sua TV em qualquer lugar",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Username Input - styled as a white pill with dropdown icon on the right
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { 
                        Text(
                            text = "Usuário", 
                            color = Color(0xFF7F8C8D), 
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onNext = {
                            try {
                                passFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E2025),
                        unfocusedTextColor = Color(0xFF1E2025),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White,
                        cursorColor = Color(0xFF1E2025)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = Color(0xFF7F8C8D),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer(
                            scaleX = userScale,
                            scaleY = userScale
                        )
                        .focusRequester(userFocusRequester)
                        .onFocusChanged { userFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        passFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .border(
                            width = if (userFocused) 3.dp else 0.dp,
                            color = if (userFocused) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Input - styled as a white pill with dropdown icon on the right
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { 
                        Text(
                            text = "Senha", 
                            color = Color(0xFF7F8C8D), 
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            try {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            if (username.isNotEmpty() && password.isNotEmpty()) {
                                viewModel.performLogin(serverUrl.trim(), username.trim(), password.trim())
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1E2025),
                        unfocusedTextColor = Color(0xFF1E2025),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White,
                        cursorColor = Color(0xFF1E2025)
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = Color(0xFF7F8C8D),
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .graphicsLayer(
                            scaleX = passScale,
                            scaleY = passScale
                        )
                        .focusRequester(passFocusRequester)
                        .onFocusChanged { passFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        userFocusRequester.requestFocus()
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        loginFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .border(
                            width = if (passFocused) 3.dp else 0.dp,
                            color = if (passFocused) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(28.dp)
                        ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Login Trigger Button - Styled as a beautiful, smaller centered white pill button with dpad focus and push animation
                Button(
                    onClick = {
                        try {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            viewModel.performLogin(serverUrl.trim(), username.trim(), password.trim())
                        }
                    },
                    interactionSource = buttonInteractionSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1E2025)
                    ),
                    modifier = Modifier
                        .width(220.dp) // Beautifully sized smaller pill button as requested
                        .height(50.dp) // Sleek size
                        .graphicsLayer(
                            scaleX = buttonScale,
                            scaleY = buttonScale
                        )
                        .focusRequester(loginFocusRequester)
                        .onFocusChanged { loginFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        passFocusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                        .border(
                            width = if (loginFocused) 3.dp else 0.dp,
                            color = if (loginFocused) Color(0xFF00E5FF) else Color.Transparent,
                            shape = RoundedCornerShape(25.dp)
                        ),
                    shape = RoundedCornerShape(25.dp)
                ) {
                    Text(
                        text = "Entrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1E2025)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                // Muted secure server / SSL indicator below the button
                Text(
                    text = "Servidor: http://streatv.elementfx.com • Conexão Segura SSL",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Display login validation error if any
                if (syncState is SyncState.Error) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = (syncState as SyncState.Error).message,
                        color = Color(0xFFE50914),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            // Right Column: Gorgeous Cinema/IPTV 3D Tilted Showcase
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top-right Header Group (Colorful premium capsule + bold "IPTV" text)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp, end = 24.dp)
                ) {
                    // Beautiful gradient capsule
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF00E5FF), Color(0xFF7B1FA2))
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "STREATV",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "IPTV",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }

                // Main body of 3D tilted overlapping poster cards
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer(
                            rotationZ = -10f,
                            rotationY = -12f,
                            cameraDistance = 16f
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // --- TOP OVERLAPPING LARGE MOVIE CARDS ---
                    
                    // Card 3 (Tertiary, furthest back on right)
                    Box(
                        modifier = Modifier
                            .offset(x = 340.dp, y = (-20).dp)
                            .width(180.dp)
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=400&q=80",
                            contentDescription = "Showcase item 3",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    }

                    // Card 2 (Secondary, in middle)
                    Box(
                        modifier = Modifier
                            .offset(x = 170.dp, y = (-10).dp)
                            .width(180.dp)
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=400&q=80",
                            contentDescription = "Showcase item 2",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                        Text(
                            text = "STREATV CATALOGO",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }

                    // Card 1 (Primary, in front on the left)
                    Box(
                        modifier = Modifier
                            .offset(x = 0.dp, y = 0.dp)
                            .width(180.dp)
                            .height(250.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.DarkGray)
                    ) {
                        AsyncImage(
                            model = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?w=400&q=80",
                            contentDescription = "Showcase item 1",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                    )
                                )
                        )
                        Text(
                            text = "PREMIUM SELECTION",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        )
                    }

                    // --- BOTTOM OVERLAPPING PORTRAIT CAROUSEL ---
                    Row(
                        modifier = Modifier
                            .offset(x = (-30).dp, y = 175.dp),
                        horizontalArrangement = Arrangement.spacedBy((-16).dp)
                    ) {
                        val bottomImages = listOf(
                            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300&q=80",
                            "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=300&q=80",
                            "https://images.unsplash.com/photo-1478720143023-6bbff96d11f9?w=300&q=80",
                            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=300&q=80",
                            "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=300&q=80",
                            "https://images.unsplash.com/photo-1505686994434-e3cc5abf1330?w=300&q=80"
                        )
                        bottomImages.forEachIndexed { index, imageUrl ->
                            Box(
                                modifier = Modifier
                                    .width(85.dp)
                                    .height(115.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Gray)
                            ) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Catalog mini $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fullscreen Loading Overlay during Playlist download & extraction process
        if (syncState is SyncState.Syncing) {
            val syncing = syncState as SyncState.Syncing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black,
                                0.4f to Color(0xFF030101),
                                0.75f to Color(0xFF550000),
                                1.0f to Color(0xFFD6040C)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        StreaTVLogo(size = 140.dp, showText = true, animated = true)
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        Text(
                            text = syncing.message,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        
                        Spacer(modifier = Modifier.height(28.dp))
                        
                        // Styled progress bar mapping true download percent matching the red theme
                        Box(
                            modifier = Modifier
                                .width(320.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        ) {
                            LinearProgressIndicator(
                                progress = { syncing.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFF0D1A),
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "${(syncing.progress * 100).toInt()}%",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bottom premium watermark text matching the screenshot
                    Text(
                        text = "SUA EXPERIÊNCIA PREMIUM DE STREAMING",
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}
