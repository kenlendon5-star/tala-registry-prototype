package ph.tala.registry.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AccountGate(viewModel: AccountViewModel, content: @Composable (String, () -> Unit) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedInEmail = state.signedInEmail
    when {
        state.checking -> LoadingAccount()
        signedInEmail != null -> content(signedInEmail, viewModel::signOut)
        else -> AccountScreen(state, viewModel::signIn, viewModel::signUp, viewModel::resendConfirmation, viewModel::signOut)
    }
}

@Composable
fun AccountScreen(
    state: AccountUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String, String) -> Unit,
    onResendConfirmation: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    Box(Modifier.fillMaxSize().testTag("account-screen")) {
        when {
            state.checking -> LoadingAccount()
            state.signedInEmail != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Cloud account", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text("Signed in as ${state.signedInEmail}")
                state.message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onSignOut, enabled = !state.busy) { Text("Sign out") }
            }
            else -> SignedOutAccountScreen(state, onSignIn, onSignUp, onResendConfirmation)
        }
    }
}

@Composable
private fun LoadingAccount() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Checking account…")
    }
}

@Composable
private fun SignedOutAccountScreen(
    state: AccountUiState,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String, String) -> Unit,
    onResendConfirmation: (String) -> Unit,
) {
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var barangay by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val passwordsMatch = !createAccount || password == confirmPassword
    val canSubmit = if (createAccount) {
        AccountInputRules.signUpProblem(email, password, confirmPassword, name, barangay) == null
    } else {
        AccountInputRules.signInProblem(email, password) == null
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Tala", style = MaterialTheme.typography.displaySmall)
        Text("Barangay household registry", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (createAccount) "Create enumerator account" else "Sign in", style = MaterialTheme.typography.titleLarge)
                if (createAccount) {
                    OutlinedTextField(name, { name = it }, label = { Text("Enumerator name") }, modifier = Modifier.fillMaxWidth().testTag("account-name"), enabled = !state.busy, singleLine = true)
                    OutlinedTextField(barangay, { barangay = it }, label = { Text("Barangay") }, modifier = Modifier.fillMaxWidth().testTag("account-barangay"), enabled = !state.busy, singleLine = true)
                }
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth().testTag("account-email"), enabled = !state.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password (at least 8 characters)") }, modifier = Modifier.fillMaxWidth().testTag("account-password"), enabled = !state.busy, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (createAccount) {
                    OutlinedTextField(
                        confirmPassword,
                        { confirmPassword = it },
                        label = { Text("Confirm password") },
                        modifier = Modifier.fillMaxWidth().testTag("account-confirm-password"),
                        enabled = !state.busy,
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = confirmPassword.isNotEmpty() && !passwordsMatch,
                        supportingText = if (confirmPassword.isNotEmpty() && !passwordsMatch) ({ Text("Passwords do not match.") }) else null,
                    )
                }
                state.message?.let {
                    Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = { if (createAccount) onSignUp(email, password, confirmPassword, name, barangay) else onSignIn(email, password) },
                    enabled = canSubmit && !state.busy,
                    modifier = Modifier.fillMaxWidth().testTag("account-submit"),
                ) {
                    if (state.busy) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text(if (createAccount) "Create account" else "Sign in")
                }
                TextButton(onClick = { createAccount = !createAccount }, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("account-mode-toggle")) {
                    Text(if (createAccount) "Already have an account? Sign in" else "New enumerator? Create an account")
                }
                state.pendingConfirmationEmail?.let { pendingEmail ->
                    TextButton(
                        onClick = { onResendConfirmation(pendingEmail) },
                        enabled = !state.busy,
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("account-resend"),
                    ) { Text("Resend confirmation email") }
                }
            }
        }
    }
}
