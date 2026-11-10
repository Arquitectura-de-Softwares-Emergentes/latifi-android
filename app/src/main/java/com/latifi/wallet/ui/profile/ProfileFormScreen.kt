package com.latifi.wallet.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.profile.ContactError
import com.latifi.wallet.domain.profile.NameError
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.onboarding.LegalLinks
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/**
 * S05. One scrolling column in focus order: title, name, contact type, contact, the error summary
 * (only after a rejected submit), Save details, the privacy line and the Terms and Privacy links.
 * Every field keeps its visible label, says "Required" in words, and shows its error as text next
 * to an error icon. A submit with errors moves focus to the first invalid field and a polite live
 * region says how many fields need attention.
 */
@Composable
fun ProfileFormScreen(
    state: ProfileFormState,
    onEvent: (ProfileFormEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameFocus = remember { FocusRequester() }
    val contactFocus = remember { FocusRequester() }
    val snackbar = remember { SnackbarHostState() }
    val saveError = stringResource(R.string.profile_save_error)

    LaunchedEffect(state.focusRequest) {
        val request = state.focusRequest ?: return@LaunchedEffect
        when (request.field) {
            ProfileField.Name -> nameFocus.requestFocus()
            ProfileField.Contact -> contactFocus.requestFocus()
        }
        onEvent(ProfileFormEvent.FocusHandled)
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbar.showSnackbar(saveError)
            onEvent(ProfileFormEvent.SaveErrorShown)
        }
    }

    ScreenScaffold(
        title = stringResource(if (state.fromGate) R.string.profile_gate_title else R.string.profile_title),
        modifier = modifier,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.fromGate) {
                val body = if (state.role == AppRole.Lender) R.string.profile_gate_body_lend else R.string.profile_gate_body_borrow
                Text(text = stringResource(body), style = MaterialTheme.typography.bodyLarge)
            }

            FormTextField(
                value = state.name,
                onValueChange = { onEvent(ProfileFormEvent.NameChanged(it)) },
                label = R.string.field_name,
                helper = R.string.field_name_help,
                errorText = state.nameError?.let { stringResource(nameErrorText(it)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                contentType = ContentType.PersonFullName,
                onBlur = { onEvent(ProfileFormEvent.Blurred(ProfileField.Name)) },
                focusRequester = nameFocus,
            )

            ContactTypeChoice(
                selected = state.contactType,
                onSelect = { onEvent(ProfileFormEvent.TypeChanged(it)) },
            )

            val isEmail = state.contactType == ContactType.EMAIL
            FormTextField(
                value = state.contact,
                onValueChange = { onEvent(ProfileFormEvent.ContactChanged(it)) },
                label = if (isEmail) R.string.field_contact_email else R.string.field_contact_phone,
                helper = null,
                errorText = state.contactError?.let { stringResource(contactErrorText(it, isEmail)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = if (isEmail) KeyboardType.Email else KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onEvent(ProfileFormEvent.Submit) }),
                contentType = if (isEmail) ContentType.EmailAddress else ContentType.PhoneNumber,
                onBlur = { onEvent(ProfileFormEvent.Blurred(ProfileField.Contact)) },
                focusRequester = contactFocus,
            )

            if (state.errorCount > 0 && (state.nameError != null || state.contactError != null)) {
                ErrorSummary(count = state.errorCount)
            }

            PrimaryButton(
                text = stringResource(R.string.profile_save),
                onClick = { onEvent(ProfileFormEvent.Submit) },
                loading = state.saving,
            )

            Text(
                text = stringResource(R.string.contact_privacy),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegalLinks(
                onOpenTerms = { onEvent(ProfileFormEvent.OpenTerms) },
                onOpenPrivacy = { onEvent(ProfileFormEvent.OpenPrivacy) },
            )
        }
    }
}

@StringRes
private fun nameErrorText(error: NameError): Int = when (error) {
    NameError.Required -> R.string.err_name_required
    NameError.TooLong -> R.string.err_name_long
}

@StringRes
private fun contactErrorText(error: ContactError, isEmail: Boolean): Int = when (error) {
    ContactError.Invalid -> if (isEmail) R.string.err_contact_email else R.string.err_contact_phone
}

/**
 * A text field with a persistent floating label (never a placeholder), the word "Required" in the
 * supporting text, an optional helper line, and an inline error with an icon. [contentType] is
 * the autofill hint of the platform (the successor of `autofillHints`), so a password manager
 * or the keyboard can offer the saved name, email or phone.
 */
@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes helper: Int?,
    errorText: String?,
    keyboardOptions: KeyboardOptions,
    contentType: ContentType,
    onBlur: () -> Unit,
    focusRequester: FocusRequester,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val required = stringResource(R.string.label_required)
    val helperText = helper?.let { stringResource(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                if (hadFocus && !focus.isFocused) onBlur()
                if (focus.isFocused) hadFocus = true
            }
            .semantics {
                this.contentType = contentType
                if (errorText != null) semanticsError(errorText)
            },
        label = { Text(text = stringResource(label)) },
        supportingText = {
            if (errorText != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(text = errorText)
                }
            } else {
                Text(text = if (helperText != null) "$required. $helperText" else required)
            }
        },
        isError = errorText != null,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = LatiFiShapes.field,
    )
}

/** Email or Phone, a two option segmented control. Each segment is a radio item at least 48dp high. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactTypeChoice(selected: ContactType, onSelect: (ContactType) -> Unit) {
    val options = listOf(
        ContactType.EMAIL to R.string.contact_email,
        ContactType.PHONE to R.string.contact_phone,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.field_contact_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.heightIn(min = MinTouchTarget),
                ) {
                    Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** One polite live region that says how many fields the last submit rejected. */
@Composable
private fun ErrorSummary(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = pluralStringResource(R.plurals.profile_errors_count, count, count),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
