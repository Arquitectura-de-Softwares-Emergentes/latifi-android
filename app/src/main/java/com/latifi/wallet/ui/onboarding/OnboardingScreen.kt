package com.latifi.wallet.ui.onboarding

import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.components.PulseMark
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.components.PinnedActionBar
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.StepProgress
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.components.rememberReducedMotion
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.Spacing

private data class OnboardingPageContent(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val Pages = listOf(
    OnboardingPageContent(R.drawable.ic_payments, R.string.onb1_title, R.string.onb1_body),
    OnboardingPageContent(R.drawable.ic_verified_user, R.string.onb2_title, R.string.onb2_body),
    OnboardingPageContent(R.drawable.ic_currency_exchange, R.string.onb3_title, R.string.onb3_body),
    OnboardingPageContent(R.drawable.ic_workspace_premium, R.string.onb4_title, R.string.onb4_body),
)

private val IllustrationSize = 160.dp

/**
 * S01. Four plain-language pages with Back and Next that always work. Swiping is optional.
 * After a page change focus moves to the new title, and with the system animation scale at 0
 * the pager jumps instead of sliding. The screen is stateless: the view model owns the page.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val pager = rememberPagerState(initialPage = state.page) { ONBOARDING_PAGE_COUNT }
    val titleFocus = remember { List(ONBOARDING_PAGE_COUNT) { FocusRequester() } }

    // Back and Next change the state first, and the pager follows.
    LaunchedEffect(state.page) {
        if (pager.currentPage != state.page) {
            if (reducedMotion) pager.scrollToPage(state.page) else pager.animateScrollToPage(state.page)
        }
    }
    // A swipe settles the pager first, and the state follows. The first run keeps focus where it is.
    var firstSettle by remember { mutableStateOf(true) }
    LaunchedEffect(pager.settledPage) {
        if (pager.settledPage != state.page) onEvent(OnboardingEvent.PageSettled(pager.settledPage))
        if (firstSettle) {
            firstSettle = false
        } else {
            runCatching { titleFocus[pager.settledPage].requestFocus() }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PinnedActionBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!state.isFirst) {
                        TertiaryButton(text = stringResource(R.string.action_back), onClick = { onEvent(OnboardingEvent.Back) })
                    }
                    PrimaryButton(
                        text = stringResource(
                            when {
                                !state.isLast -> R.string.action_next
                                state.showConnect -> R.string.connect_cta
                                else -> R.string.onb_finish_help
                            },
                        ),
                        onClick = { onEvent(OnboardingEvent.Next) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalAlignment = Alignment.Top,
        ) { index ->
            OnboardingPage(
                index = index,
                titleFocus = titleFocus[index],
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    index: Int,
    titleFocus: FocusRequester,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val page = Pages[index]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(
            modifier = Modifier
                .size(IllustrationSize)
                .clip(LatiFiShapes.hero)
                .background(Brush.linearGradient(LatiFiTheme.colors.heroGradient))
                .align(Alignment.CenterHorizontally)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (index == 0) {
                PulseMark(size = 104.dp, animateDraw = true, withBackground = false)
            } else {
                Icon(
                    painter = painterResource(page.icon),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = LatiFiTheme.colors.heroAccent,
                )
            }
        }
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .focusRequester(titleFocus)
                .focusable()
                .semantics { heading() },
        )
        Text(text = stringResource(page.body), style = MaterialTheme.typography.bodyLarge)
        if (index == 0) {
            Text(
                text = stringResource(R.string.onb1_wallet_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepProgress(step = index + 1, total = ONBOARDING_PAGE_COUNT)
        if (index == ONBOARDING_PAGE_COUNT - 1) {
            Text(
                text = stringResource(R.string.onb_nocustody),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegalLinks(
                onOpenTerms = { onEvent(OnboardingEvent.OpenTerms) },
                onOpenPrivacy = { onEvent(OnboardingEvent.OpenPrivacy) },
            )
        }
    }
}

/** The Terms and Privacy links as two text buttons, each at least 48dp high. */
@Composable
internal fun LegalLinks(onOpenTerms: () -> Unit, onOpenPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TertiaryButton(text = stringResource(R.string.terms_link), onClick = onOpenTerms)
        TertiaryButton(text = stringResource(R.string.privacy_link), onClick = onOpenPrivacy)
    }
}
