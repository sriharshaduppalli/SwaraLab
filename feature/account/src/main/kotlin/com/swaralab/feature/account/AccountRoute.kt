package com.swaralab.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swaralab.domain.model.Account
import com.swaralab.domain.usecase.AccountReject
import com.swaralab.domain.usecase.AccountResult
import com.swaralab.domain.usecase.CurrentAccount
import com.swaralab.domain.usecase.RegisterAccount
import com.swaralab.domain.usecase.SignIn
import com.swaralab.domain.usecase.SignOut
import com.swaralab.ui.AppLanguage
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.SwaraThemeId
import com.swaralab.ui.ThemePickCard
import com.swaralab.ui.studioAtmosphere
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountMode {
    REGISTER,
    SIGN_IN,
}

data class AccountForm(
    val mode: AccountMode = AccountMode.REGISTER,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val error: String? = null,
    val busy: Boolean = false,
    val signedIn: Account? = null,
)

class AccountViewModel(
    private val registerAccount: RegisterAccount,
    private val signIn: SignIn,
    private val signOutAccount: SignOut,
    private val currentAccount: CurrentAccount,
) : ViewModel() {
    private val formState = MutableStateFlow(AccountForm())
    val form: StateFlow<AccountForm> = formState.asStateFlow()

    init {
        viewModelScope.launch {
            formState.update { it.copy(signedIn = currentAccount()) }
        }
    }

    fun onName(value: String) = formState.update { it.copy(name = value, error = null) }

    fun onEmail(value: String) = formState.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = formState.update { it.copy(password = value, error = null) }

    fun switchMode() {
        formState.update {
            it.copy(
                mode = if (it.mode == AccountMode.REGISTER) AccountMode.SIGN_IN else AccountMode.REGISTER,
                password = "",
                error = null,
            )
        }
    }

    fun submit() {
        val current = formState.value
        if (current.busy || current.signedIn != null) return
        formState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (current.mode == AccountMode.REGISTER) {
                    registerAccount(current.name, current.email, current.password)
                } else {
                    signIn(current.email, current.password)
                }
                when (result) {
                    is AccountResult.Ready -> formState.update {
                        it.copy(busy = false, signedIn = result.account, password = "", error = null)
                    }
                    is AccountResult.Rejected -> formState.update {
                        it.copy(busy = false, error = result.reason.message())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                formState.update {
                    it.copy(busy = false, error = "The account file on this phone could not be read.")
                }
            }
        }
    }

    fun signOut() {
        if (formState.value.busy) return
        viewModelScope.launch {
            signOutAccount()
            formState.update {
                AccountForm(mode = AccountMode.SIGN_IN)
            }
        }
    }

    companion object {
        fun factory(
            registerAccount: RegisterAccount,
            signIn: SignIn,
            signOut: SignOut,
            currentAccount: CurrentAccount,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(registerAccount, signIn, signOutAccount = signOut, currentAccount) }
        }
    }
}

private fun AccountReject.message(): String = when (this) {
    AccountReject.EMAIL_INVALID -> "Enter a valid email address."
    AccountReject.PASSWORD_TOO_SHORT -> "Use at least 8 characters."
    AccountReject.NAME_MISSING -> "Enter the name you sing under."
    AccountReject.EMAIL_TAKEN -> "That email is already registered on this phone."
    AccountReject.UNKNOWN_EMAIL -> "No account on this phone uses that email."
    AccountReject.WRONG_PASSWORD -> "That password does not match."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountRoute(
    registerAccount: RegisterAccount,
    signIn: SignIn,
    signOut: SignOut,
    currentAccount: CurrentAccount,
    appLanguage: AppLanguage,
    onAppLanguage: (AppLanguage) -> Unit,
    themeId: SwaraThemeId,
    onTheme: (SwaraThemeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AccountViewModel = viewModel(
        factory = AccountViewModel.factory(registerAccount, signIn, signOut, currentAccount),
    )
    val form by viewModel.form.collectAsState()
    val signedIn = form.signedIn
    val copy = LocalAppCopy.current

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHero(
            title = "Singer profile",
            body = copy.rightsNote,
            accent = SwaraColors.Rose,
        )
        Text(
            text = copy.appLanguage,
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppLanguage.entries.forEach { language ->
                FilterChip(
                    selected = language == appLanguage,
                    onClick = { onAppLanguage(language) },
                    label = { Text(language.label) },
                )
            }
        }
        Text(
            text = copy.appTheme,
            style = MaterialTheme.typography.titleMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwaraThemeId.entries.forEach { theme ->
                ThemePickCard(
                    theme = theme,
                    selected = theme == themeId,
                    onClick = { onTheme(theme) },
                )
            }
        }
        if (signedIn != null) {
            Text(text = signedIn.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = signedIn.email,
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
            )
            Text(
                text = "This name stays on this phone. Your songs are not uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        } else {
            Text(
                text = if (form.mode == AccountMode.REGISTER) {
                    "Optional. The studio works without an account."
                } else {
                    "Sign in to the account already on this phone."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = SwaraColors.Muted,
            )
            if (form.mode == AccountMode.REGISTER) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::onName,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
            }
            OutlinedTextField(
                value = form.email,
                onValueChange = viewModel::onEmail,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = form.password,
                onValueChange = viewModel::onPassword,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            form.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::submit,
                enabled = !form.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (form.mode == AccountMode.REGISTER) "Create account" else "Sign in")
            }
            TextButton(onClick = viewModel::switchMode, enabled = !form.busy) {
                Text(
                    if (form.mode == AccountMode.REGISTER) {
                        "Already registered? Sign in"
                    } else {
                        "New here? Create an account"
                    },
                )
            }
        }
    }
}
