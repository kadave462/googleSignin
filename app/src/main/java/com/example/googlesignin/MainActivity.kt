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
import androidx.compose.material3.Text
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
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Base64

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SignInScreen() }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var message by remember { mutableStateOf("") }
    var userSignedIn by remember { mutableStateOf(false) }

    val credentialManager = CredentialManager.create(context)

    // Sign-in request (authorized accounts)
    val signInRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(context.getString(R.string.web_client_id))
                .setAutoSelectEnabled(true)
                .setNonce(generateNonce())
                .build()
        )
        .build()

    // Sign-up request (allow any Google account)
    val signUpRequest = GetCredentialRequest.Builder()
        .addCredentialOption(
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(context.getString(R.string.web_client_id))
                .setAutoSelectEnabled(true)
                .setNonce(generateNonce())
                .build()
        )
        .build()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Sign in button
            Button(onClick = {
                coroutineScope.launch {
                    try {
                        val result = credentialManager.getCredential(context, signInRequest)
                        // Use callback to handle success/failure
                        handleCredential(result) { success ->
                            if (success) {
                                userSignedIn = true
                                message = "Signed in successfully!"
                            } else {
                                message = "Authentication failed!"
                            }
                        }
                    } catch (e: GetCredentialException) {
                        Log.e("Auth", "Sign in error: ${e.message}")
                        // If no credentials found, prompt sign up
                        if (e.message?.contains("No credentials available") == true) {
                            try {
                                val result = credentialManager.getCredential(context, signUpRequest)
                                handleCredential(result) { success ->
                                    if (success) {
                                        userSignedIn = true
                                        message = "New user sign-up successful!"
                                    } else {
                                        message = "Authentication failed!"
                                    }
                                }
                            } catch (signUpError: GetCredentialException) {
                                Log.e("Auth", "Sign up error: ${signUpError.message}")
                                message = "Authentication failed!"
                            }
                        } else {
                            message = "Authentication failed!"
                        }
                    }
                }
            }) {
                Text("Sign in with Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sign out button (only show if signed in)
            if (userSignedIn) {
                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            // Also sign out from Firebase
                            FirebaseAuth.getInstance().signOut()

                            // Clear the Credential Manager state
                            val clearRequest = ClearCredentialStateRequest()
                            credentialManager.clearCredentialState(clearRequest)

                            userSignedIn = false
                            message = "You have signed out!"
                        } catch (e: ClearCredentialException) {
                            Log.e("Auth", "Error clearing credential state: ${e.message}")
                            message = "Failed to sign out."
                        }
                    }
                }) {
                    Text("Sign out")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status message
            if (message.isNotEmpty()) {
                Text(message)
            }
        }
    }
}

/**
 * handleCredential uses a callback to return success or failure.
 * Because FirebaseAuth signInWithCredential is asynchronous,
 * we can't just return a Boolean immediately.
 */
fun handleCredential(result: GetCredentialResponse, onResult: (Boolean) -> Unit) {
    val credential = result.credential
    when (credential) {
        is CustomCredential -> {
            if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                try {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken
                    Log.d("Auth", "Google ID token: $idToken")

                    // Create a Firebase credential with the Google ID token
                    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                    FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d("FirebaseAuth", "Firebase sign-in successful")
                                onResult(true)
                            } else {
                                Log.e("FirebaseAuth", "Firebase sign-in failed: ${task.exception?.message}")
                                onResult(false)
                            }
                        }
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e("Auth", "Token parsing error: ${e.message}")
                    onResult(false)
                }
            } else {
                Log.e("Auth", "Unexpected credential type: ${credential.type}")
                onResult(false)
            }
        }
        else -> {
            Log.e("Auth", "Unrecognized credential type: ${credential.type}")
            onResult(false)
        }
    }
}

/**
 * Generates a 256-bit nonce and returns a URL-safe Base64 encoded string.
 */
fun generateNonce(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    } else {
        android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }
}
