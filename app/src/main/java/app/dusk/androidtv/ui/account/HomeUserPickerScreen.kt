package app.dusk.androidtv.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dusk.androidtv.R
import app.dusk.androidtv.data.plex.PlexHomeUser
import app.dusk.androidtv.ui.components.DuskIcon
import app.dusk.androidtv.ui.components.DuskSpinner
import app.dusk.androidtv.ui.components.DuskSwitch
import app.dusk.androidtv.ui.components.DuskTextButton
import app.dusk.androidtv.ui.components.duskTvFocusEffect
import app.dusk.androidtv.ui.theme.DuskTheme
import coil.compose.AsyncImage

/**
 * "Who’s Watching?" (`HomeUserPickerView`, rama tvOS): rejilla de perfiles de 250 pt,
 * opción "Remember This User" y, para perfiles con PIN, teclado numérico propio.
 */
@Composable
fun HomeUserPickerScreen(
    users: List<PlexHomeUser>,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    activeProfileId: String?,
    viewModel: HomeUserPickerViewModel = viewModel(factory = HomeUserPickerViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending = state.pendingUser

    LaunchedEffect(Unit) { viewModel.reset() }

    BackHandler(enabled = pending != null || canCancel) {
        if (pending != null) viewModel.leavePin() else onCancel()
    }

    Box(Modifier.fillMaxSize().background(DuskTheme.colors.background)) {
        if (pending != null) {
            PinEntry(user = pending, state = state, viewModel = viewModel)
        } else {
            ProfileSelection(
                users = users,
                state = state,
                activeProfileId = activeProfileId,
                canCancel = canCancel,
                viewModel = viewModel,
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
private fun ProfileSelection(
    users: List<PlexHomeUser>,
    state: HomeUserPickerState,
    activeProfileId: String?,
    canCancel: Boolean,
    viewModel: HomeUserPickerViewModel,
    onSignOut: () -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val firstCard = remember { FocusRequester() }
    val columns = users.size.coerceIn(1, 4)

    LaunchedEffect(Unit) { runCatching { firstCard.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = tv.dp(64), end = tv.dp(64), top = tv.dp(58), bottom = tv.dp(80)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tv.dp(34)),
    ) {
        // Cabecera
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(18)),
        ) {
            DuskIcon(R.drawable.ic_people, tv.dp(72), colors.textSecondary)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tv.dp(9)),
            ) {
                BasicText(text = "Who’s Watching?", style = type.pageTitle)
                BasicText(
                    text = "Choose a Plex Home user. Watch history and recommendations will stay with this profile.",
                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.widthIn(max = tv.dp(680)),
                )
            }
        }

        // Rejilla de perfiles (hasta 4 columnas)
        Column(verticalArrangement = Arrangement.spacedBy(tv.dp(34))) {
            users.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(tv.dp(34))) {
                    row.forEachIndexed { colIndex, user ->
                        HomeUserCard(
                            user = user,
                            isCurrent = user.stableProfileId == activeProfileId,
                            isSwitching = state.switchingProfileId == user.stableProfileId,
                            isDisabled = state.switchingProfileId != null,
                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstCard else null,
                            onClick = { viewModel.select(user) },
                        )
                    }
                }
            }
        }

        RememberRow(
            checked = state.rememberSelection,
            onToggle = { viewModel.setRemember(!state.rememberSelection) },
            modifier = Modifier.widthIn(max = tv.dp(1120)).fillMaxWidth(),
        )

        state.error?.let { message ->
            BasicText(
                text = message,
                style = type.body.copy(color = colors.error, textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(680)),
            )
        }

        if (!canCancel) {
            DuskTextButton(text = "Sign Out", onClick = onSignOut, tint = colors.error)
        }
    }
}

@Composable
private fun HomeUserCard(
    user: PlexHomeUser,
    isCurrent: Boolean,
    isSwitching: Boolean,
    isDisabled: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(28))
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Column(
        modifier = Modifier
            .width(tv.dp(250))
            .then(focusModifier)
            .duskTvFocusEffect(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(colors.surface)
            .border(
                width = tv.dp(1),
                color = if (isCurrent) colors.textPrimary.copy(alpha = 0.35f) else colors.textPrimary.copy(alpha = 0.05f),
                shape = shape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !isDisabled,
                onClick = onClick,
            )
            .padding(horizontal = tv.dp(14), vertical = tv.dp(20)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tv.dp(14)),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            PlexHomeUserAvatar(user = user, size = tv.dp(142))
            if (user.isProtected) {
                Box(
                    modifier = Modifier
                        .padding(tv.dp(2))
                        .clip(CircleShape)
                        .background(colors.surface)
                        .padding(tv.dp(8)),
                ) {
                    DuskIcon(R.drawable.ic_lock, tv.dp(22), colors.textPrimary)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(tv.dp(6), Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = user.displayName,
                style = type.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCurrent) DuskIcon(R.drawable.ic_check_circle, tv.dp(23), colors.textSecondary)
        }
        Box(Modifier.height(tv.dp(24)), contentAlignment = Alignment.Center) {
            if (isSwitching) DuskSpinner(size = tv.dp(24), color = colors.textSecondary)
        }
    }
}

@Composable
fun PlexHomeUserAvatar(user: PlexHomeUser, size: Dp) {
    val colors = DuskTheme.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.textSecondary.copy(alpha = 0.14f))
            .border(DuskTheme.tv.dp(1), colors.textPrimary.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        DuskIcon(R.drawable.ic_person, size * 0.44f, colors.textSecondary)
        if (user.thumb != null) {
            AsyncImage(
                model = user.thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

@Composable
private fun RememberRow(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(24))

    Row(
        modifier = modifier
            .duskTvFocusEffect(focused, shape, glow = false, focusedScale = 1.02f)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) colors.surface.copy(alpha = 1f) else colors.surface)
            .border(tv.dp(1), if (focused) colors.textPrimary.copy(alpha = 0.25f) else Color.Transparent, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(tv.dp(22)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tv.dp(5))) {
            BasicText(text = "Remember This User", style = type.rowTitle)
            BasicText(
                text = "Open Dusk with this profile next time. You can change this anytime in Settings.",
                style = type.caption,
            )
        }
        DuskSwitch(checked = checked)
    }
}

// ---------------------------------------------------------------- PIN

@Composable
private fun PinEntry(user: PlexHomeUser, state: HomeUserPickerState, viewModel: HomeUserPickerViewModel) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    val type = DuskTheme.type
    val firstKey = remember { FocusRequester() }
    val isSubmitting = state.switchingProfileId != null

    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = tv.dp(80), vertical = tv.dp(72)),
        horizontalArrangement = Arrangement.spacedBy(tv.dp(120), Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.width(tv.dp(650)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(28)),
        ) {
            PlexHomeUserAvatar(user = user, size = tv.dp(176))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tv.dp(10)),
            ) {
                BasicText(text = "Enter PIN", style = type.pageTitle)
                BasicText(
                    text = "Enter the four-digit Plex Home PIN for ${user.displayName}.",
                    style = type.pageSubtitle.copy(textAlign = TextAlign.Center),
                )
            }
            Row(
                modifier = Modifier.padding(vertical = tv.dp(8)),
                horizontalArrangement = Arrangement.spacedBy(tv.dp(16)),
            ) {
                for (index in 0 until HomeUserPickerViewModel.PIN_LENGTH) {
                    Box(
                        Modifier
                            .size(tv.dp(25))
                            .clip(CircleShape)
                            .background(if (index < state.pin.length) colors.textPrimary else colors.surface)
                            .border(tv.dp(1), colors.textSecondary.copy(alpha = 0.35f), CircleShape),
                    )
                }
            }
            Box(Modifier.height(tv.dp(60)), contentAlignment = Alignment.Center) {
                state.error?.let { message ->
                    BasicText(
                        text = message,
                        style = type.body.copy(color = colors.error, textAlign = TextAlign.Center),
                        maxLines = 2,
                        modifier = Modifier.widthIn(max = tv.dp(620)),
                    )
                }
            }
            BasicText(
                text = "Forgot your PIN? Manage Plex Home from your Plex account on another device.",
                style = type.caption.copy(textAlign = TextAlign.Center),
                modifier = Modifier.widthIn(max = tv.dp(620)),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tv.dp(22)),
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "delete"),
            )
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(tv.dp(22))) {
                    row.forEach { key ->
                        when {
                            key.isEmpty() -> Box(Modifier.size(width = tv.dp(112), height = tv.dp(82)))
                            else -> PinKey(
                                key = key,
                                enabled = when {
                                    isSubmitting -> false
                                    key == "delete" -> state.pin.isNotEmpty()
                                    else -> state.pin.length < HomeUserPickerViewModel.PIN_LENGTH
                                },
                                focusRequester = if (key == "1") firstKey else null,
                                onClick = {
                                    if (key == "delete") viewModel.deleteDigit() else viewModel.appendDigit(key)
                                },
                            )
                        }
                    }
                }
            }
            Box(Modifier.height(tv.dp(32)).padding(top = tv.dp(4)), contentAlignment = Alignment.Center) {
                if (isSubmitting) DuskSpinner(size = tv.dp(28), color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun PinKey(key: String, enabled: Boolean, focusRequester: FocusRequester?, onClick: () -> Unit) {
    val tv = DuskTheme.tv
    val colors = DuskTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(tv.dp(22))
    val focusModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier

    Box(
        modifier = Modifier
            .size(width = tv.dp(112), height = tv.dp(82))
            .then(focusModifier)
            .duskTvFocusEffect(focused, shape)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(colors.surface.copy(alpha = if (enabled) 1f else 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val contentAlpha = if (enabled) 1f else 0.35f
        if (key == "delete") {
            DuskIcon(R.drawable.ic_backspace, tv.dp(40), colors.textPrimary.copy(alpha = contentAlpha))
        } else {
            BasicText(
                text = key,
                style = DuskTheme.type.body.copy(
                    fontSize = tv.sp(40),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary.copy(alpha = contentAlpha),
                ),
            )
        }
    }
}
