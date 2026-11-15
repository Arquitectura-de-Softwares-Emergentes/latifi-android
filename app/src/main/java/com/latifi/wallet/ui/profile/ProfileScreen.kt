package com.latifi.wallet.ui.profile

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.ConfirmDialog
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.Spacing

/** Every row of S17 is at least this high, and the whole row is the target. */
private val RowMinHeight = 56.dp

/**
 * S17 Profile, grouped by topic: personal details, wallet, preferences, help and about. Rows are
 * at least 56dp high with an icon and a label. Radio lists use `selectableGroup` and name each
 * option in its own language. The disconnect row opens a destructive dialog whose safe action
 * holds the default focus.
 */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileEvent) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val disconnectRow = remember { FocusRequester() }
    val messageText = state.message?.let { stringResource(messageRes(it)) }

    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            onEvent(ProfileEvent.MessageShown)
        }
    }

    ScreenScaffold(
        title = title,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            PersonalGroup(state, onEvent)
            WalletGroup(state, onEvent, disconnectRow)
            PreferencesGroup(state, onEvent)
            HelpGroup(onEvent)
            AboutGroup(state, onEvent)
        }
    }

    if (state.disconnectDialogOpen) {
        ConfirmDialog(
            title = stringResource(R.string.disconnect_title),
            body = stringResource(R.string.disconnect_body),
            safeLabel = stringResource(R.string.disconnect_keep),
            onSafe = { onEvent(ProfileEvent.KeepConnected) },
            confirmLabel = stringResource(R.string.disconnect_confirm),
            onConfirm = { onEvent(ProfileEvent.ConfirmDisconnect) },
            destructive = true,
            triggerFocus = disconnectRow,
        )
    }
}

@StringRes
private fun messageRes(message: ProfileMessage): Int = when (message) {
    ProfileMessage.AddressCopied -> R.string.profile_address_copied
    ProfileMessage.LanguageEnglish -> R.string.language_changed_en
    ProfileMessage.LanguageSpanish -> R.string.language_changed_es
}

@Composable
private fun PersonalGroup(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    Group(R.string.profile_section_personal) {
        state.displayName?.let { name ->
            InfoRow(icon = R.drawable.ic_person, primary = name, secondary = stringResource(R.string.field_name))
        }
        state.contact?.let { contact ->
            val label = if (contact.type == ContactType.EMAIL) R.string.field_contact_email else R.string.field_contact_phone
            InfoRow(icon = R.drawable.ic_verified_user, primary = contact.value, secondary = stringResource(label))
        }
        ActionRow(
            icon = R.drawable.ic_edit,
            label = stringResource(R.string.profile_edit),
            onClick = { onEvent(ProfileEvent.EditDetails) },
        )
    }
}

@Composable
private fun WalletGroup(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit, disconnectRow: FocusRequester) {
    Group(R.string.profile_section_wallet) {
        state.shortAddress?.let { short ->
            InfoRow(icon = R.drawable.ic_account_balance_wallet, primary = stringResource(R.string.profile_address, short))
        }
        ActionRow(
            icon = R.drawable.ic_content_copy,
            label = stringResource(R.string.profile_copy_address),
            onClick = { onEvent(ProfileEvent.CopyAddress) },
        )
        ActionRow(
            icon = R.drawable.ic_payments,
            label = stringResource(R.string.coins_cta),
            onClick = { onEvent(ProfileEvent.GetCoins) },
        )
        ActionRow(
            icon = R.drawable.ic_logout,
            label = stringResource(R.string.disconnect_action),
            onClick = { onEvent(ProfileEvent.AskDisconnect) },
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.focusRequester(disconnectRow),
        )
    }
}

@Composable
private fun PreferencesGroup(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    Group(R.string.profile_section_prefs) {
        val switchLabel = if (state.role == AppRole.Borrower) R.string.profile_role_switch_lender else R.string.profile_role_switch_borrower
        ActionRow(
            icon = R.drawable.ic_sync,
            label = stringResource(switchLabel),
            secondary = stringResource(R.string.profile_role),
            onClick = { onEvent(ProfileEvent.SwitchRole) },
        )

        SubHeading(R.string.profile_language)
        Column(modifier = Modifier.selectableGroup()) {
            LanguageOption(AppLanguage.English, R.string.language_name_en, state.language) { onEvent(ProfileEvent.SelectLanguage(it)) }
            LanguageOption(AppLanguage.Spanish, R.string.language_name_es, state.language) { onEvent(ProfileEvent.SelectLanguage(it)) }
        }

        SubHeading(R.string.profile_currency)
        Column(modifier = Modifier.selectableGroup()) {
            for (currency in LocalCurrency.entries) {
                RadioRow(
                    label = AnnotatedString(stringResource(currencyName(currency))),
                    selected = state.currency == currency,
                    onSelect = { onEvent(ProfileEvent.SelectCurrency(currency)) },
                )
            }
        }
    }
}

@StringRes
private fun currencyName(currency: LocalCurrency): Int = when (currency) {
    LocalCurrency.PEN -> R.string.currency_pen
    LocalCurrency.COP -> R.string.currency_cop
    LocalCurrency.MXN -> R.string.currency_mxn
    LocalCurrency.ARS -> R.string.currency_ars
    LocalCurrency.BRL -> R.string.currency_brl
    LocalCurrency.CLP -> R.string.currency_clp
}

@Composable
private fun LanguageOption(
    language: AppLanguage,
    @StringRes name: Int,
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    // The name carries its own locale, so a screen reader can read "Español" in Spanish even
    // while the app is in English.
    val label = buildAnnotatedString {
        withStyle(SpanStyle(localeList = LocaleList(language.tag))) { append(stringResource(name)) }
    }
    RadioRow(label = label, selected = current == language, onSelect = { onSelect(language) })
}

@Composable
private fun HelpGroup(onEvent: (ProfileEvent) -> Unit) {
    Group(R.string.profile_section_help) {
        ActionRow(
            icon = R.drawable.ic_help,
            label = stringResource(R.string.profile_how),
            onClick = { onEvent(ProfileEvent.OpenHowItWorks) },
        )
        ActionRow(
            icon = R.drawable.ic_open_in_new,
            label = stringResource(R.string.terms_link),
            onClick = { onEvent(ProfileEvent.OpenTerms) },
        )
        ActionRow(
            icon = R.drawable.ic_open_in_new,
            label = stringResource(R.string.privacy_link),
            onClick = { onEvent(ProfileEvent.OpenPrivacy) },
        )
    }
}

@Composable
private fun AboutGroup(state: ProfileUiState, onEvent: (ProfileEvent) -> Unit) {
    Group(R.string.profile_section_about) {
        InfoRow(icon = R.drawable.ic_info, primary = stringResource(R.string.profile_version, state.versionName))
        InfoRow(icon = R.drawable.ic_language, primary = stringResource(R.string.profile_network))
        val mode = if (state.mode == AppMode.Live) R.string.profile_mode_live else R.string.profile_mode_demo
        InfoRow(icon = R.drawable.ic_tune, primary = stringResource(mode))
        AttributionRow(modifier = Modifier.fillMaxWidth(), onOpen = { onEvent(ProfileEvent.OpenRatesProvider) })
    }
}

/** A titled block of rows. The title is a heading, so a screen reader can jump between groups. */
@Composable
private fun Group(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SubHeading(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
    )
}

/** A row that shows a value and does nothing when tapped. */
@Composable
private fun InfoRow(@DrawableRes icon: Int, primary: String, secondary: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        RowIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = primary, style = MaterialTheme.typography.bodyLarge)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A row that acts. The whole row is the target and announces itself as a button. */
@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .latifiFocusRing()
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        RowIcon(icon, color)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A radio option. The check icon repeats the selected state, so color is never the only signal. */
@Composable
private fun RadioRow(label: AnnotatedString, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .latifiFocusRing()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RowIcon(@DrawableRes icon: Int, tint: Color) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = tint,
    )
}
