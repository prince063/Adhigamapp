package com.example.adhigam.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adhigam.R
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.UserRole
import com.example.adhigam.data.repository.AuthRepository
import com.example.adhigam.data.repository.LoginPortal
import com.example.adhigam.data.session.Session
import com.example.adhigam.ui.components.PasswordResetDialog
import com.example.adhigam.ui.theme.AdhigamTheme
import kotlinx.coroutines.launch

private const val MOBILE_LENGTH = 10

// Copy and colours of the three web login pages (login.html, student-login.html, parent-login.html).
private data class PortalStyle(
    val tab: String,
    val tagline: String,
    val title: String,
    val subtitle: String,
    val mobileLabel: String,
    val mobilePlaceholder: String,
    val passwordPlaceholder: String,
    val buttonText: String,
    val deep: Color,      // web panel colour, used for the background tint and the card
    val light: Color,     // web tagline colour
    val accent: Color,    // web focus colour
    val button: Color     // web button colour
)

private fun styleFor(portal: LoginPortal): PortalStyle = when (portal) {
    LoginPortal.Staff -> PortalStyle(
        tab = "Staff",
        tagline = "Transforming Education with Technology",
        title = "Staff Login",
        subtitle = "Sign in with your registered mobile and password",
        mobileLabel = "Mobile No",
        mobilePlaceholder = "10-digit mobile number",
        passwordPlaceholder = "Your password",
        buttonText = "Sign In",
        deep = Color(0xFF0A1432),
        light = Color(0xFF93C5FD),
        accent = Color(0xFF3B82F6),
        button = Color(0xFF1E3FA5)
    )
    LoginPortal.Student -> PortalStyle(
        tab = "Student",
        tagline = "Student Portal",
        title = "Student Login",
        subtitle = "Sign in with your registered mobile and password",
        mobileLabel = "Mobile No",
        mobilePlaceholder = "Your registered mobile",
        passwordPlaceholder = "Your password",
        buttonText = "Student Sign In",
        deep = Color(0xFF052814),
        light = Color(0xFF6EE7B7),
        accent = Color(0xFF10B981),
        button = Color(0xFF059669)
    )
    LoginPortal.Parent -> PortalStyle(
        tab = "Parent",
        tagline = "Parent Portal",
        title = "Parent Login",
        subtitle = "Sign in using your child's registered mobile and password",
        mobileLabel = "Child's Mobile No",
        mobilePlaceholder = "Registered mobile number",
        passwordPlaceholder = "Password",
        buttonText = "Parent Sign In",
        deep = Color(0xFF1E0A3C),
        light = Color(0xFFC4B5FD),
        accent = Color(0xFF8B5CF6),
        button = Color(0xFF7C3AED)
    )
}

private val LabelColor = Color(0xFFCBD5E1)
private val MutedColor = Color(0xFF94A3B8)
private val PlaceholderColor = Color(0xFF64748B)
private val ErrorText = Color(0xFFFCA5A5)
private val ErrorRedBase = Color(0xFFEF4444)
private val ColorTween = tween<Color>(durationMillis = 450)

@Composable
fun LoginScreen(
    onLoginSuccess: (UserRole) -> Unit,
    authRepository: AuthRepository = AppContainer.authRepository
) {
    // The web portal opens on the staff login.
    var portal by rememberSaveable { mutableStateOf(LoginPortal.Staff) }
    var mobile by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showForgotPassword by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val style = styleFor(portal)

    // Portal colours cross-fade when switching tabs.
    val deep by animateColorAsState(style.deep, ColorTween, label = "deep")
    val light by animateColorAsState(style.light, ColorTween, label = "light")
    val accent by animateColorAsState(style.accent, ColorTween, label = "accent")
    val button by animateColorAsState(style.button, ColorTween, label = "button")

    // Card slides up once when the screen opens.
    val entrance = remember { MutableTransitionState(false).apply { targetState = true } }

    fun switchPortal(target: LoginPortal) {
        if (isLoading) return
        portal = target
        error = null
    }

    fun submit() {
        if (isLoading) return
        // Same message as the web pages.
        if (mobile.isBlank() || password.isEmpty()) {
            error = "Please enter mobile and password."
            return
        }
        focusManager.clearFocus()
        error = null
        isLoading = true
        scope.launch {
            try {
                val role = authRepository.login(portal, mobile, password)
                onLoginSuccess(role)
            } catch (e: Exception) {
                error = e.message ?: "Login failed. Check your connection."
            } finally {
                isLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Portal-tinted scrim: the photo shows through at the top and deepens behind the form.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to deep.copy(alpha = 0.55f),
                        0.45f to deep.copy(alpha = 0.85f),
                        1f to deep.copy(alpha = 0.97f)
                    )
                )
        )
        // Soft glow behind the logo.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .size(320.dp)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Brand(light = light, accent = accent, tagline = style.tagline)
            Spacer(Modifier.height(28.dp))

            AnimatedVisibility(
                visibleState = entrance,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 6 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                        .padding(22.dp)
                ) {
                    PortalSwitcher(selected = portal, accent = button, onSelect = ::switchPortal)
                    Spacer(Modifier.height(24.dp))

                    AnimatedContent(
                        targetState = style,
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                        label = "heading"
                    ) { s ->
                        Column {
                            Text(s.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(s.subtitle, color = MutedColor, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Spacer(Modifier.height(20.dp))

                    AnimatedVisibility(
                        visible = portal == LoginPortal.Parent,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Note(
                            "Use the same mobile number and password as your child's student account.",
                            icon = Icons.Outlined.Info,
                            color = light,
                            accent = accent,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = error != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Note(
                            error.orEmpty(),
                            icon = Icons.Outlined.ErrorOutline,
                            color = ErrorText,
                            accent = ErrorRedBase,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }

                    FieldLabel(style.mobileLabel)
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { input -> mobile = input.filter(Char::isDigit).take(MOBILE_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(style.mobilePlaceholder) },
                        leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(accent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                    )
                    Spacer(Modifier.height(16.dp))

                    FieldLabel("Password")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(style.passwordPlaceholder) },
                        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(accent),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )

                    TextButton(
                        onClick = { if (!isLoading) showForgotPassword = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Forgot password?", color = light, fontSize = 13.sp)
                    }

                    GradientButton(
                        text = style.buttonText,
                        isLoading = isLoading,
                        start = button,
                        end = accent,
                        onClick = ::submit
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Powered by VaidiTech", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
    }

    if (showForgotPassword) {
        PasswordResetDialog(
            title = "Reset ${style.tab} Password",
            initialMobile = mobile,
            mobileEditable = true,
            // Parents log in with the child's student account.
            studentAccount = portal != LoginPortal.Staff,
            authRepository = authRepository,
            onDismiss = { showForgotPassword = false },
            onDone = { message ->
                showForgotPassword = false
                password = ""
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
}

@Composable
private fun Brand(light: Color, accent: Color, tagline: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Logo inside two soft rings in the portal accent.
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f))
                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.school_logo),
                contentDescription = "School logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "AAdhigam School",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(tagline, color = light, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// Pill switch between the three login pages, with a sliding highlight.
@Composable
private fun PortalSwitcher(selected: LoginPortal, accent: Color, onSelect: (LoginPortal) -> Unit) {
    val portals = LoginPortal.entries
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.25f))
            .padding(4.dp)
    ) {
        val segment = maxWidth / portals.size
        val indicatorOffset by animateDpAsState(segment * portals.indexOf(selected), tween(300), label = "indicator")
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segment)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(accent)
        )
        Row(Modifier.fillMaxSize()) {
            portals.forEach { portal ->
                val isSelected = portal == selected
                val textColor by animateColorAsState(
                    if (isSelected) Color.White else MutedColor,
                    tween(300),
                    label = "tabText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab
                        ) { onSelect(portal) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        styleFor(portal).tab,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientButton(text: String, isLoading: Boolean, start: Color, end: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(start, end)), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text("Signing in…", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.3.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = LabelColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

// Tinted box used for the parent hint and for login errors.
@Composable
private fun Note(text: String, icon: ImageVector, color: Color, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun fieldColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedContainerColor = Color.White.copy(alpha = 0.12f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.07f),
    focusedBorderColor = accent,
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    cursorColor = accent,
    focusedLeadingIconColor = accent,
    unfocusedLeadingIconColor = MutedColor,
    focusedTrailingIconColor = LabelColor,
    unfocusedTrailingIconColor = MutedColor,
    focusedPlaceholderColor = PlaceholderColor,
    unfocusedPlaceholderColor = PlaceholderColor
)

// Preview only: draws the screen without a backend; signing in does nothing.
private object PreviewAuthRepository : AuthRepository {
    override suspend fun login(portal: LoginPortal, mobile: String, password: String): UserRole =
        throw IllegalStateException("Preview")
    override fun currentSession(): Session? = null
    override fun logout() = Unit
    override suspend fun sendPasswordOtp(mobile: String, studentAccount: Boolean) = Unit
    override suspend fun resetPassword(mobile: String, otp: Int, newPassword: String, studentAccount: Boolean) = Unit
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    AdhigamTheme {
        LoginScreen(onLoginSuccess = {}, authRepository = PreviewAuthRepository)
    }
}
