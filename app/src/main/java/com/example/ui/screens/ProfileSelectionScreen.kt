package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.example.data.model.UserProfile
import com.example.ui.components.StreaTVLogo
import com.example.ui.viewmodel.IPTVViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction

// High-quality cinematic character avatar choices
data class AvatarOption(val id: String, val url: String, val name: String)

val avatarOptions = listOf(
    // OpenPeeps
    AvatarOption("openpeeps_alex", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Alex", "Alex"),
    AvatarOption("openpeeps_jordan", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jordan", "Jordan"),
    AvatarOption("openpeeps_taylor", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Taylor", "Taylor"),
    AvatarOption("openpeeps_morgan", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Morgan", "Morgan"),
    AvatarOption("openpeeps_sam", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Sam", "Sam"),
    AvatarOption("openpeeps_casey", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Casey", "Casey"),
    AvatarOption("openpeeps_robin", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Robin", "Robin"),
    AvatarOption("openpeeps_jamie", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jamie", "Jamie"),
    AvatarOption("openpeeps_chris", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Chris", "Chris"),
    AvatarOption("openpeeps_jesse", "https://api.dicebear.com/9.x/open-peeps/svg?seed=Jesse", "Jesse"),

    // FunEmoji
    AvatarOption("funemoji_happy", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Happy", "Happy"),
    AvatarOption("funemoji_cool", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Cool", "Cool"),
    AvatarOption("funemoji_smile", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Smile", "Smile"),
    AvatarOption("funemoji_wink", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Wink", "Wink"),
    AvatarOption("funemoji_love", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Love", "Love"),
    AvatarOption("funemoji_smart", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Smart", "Smart"),
    AvatarOption("funemoji_star", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Star", "Star"),
    AvatarOption("funemoji_angel", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Angel", "Angel"),
    AvatarOption("funemoji_laugh", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Laugh", "Laugh"),
    AvatarOption("funemoji_party", "https://api.dicebear.com/9.x/fun-emoji/svg?seed=Party", "Party"),

    // BigSmile
    AvatarOption("bigsmile_avatara", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarA", "AvatarA"),
    AvatarOption("bigsmile_avatarb", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarB", "AvatarB"),
    AvatarOption("bigsmile_avatarc", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarC", "AvatarC"),
    AvatarOption("bigsmile_avatard", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarD", "AvatarD"),
    AvatarOption("bigsmile_avatare", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarE", "AvatarE"),
    AvatarOption("bigsmile_avatarf", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarF", "AvatarF"),
    AvatarOption("bigsmile_avatarg", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarG", "AvatarG"),
    AvatarOption("bigsmile_avatarh", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarH", "AvatarH"),
    AvatarOption("bigsmile_avatari", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarI", "AvatarI"),
    AvatarOption("bigsmile_avatarj", "https://api.dicebear.com/9.x/big-smile/svg?seed=AvatarJ", "AvatarJ")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    viewModel: IPTVViewModel
) {
    val profiles by viewModel.profiles.collectAsState()
    var isInCreateMode by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var profileToDelete by remember { mutableStateOf<UserProfile?>(null) }
    
    // Create Profile Form States
    var newProfileName by remember { mutableStateOf("") }
    var selectedAvatarUrl by remember { mutableStateOf("") }
    
    // Focus Requesters for Leanback remote flow
    val nameInputFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }
    
    // Selection state for click animation feedback
    var selectedProfileId by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isLandscape = LocalContext.current.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val svgImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }
    var selectedStyle by remember { mutableStateOf("OpenPeeps") }
    val displayedAvatars = remember(selectedStyle) {
        avatarOptions.filter { 
            when (selectedStyle) {
                "OpenPeeps" -> it.id.startsWith("openpeeps")
                "FunEmoji" -> it.id.startsWith("funemoji")
                "BigSmile" -> it.id.startsWith("bigsmile")
                else -> true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF0C0707),
                        0.5f to Color(0xFF030101),
                        1.0f to Color(0xFF1D0303)
                    )
                )
            )
    ) {
        AnimatedContent(
            targetState = isInCreateMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "screen_transition"
        ) { createMode ->
            if (createMode) {
                // Profile Creation Screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 90.dp, bottom = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLandscape) {
                        // Side-by-side design for widescreen/tablets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left Column: Form Info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Novo Perfil",
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = (-1).sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Adicione um novo perfil para personalizar sua experiência cinematográfica no StreaTV.",
                                    fontSize = 15.sp,
                                    color = Color.White.copy(alpha = 0.65f),
                                    lineHeight = 22.sp
                                )
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                val isAvatarSelected = selectedAvatarUrl.isNotEmpty()
                                val isSaveEnabled = isAvatarSelected && newProfileName.isNotBlank()

                                Text(
                                    text = if (isAvatarSelected) "NOME DO PERFIL" else "NOME DO PERFIL (Selecione um avatar para liberar)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAvatarSelected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    enabled = isAvatarSelected,
                                    placeholder = { Text(if (isAvatarSelected) "Ex: João Silva" else "Selecione um avatar primeiro", color = Color.White.copy(alpha = if (isAvatarSelected) 0.35f else 0.15f)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        disabledTextColor = Color.White.copy(alpha = 0.3f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                                        disabledContainerColor = Color.White.copy(alpha = 0.01f),
                                        focusedBorderColor = Color(0xFFFF0D1A),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        disabledBorderColor = Color.White.copy(alpha = 0.05f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(nameInputFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && 
                                                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                                if (isSaveEnabled) {
                                                    saveButtonFocusRequester.requestFocus()
                                                }
                                                true
                                            } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                                if (isSaveEnabled) {
                                                    saveButtonFocusRequester.requestFocus()
                                                } else {
                                                    cancelButtonFocusRequester.requestFocus()
                                                }
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            }
                                        }
                                    )
                                )
                                Spacer(modifier = Modifier.height(36.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val saveInteractionSource = remember { MutableInteractionSource() }
                                    val isSaveHovered by saveInteractionSource.collectIsHoveredAsState()
                                    var isSaveFocused by remember { mutableStateOf(false) }
                                    val isSaveFocusedOrHovered = (isSaveFocused || isSaveHovered) && isSaveEnabled

                                    val saveScale by animateFloatAsState(
                                        targetValue = if (isSaveFocusedOrHovered) 1.08f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "save_scale"
                                    )

                                    Button(
                                        onClick = {
                                            if (isSaveEnabled) {
                                                viewModel.addNewProfile(newProfileName.trim(), selectedAvatarUrl)
                                                isInCreateMode = false
                                                newProfileName = ""
                                                selectedAvatarUrl = ""
                                            }
                                        },
                                        enabled = isSaveEnabled,
                                        interactionSource = saveInteractionSource,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF0D1A),
                                            disabledContainerColor = Color(0xFFFF0D1A).copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .graphicsLayer {
                                                scaleX = saveScale
                                                scaleY = saveScale
                                            }
                                            .hoverable(saveInteractionSource)
                                            .onFocusChanged { isSaveFocused = it.isFocused }
                                            .focusRequester(saveButtonFocusRequester)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                    nameInputFocusRequester.requestFocus()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusable()
                                    ) {
                                        Text("Salvar", color = Color.White.copy(alpha = if (isSaveEnabled) 1.0f else 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }

                                    val cancelInteractionSource = remember { MutableInteractionSource() }
                                    val isCancelHovered by cancelInteractionSource.collectIsHoveredAsState()
                                    var isCancelFocused by remember { mutableStateOf(false) }
                                    val isCancelFocusedOrHovered = isCancelFocused || isCancelHovered

                                    val cancelScale by animateFloatAsState(
                                        targetValue = if (isCancelFocusedOrHovered) 1.08f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                        label = "cancel_scale"
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            isInCreateMode = false
                                            newProfileName = ""
                                            selectedAvatarUrl = ""
                                        },
                                        interactionSource = cancelInteractionSource,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isCancelFocusedOrHovered) 0.5f else 0.2f)),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .graphicsLayer {
                                                scaleX = cancelScale
                                                scaleY = cancelScale
                                            }
                                            .hoverable(cancelInteractionSource)
                                            .onFocusChanged { isCancelFocused = it.isFocused }
                                            .focusRequester(cancelButtonFocusRequester)
                                            .onKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                    nameInputFocusRequester.requestFocus()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            .focusable()
                                    ) {
                                        Text("Cancelar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    }
                                }
                            }

                            // Right Column: Avatar Grid Card
                            Column(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .background(Color(0xFF0F0F0F).copy(alpha = 0.95f), RoundedCornerShape(16.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ESCOLHA SEU AVATAR",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.8f),
                                        letterSpacing = 1.5.sp
                                    )
                                }

                                // Category selection chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("OpenPeeps", "FunEmoji", "BigSmile").forEach { style ->
                                        val isStyleSelected = selectedStyle == style
                                        val chipInteractionSource = remember { MutableInteractionSource() }
                                        val isChipHovered by chipInteractionSource.collectIsHoveredAsState()
                                        var isChipFocused by remember { mutableStateOf(false) }
                                        val isChipFocusedOrHovered = isChipFocused || isChipHovered

                                        val chipScale by animateFloatAsState(
                                            targetValue = if (isChipFocusedOrHovered) 1.12f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_scale_land"
                                        )
                                        val chipTranslationY by animateDpAsState(
                                            targetValue = if (isChipFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_jump_land"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = chipScale
                                                    scaleY = chipScale
                                                    translationY = chipTranslationY.toPx()
                                                }
                                                .hoverable(chipInteractionSource)
                                                .onFocusChanged { isChipFocused = it.isFocused }
                                                .focusable()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(
                                                    if (isStyleSelected) Color(0xFFFF0D1A) 
                                                    else if (isChipFocusedOrHovered) Color.White.copy(alpha = 0.15f) 
                                                    else Color.White.copy(alpha = 0.08f)
                                                )
                                                .clickable(
                                                    interactionSource = chipInteractionSource,
                                                    indication = null
                                                ) { selectedStyle = style }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = style,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.height(240.dp)
                                ) {
                                    items(displayedAvatars) { avatar ->
                                        val isSelected = selectedAvatarUrl == avatar.url
                                        val avatarInteractionSource = remember { MutableInteractionSource() }
                                        val isAvatarHovered by avatarInteractionSource.collectIsHoveredAsState()
                                        var isAvatarFocused by remember { mutableStateOf(false) }
                                        val isAvatarFocusedOrHovered = isAvatarFocused || isAvatarHovered

                                        val avatarScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.18f else if (isAvatarFocusedOrHovered) 1.10f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_scale_land"
                                        )
                                        val avatarTranslationY by animateDpAsState(
                                            targetValue = if (isAvatarFocusedOrHovered) (-4).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_jump_land"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .graphicsLayer {
                                                    scaleX = avatarScale
                                                    scaleY = avatarScale
                                                    translationY = avatarTranslationY.toPx()
                                                }
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSelected) 3.dp else if (isAvatarFocusedOrHovered) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFFFF0D1A) else if (isAvatarFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                )
                                                .hoverable(avatarInteractionSource)
                                                .onFocusChanged { isAvatarFocused = it.isFocused }
                                                .focusable()
                                                .clickable(
                                                    interactionSource = avatarInteractionSource,
                                                    indication = null
                                                ) {
                                                    selectedAvatarUrl = avatar.url
                                                    coroutineScope.launch {
                                                        try {
                                                            nameInputFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = avatar.url,
                                                contentDescription = avatar.name,
                                                imageLoader = svgImageLoader,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Portrait Scrollable layout for Phones
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Novo Perfil",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Adicione um novo perfil para personalizar sua experiência no StreaTV.",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            val isAvatarSelected = selectedAvatarUrl.isNotEmpty()
                            val isSaveEnabled = isAvatarSelected && newProfileName.isNotBlank()

                            Text(
                                text = if (isAvatarSelected) "NOME DO PERFIL" else "NOME DO PERFIL (Selecione um avatar para liberar)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAvatarSelected) Color.White.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.2f),
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = newProfileName,
                                onValueChange = { newProfileName = it },
                                enabled = isAvatarSelected,
                                placeholder = { Text(if (isAvatarSelected) "Ex: João Silva" else "Selecione um avatar primeiro", color = Color.White.copy(alpha = if (isAvatarSelected) 0.35f else 0.15f)) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.White.copy(alpha = 0.3f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                                    disabledContainerColor = Color.White.copy(alpha = 0.01f),
                                    focusedBorderColor = Color(0xFFFF0D1A),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                    disabledBorderColor = Color.White.copy(alpha = 0.05f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(nameInputFocusRequester)
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown && 
                                            (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            }
                                            true
                                        } else if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                                            if (isSaveEnabled) {
                                                saveButtonFocusRequester.requestFocus()
                                            } else {
                                                cancelButtonFocusRequester.requestFocus()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (isSaveEnabled) {
                                            saveButtonFocusRequester.requestFocus()
                                        }
                                    }
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Avatar Grid Panel
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F0F0F).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "ESCOLHA SEU AVATAR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White.copy(alpha = 0.8f),
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                // Category selection chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf("OpenPeeps", "FunEmoji", "BigSmile").forEach { style ->
                                        val isStyleSelected = selectedStyle == style
                                        val chipInteractionSource = remember { MutableInteractionSource() }
                                        val isChipHovered by chipInteractionSource.collectIsHoveredAsState()
                                        var isChipFocused by remember { mutableStateOf(false) }
                                        val isChipFocusedOrHovered = isChipFocused || isChipHovered

                                        val chipScale by animateFloatAsState(
                                            targetValue = if (isChipFocusedOrHovered) 1.12f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_scale_port"
                                        )
                                        val chipTranslationY by animateDpAsState(
                                            targetValue = if (isChipFocusedOrHovered) (-2).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "chip_jump_port"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    scaleX = chipScale
                                                    scaleY = chipScale
                                                    translationY = chipTranslationY.toPx()
                                                }
                                                .hoverable(chipInteractionSource)
                                                .onFocusChanged { isChipFocused = it.isFocused }
                                                .focusable()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(
                                                    if (isStyleSelected) Color(0xFFFF0D1A) 
                                                    else if (isChipFocusedOrHovered) Color.White.copy(alpha = 0.15f) 
                                                    else Color.White.copy(alpha = 0.08f)
                                                )
                                                .clickable(
                                                    interactionSource = chipInteractionSource,
                                                    indication = null
                                                ) { selectedStyle = style }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = style,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.height(180.dp)
                                ) {
                                    items(displayedAvatars) { avatar ->
                                        val isSelected = selectedAvatarUrl == avatar.url
                                        val avatarInteractionSource = remember { MutableInteractionSource() }
                                        val isAvatarHovered by avatarInteractionSource.collectIsHoveredAsState()
                                        var isAvatarFocused by remember { mutableStateOf(false) }
                                        val isAvatarFocusedOrHovered = isAvatarFocused || isAvatarHovered

                                        val avatarScale by animateFloatAsState(
                                            targetValue = if (isSelected) 1.18f else if (isAvatarFocusedOrHovered) 1.10f else 1.0f,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_scale_port"
                                        )
                                        val avatarTranslationY by animateDpAsState(
                                            targetValue = if (isAvatarFocusedOrHovered) (-4).dp else 0.dp,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                            label = "avatar_jump_port"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .graphicsLayer {
                                                    scaleX = avatarScale
                                                    scaleY = avatarScale
                                                    translationY = avatarTranslationY.toPx()
                                                }
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(
                                                    width = if (isSelected) 3.dp else if (isAvatarFocusedOrHovered) 2.dp else 1.dp,
                                                    color = if (isSelected) Color(0xFFFF0D1A) else if (isAvatarFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                    shape = CircleShape
                                                )
                                                .hoverable(avatarInteractionSource)
                                                .onFocusChanged { isAvatarFocused = it.isFocused }
                                                .focusable()
                                                .clickable(
                                                    interactionSource = avatarInteractionSource,
                                                    indication = null
                                                ) {
                                                    selectedAvatarUrl = avatar.url
                                                    coroutineScope.launch {
                                                        try {
                                                            nameInputFocusRequester.requestFocus()
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    }
                                                }
                                        ) {
                                            AsyncImage(
                                                model = avatar.url,
                                                contentDescription = avatar.name,
                                                imageLoader = svgImageLoader,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(28.dp))

                            // Buttons Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val cancelInteractionSource = remember { MutableInteractionSource() }
                                val isCancelHovered by cancelInteractionSource.collectIsHoveredAsState()
                                var isCancelFocused by remember { mutableStateOf(false) }
                                val isCancelFocusedOrHovered = isCancelFocused || isCancelHovered

                                val cancelScale by animateFloatAsState(
                                    targetValue = if (isCancelFocusedOrHovered) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "cancel_scale_port"
                                )

                                OutlinedButton(
                                    onClick = {
                                        isInCreateMode = false
                                        newProfileName = ""
                                        selectedAvatarUrl = ""
                                    },
                                    interactionSource = cancelInteractionSource,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (isCancelFocusedOrHovered) 0.5f else 0.2f)),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .graphicsLayer {
                                            scaleX = cancelScale
                                            scaleY = cancelScale
                                        }
                                        .hoverable(cancelInteractionSource)
                                        .onFocusChanged { isCancelFocused = it.isFocused }
                                        .focusRequester(cancelButtonFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                nameInputFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable()
                                ) {
                                    Text("Cancelar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                }

                                val saveInteractionSource = remember { MutableInteractionSource() }
                                val isSaveHovered by saveInteractionSource.collectIsHoveredAsState()
                                var isSaveFocused by remember { mutableStateOf(false) }
                                val isSaveFocusedOrHovered = (isSaveFocused || isSaveHovered) && isSaveEnabled

                                val saveScale by animateFloatAsState(
                                    targetValue = if (isSaveFocusedOrHovered) 1.08f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "save_scale_port"
                                )

                                Button(
                                    onClick = {
                                        if (isSaveEnabled) {
                                            viewModel.addNewProfile(newProfileName.trim(), selectedAvatarUrl)
                                            isInCreateMode = false
                                            newProfileName = ""
                                            selectedAvatarUrl = ""
                                        }
                                    },
                                    enabled = isSaveEnabled,
                                    interactionSource = saveInteractionSource,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF0D1A),
                                        disabledContainerColor = Color(0xFFFF0D1A).copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .graphicsLayer {
                                            scaleX = saveScale
                                            scaleY = saveScale
                                        }
                                        .hoverable(saveInteractionSource)
                                        .onFocusChanged { isSaveFocused = it.isFocused }
                                        .focusRequester(saveButtonFocusRequester)
                                        .onKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                                nameInputFocusRequester.requestFocus()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                        .focusable()
                                ) {
                                    Text("Salvar", color = Color.White.copy(alpha = if (isSaveEnabled) 1.0f else 0.5f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            } else {
                // Profile Selection View
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "Quem está assistindo?",
                            fontSize = if (isLandscape) 40.sp else 30.sp,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selecione um perfil para começar sua experiência cinema.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(48.dp))

                        // Horizontally scrollable Row for standard TV experience
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                        ) {
                            profiles.forEach { profile ->
                                val profileInteractionSource = remember { MutableInteractionSource() }
                                val isProfileHovered by profileInteractionSource.collectIsHoveredAsState()
                                var isCardFocused by remember { mutableStateOf(false) }
                                val isFocusedOrHovered = isCardFocused || isProfileHovered
                                val isSelected = selectedProfileId == profile.id

                                // Dynamic scale & bounce animation targeting the "salto e zoom ao selecionar"
                                val scale by animateFloatAsState(
                                    targetValue = if (isSelected) 1.22f else if (isFocusedOrHovered) 1.12f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "profile_scale"
                                )

                                val translationY by animateDpAsState(
                                    targetValue = if (isSelected) (-16).dp else if (isFocusedOrHovered) (-8).dp else 0.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "profile_jump"
                                )

                                val colorHex = try {
                                    Color(android.graphics.Color.parseColor(profile.avatarColorHex))
                                } catch (e: Exception) {
                                    Color(0xFFFF0D1A)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(115.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            this.translationY = translationY.toPx()
                                        }
                                        .hoverable(profileInteractionSource)
                                        .onFocusChanged { isCardFocused = it.isFocused }
                                        .focusable()
                                        .clickable(
                                            interactionSource = profileInteractionSource,
                                            indication = null
                                        ) {
                                            if (isDeleteMode) {
                                                profileToDelete = profile
                                            } else {
                                                selectedProfileId = profile.id
                                                coroutineScope.launch {
                                                    delay(600) // let the bounce and zoom play nicely
                                                    viewModel.selectProfile(profile)
                                                }
                                            }
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(colorHex)
                                            .border(
                                                width = if (isFocusedOrHovered) 3.dp else 1.5.dp,
                                                color = if (isFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profile.avatarIconName.startsWith("http")) {
                                            SubcomposeAsyncImage(
                                                model = profile.avatarIconName,
                                                contentDescription = profile.name,
                                                imageLoader = svgImageLoader,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                                loading = {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.White.copy(alpha = 0.05f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = Color(0xFFFF0D1A),
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(colorHex),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = profile.name.take(1).uppercase(),
                                                            color = Color.White,
                                                            fontSize = 32.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isDeleteMode) Icons.Default.Delete else Icons.Default.Person,
                                                contentDescription = profile.name,
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = profile.name,
                                        color = if (isFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        fontWeight = if (isFocusedOrHovered) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Add Profile circular card
                            if (profiles.size < 5) {
                                val addInteractionSource = remember { MutableInteractionSource() }
                                val isAddHovered by addInteractionSource.collectIsHoveredAsState()
                                var isAddFocused by remember { mutableStateOf(false) }
                                val isAddFocusedOrHovered = isAddFocused || isAddHovered

                                val addScale by animateFloatAsState(
                                    targetValue = if (isAddFocusedOrHovered) 1.12f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "add_scale"
                                )
                                val addTranslationY by animateDpAsState(
                                    targetValue = if (isAddFocusedOrHovered) (-8).dp else 0.dp,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "add_jump"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(115.dp)
                                        .graphicsLayer {
                                            scaleX = addScale
                                            scaleY = addScale
                                            translationY = addTranslationY.toPx()
                                        }
                                        .hoverable(addInteractionSource)
                                        .onFocusChanged { isAddFocused = it.isFocused }
                                        .focusable()
                                        .clickable(
                                            interactionSource = addInteractionSource,
                                            indication = null
                                        ) { isInCreateMode = true }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.03f))
                                            .border(
                                                width = if (isAddFocusedOrHovered) 3.dp else 1.5.dp,
                                                color = if (isAddFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.15f),
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Adicionar Perfil",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Novo Perfil",
                                        color = if (isAddFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }

                    // Bottom Navigation Buttons styled beautifully matching the mockup
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp)
                    ) {
                        // Gerenciar Perfis
                        val manageInteractionSource = remember { MutableInteractionSource() }
                        val isManageHovered by manageInteractionSource.collectIsHoveredAsState()
                        var isManageFocused by remember { mutableStateOf(false) }
                        val isManageFocusedOrHovered = isManageFocused || isManageHovered

                        val manageScale by animateFloatAsState(
                            targetValue = if (isManageFocusedOrHovered) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "manage_scale"
                        )
                        val manageTranslationY by animateDpAsState(
                            targetValue = if (isManageFocusedOrHovered) (-4).dp else 0.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "manage_jump"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = manageScale
                                    scaleY = manageScale
                                    translationY = manageTranslationY.toPx()
                                }
                                .hoverable(manageInteractionSource)
                                .onFocusChanged { isManageFocused = it.isFocused }
                                .focusable()
                                .clickable(
                                    interactionSource = manageInteractionSource,
                                    indication = null
                                ) { isDeleteMode = !isDeleteMode }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Gerenciar Perfis",
                                tint = if (isDeleteMode) Color(0xFFFF0D1A) else if (isManageFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDeleteMode) "CONCLUIR GERENCIAMENTO" else "GERENCIAR PERFIS",
                                color = if (isDeleteMode) Color(0xFFFF0D1A) else if (isManageFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }

                        // Ajuda
                        val helpInteractionSource = remember { MutableInteractionSource() }
                        val isHelpHovered by helpInteractionSource.collectIsHoveredAsState()
                        var isHelpFocused by remember { mutableStateOf(false) }
                        val isHelpFocusedOrHovered = isHelpFocused || isHelpHovered

                        val helpScale by animateFloatAsState(
                            targetValue = if (isHelpFocusedOrHovered) 1.15f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "help_scale"
                        )
                        val helpTranslationY by animateDpAsState(
                            targetValue = if (isHelpFocusedOrHovered) (-4).dp else 0.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "help_jump"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = helpScale
                                    scaleY = helpScale
                                    translationY = helpTranslationY.toPx()
                                }
                                .hoverable(helpInteractionSource)
                                .onFocusChanged { isHelpFocused = it.isFocused }
                                .focusable()
                                .clickable(
                                    interactionSource = helpInteractionSource,
                                    indication = null
                                ) { /* Decorative help */ }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "Ajuda",
                                tint = if (isHelpFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AJUDA",
                                color = if (isHelpFocusedOrHovered) Color.White else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Cinematic Material 3 Deletion Confirmation Dialog
        if (profileToDelete != null) {
            AlertDialog(
                onDismissRequest = { profileToDelete = null },
                title = {
                    Text(
                        text = "Excluir Perfil?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Text(
                        text = "Tem certeza de que deseja excluir permanentemente o perfil \"${profileToDelete?.name}\"? Esta ação não pode ser desfeita.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                },
                containerColor = Color(0xFF141414),
                shape = RoundedCornerShape(16.dp),
                confirmButton = {
                    Button(
                        onClick = {
                            profileToDelete?.let {
                                viewModel.removeProfile(it)
                            }
                            profileToDelete = null
                            isDeleteMode = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0D1A)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Excluir", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { profileToDelete = null },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text("Cancelar", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}
