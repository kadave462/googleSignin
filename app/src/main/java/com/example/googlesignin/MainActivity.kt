package com.example.googlesignin

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignInScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showErrorSnackbar by remember { mutableStateOf(false) }
    var showSignUpOption by remember { mutableStateOf(false) }
    var userSignedIn by remember { mutableStateOf(false) }
    var userSignedUp by remember { mutableStateOf(false) }

    val credentialManager = CredentialManager.create(context)

    // Sign-in request
    val googleIdOptionSignIn = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(true)
        .setNonce(generateNonce())
        .build()

    val requestSignIn = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOptionSignIn)
        .build()

    // Sign-up request
    val googleIdOptionSignUp = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(context.getString(R.string.default_web_client_id))
        .setAutoSelectEnabled(true)
        .setNonce(generateNonce())
        .build()

    val requestSignUp = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOptionSignUp)
        .build()

    Scaffold(
        snackbarHost = {
            if (showErrorSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = {
                            showErrorSnackbar = false
                            showSignUpOption = true
                        }) {
                            Text("OK")
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Google Sign-In failed")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sign-in button
            Button(onClick = {
                coroutineScope.launch {
                    try {
                        val result = credentialManager.getCredential(context, requestSignIn)
                        handleSignIn(result) { isNewUser ->
                            if (isNewUser) {
                                userSignedUp = true
                            } else {
                                userSignedIn = true
                            }
                        }
                    } catch (e: GetCredentialException) {
                        showErrorSnackbar = true
                        Log.e("SignInError", "Google Sign-In failed: ${e.message}")

                        if (e.message == "No credentials available") {
                            showSignUpOption = true
                        }
                    }
                }
            }) {
                Text("Sign in with Google")
            }

            // Sign-up button (shown if sign-in fails)
            if (showSignUpOption) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Don't have an account?")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            val result = credentialManager.getCredential(context, requestSignUp)
                            handleSignIn(result) { isNewUser ->
                                if (isNewUser) {
                                    userSignedUp = true
                                } else {
                                    userSignedIn = true
                                }
                            }
                        } catch (e: GetCredentialException) {
                            showErrorSnackbar = true
                            Log.e("SignUpError", "Google Sign-Up failed: ${e.message}")
                        }
                    }
                }) {
                    Text("Sign up")
                }
            }

            // Signed-in message
            if (userSignedIn) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("You are signed in!")
            }

            // Signed-up message
            if (userSignedUp) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("You have successfully signed up!")
            }
        }
    }
}

fun handleSignIn(result: GetCredentialResponse, onSignInComplete: (Boolean) -> Unit) {
    val credential = result.credential

    when (credential) {
        is PasswordCredential -> {
            // Handle PasswordCredential (if you support it)
            val username = credential.id
            val password = credential.password
            Log.d("SignIn", "Password credential received: username=$username")
            // TODO: Send username and password to your server for validation
            // TODO: Determine if the user is new or existing based on server response
            onSignInComplete(false) // Assuming existing user for now
        }

        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    Log.d("SignIn", "Google ID token received: $idToken")

                    // TODO: Send idToken to your server for verification
                    // TODO: Use a proper GoogleIdTokenVerifier on your server
                    // TODO: Determine if the user is new or existing based on server response
                    onSignInComplete(true) // Assuming new user for now
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("SignInError", "Token parsing error: ${e.message}")
                }
            } else {
                Log.e("SignInError", "Unrecognized custom credential type: ${credential.type}")
            }
        }

        else -> {
            Log.e("SignInError", "Unrecognized credential type: ${credential.type}")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun generateNonce(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32) // 32 bytes for a 256-bit nonce
    secureRandom.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}