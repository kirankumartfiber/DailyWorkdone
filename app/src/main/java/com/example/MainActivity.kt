package com.example

import android.content.Context
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.theme.*
import java.util.*

class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        WorkflowSimulatorApp()
      }
    }
  }
}

@Composable
fun WorkflowSimulatorApp() {
  val context = LocalContext.current
  val activity = context as FragmentActivity
  val sharedPrefs = remember { context.getSharedPreferences("simulation_prefs", Context.MODE_PRIVATE) }

  // State Indicators
  var registeredEmpId by remember { mutableStateOf(sharedPrefs.getString("reg_empId", "") ?: "") }
  var registeredPsw by remember { mutableStateOf(sharedPrefs.getString("reg_psw", "") ?: "") }
  
  var currentLoggedInUserId by remember { mutableStateOf(sharedPrefs.getString("employeeId", "") ?: "") }
  var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("isLoggedIn", false)) }
  var isFaceIdRegistered by remember { mutableStateOf(sharedPrefs.getBoolean("isFaceIdRegistered", false)) }
  
  var showFaceIdRegistration by remember { mutableStateOf(value = false) }
  var usePasswordLogin by remember { mutableStateOf(value = false) }

  val isUserRegistered = registeredEmpId.isNotEmpty() && registeredPsw.isNotEmpty()

  // Handle Logout / Reset
  val logoutAction = {
    sharedPrefs.edit {
      remove("employeeId")
      remove("isLoggedIn")
    }
    currentLoggedInUserId = ""
    isLoggedIn = false
    usePasswordLogin = false
    Toast.makeText(context, "Logged out successfully!", Toast.LENGTH_SHORT).show()
  }

  val resetAllAction = {
    sharedPrefs.edit { clear() }
    registeredEmpId = ""
    registeredPsw = ""
    currentLoggedInUserId = ""
    isLoggedIn = false
    isFaceIdRegistered = false
    usePasswordLogin = false
    Toast.makeText(context, "App data reset!", Toast.LENGTH_SHORT).show()
  }

  val onFaceIdRegistered = {
    sharedPrefs.edit {
      putBoolean("isFaceIdRegistered", true)
      putBoolean("isLoggedIn", true)
      putString("employeeId", registeredEmpId)
    }
    isFaceIdRegistered = true
    isLoggedIn = true
    currentLoggedInUserId = registeredEmpId
    showFaceIdRegistration = false
    Toast.makeText(context, "Face ID Registered Successfully!", Toast.LENGTH_SHORT).show()
  }

  val showBiometricPrompt = {
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricPrompt = BiometricPrompt(
      activity,
      executor,
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          super.onAuthenticationSucceeded(result)
          isLoggedIn = true
          currentLoggedInUserId = registeredEmpId
          Toast.makeText(context, "Face ID verified Successfully", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          super.onAuthenticationError(errorCode, errString)
          if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
            Toast.makeText(context, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
          }
        }

        override fun onAuthenticationFailed() {
          super.onAuthenticationFailed()
          Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
        }
      }
    )

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle("Face ID Authentication")
      .setSubtitle("Log in using your biometric credential")
      .setNegativeButtonText("Use Password")
      .build()

    biometricPrompt.authenticate(promptInfo)
  }

  // Automatically trigger biometric if registered and not logged in
  LaunchedEffect(isFaceIdRegistered, isLoggedIn, usePasswordLogin) {
    if (isFaceIdRegistered && !isLoggedIn && !usePasswordLogin) {
      showBiometricPrompt()
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize()
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Header Bar
      HeaderPanel(
        isLoggedIn = isLoggedIn,
        userId = currentLoggedInUserId,
        onLogout = logoutAction
      )

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (!isLoggedIn) {
          if (!isUserRegistered) {
            // First time Registration
            RegistrationLayout(
              onRegisterSuccess = { id, psw ->
                sharedPrefs.edit {
                  putString("reg_empId", id)
                  putString("reg_psw", psw)
                }
                registeredEmpId = id
                registeredPsw = psw
                showFaceIdRegistration = true
              }
            )
          } else if (isFaceIdRegistered && !usePasswordLogin) {
            // Face ID Validation Screen
            FaceIdLoginLayout(
              onPasswordLogin = { usePasswordLogin = true },
              onBiometricClick = { showBiometricPrompt() },
              onReset = resetAllAction
            )
          } else {
            // Standard Login Screen
            LocalLoginLayout(
              storedEmpId = registeredEmpId,
              storedPsw = registeredPsw,
              onAuthSuccess = {
                sharedPrefs.edit {
                  putBoolean("isLoggedIn", true)
                  putString("employeeId", registeredEmpId)
                }
                isLoggedIn = true
                currentLoggedInUserId = registeredEmpId
              }
            )
          }
        } else {
          // WebView Screen
          WebViewLayout(employeeId = currentLoggedInUserId)
        }

        if (showFaceIdRegistration) {
          FaceIdRegistrationDialog(
            onRegister = onFaceIdRegistered,
            onSkip = {
              sharedPrefs.edit {
                putBoolean("isLoggedIn", true)
                putString("employeeId", registeredEmpId)
              }
              isLoggedIn = true
              currentLoggedInUserId = registeredEmpId
              showFaceIdRegistration = false
            }
          )
        }
      }
    }
  }
}

@Composable
fun FaceIdRegistrationDialog(onRegister: () -> Unit, onSkip: () -> Unit) {
  AlertDialog(
    onDismissRequest = { },
    title = { Text("Register Face ID") },
    text = { Text("Would you like to enable Face ID for faster login next time? (Optional)") },
    confirmButton = {
      Button(onClick = onRegister) {
        Text("Register")
      }
    },
    dismissButton = {
      TextButton(onClick = onSkip) {
        Text("Skip")
      }
    }
  )
}

@Composable
fun RegistrationLayout(onRegisterSuccess: (String, String) -> Unit) {
  var empIdInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Card(
      modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
      colors = CardDefaults.cardColors(containerColor = TealSurface),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CREATE ACCOUNT", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Register your credentials", fontSize = 12.sp, color = TealTextSecondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
          value = empIdInput,
          onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) empIdInput = it },
          label = { Text("Employee ID (4 digits)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = passwordInput,
          onValueChange = { if (it.length <= 8) passwordInput = it },
          label = { Text("Password (6-8 chars)") },
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        if (error != null) {
          Text(error!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = {
            if (empIdInput.length != 4) {
              error = "Employee ID must be 4 digits"
            } else if (passwordInput.length !in 6..8) {
              error = "Password must be 6-8 characters"
            } else {
              onRegisterSuccess(empIdInput, passwordInput)
            }
          },
          modifier = Modifier.fillMaxWidth().height(50.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
          Text("REGISTER & CONTINUE", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun FaceIdLoginLayout(
  onPasswordLogin: () -> Unit,
  onBiometricClick: () -> Unit,
  onReset: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      IconButton(
        onClick = onBiometricClick,
        modifier = Modifier
          .size(100.dp)
          .clip(CircleShape)
          .background(TealSurface)
          .border(2.dp, TealPrimary, CircleShape)
      ) {
        Icon(Icons.Default.Face, contentDescription = "Face ID", modifier = Modifier.size(60.dp), tint = TealPrimary)
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text("Tap to verify Face ID", color = Color.White)
      Spacer(modifier = Modifier.height(32.dp))
      TextButton(onClick = onPasswordLogin) {
        Text("Use Password instead", color = TealPrimary)
      }
      Spacer(modifier = Modifier.height(8.dp))
      TextButton(onClick = onReset) {
        Text("Reset All Data", color = Color(0xFFFF5F5F))
      }
    }
  }
}

@Composable
fun LocalLoginLayout(
  storedEmpId: String,
  storedPsw: String,
  onAuthSuccess: () -> Unit
) {
  var idInput by remember { mutableStateOf("") }
  var pswInput by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }

  Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Card(
      modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
      colors = CardDefaults.cardColors(containerColor = TealSurface),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SIGN IN", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
          value = idInput,
          onValueChange = { idInput = it },
          label = { Text("Employee ID") },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = pswInput,
          onValueChange = { pswInput = it },
          label = { Text("Password") },
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
          singleLine = true
        )

        if (error != null) {
          Text(error!!, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          onClick = {
            if (idInput == storedEmpId && pswInput == storedPsw) {
              onAuthSuccess()
            } else {
              error = "Invalid credentials"
            }
          },
          modifier = Modifier.fillMaxWidth().height(50.dp),
          colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
        ) {
          Text("SIGN IN", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
fun HeaderPanel(
  isLoggedIn: Boolean,
  userId: String,
  onLogout: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(8.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = TealSurface)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
          painter = painterResource(id = R.drawable.tfiber_logo),
          contentDescription = "T-Fiber Logo",
          modifier = Modifier.height(30.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text("T-Fiber", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

        }
      }

      if (isLoggedIn) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("Emp ID#$userId", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color(0xFFFF5F5F))
          }
        }
      }
    }
  }
}

@Composable
fun WebViewLayout(employeeId: String) {
  val url = "https://zfrmz.in/kBsJshOp1vfbQRFyZrpR"
  AndroidView(
    factory = { context ->
      WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
          override fun onPageFinished(view: WebView?, url: String?) {
            val script = """
              (function() {
                var labels = document.getElementsByTagName('label');
                for (var i = 0; i < labels.length; i++) {
                  if (labels[i].innerText.toLowerCase().includes('employee id')) {
                    var inputId = labels[i].getAttribute('for');
                    if (inputId) {
                      var input = document.getElementById(inputId);
                      if (input) {
                        input.value = '$employeeId';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                      }
                    }
                    break;
                  }
                }
              })();
            """.trimIndent()
            view?.evaluateJavascript(script, null)
          }
        }
        loadUrl(url)
      }
    },
    modifier = Modifier.fillMaxSize()
  )
}
