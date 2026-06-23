package com.example

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// DATA MODELS FOR THE WORKFLOW SIMULATOR
// ==========================================
data class EmployeeUser(
  val employeeId: String,
  val name: String,
  val role: String, // "employee" or "manager"
  val reportingManagerId: String,
  val managerEmail: String,
  val psw: String
)

data class DailyActivity(
  val id: String = UUID.randomUUID().toString(),
  val employeeId: String,
  val date: String,
  val activities: String,
  val timestamp: Long = System.currentTimeMillis()
)

data class MonthlyReport(
  val id: String, // employeeId + "_" + month
  val employeeId: String,
  val monthYear: String,
  val aggregatedActivities: String,
  var status: String, // "Pending Manager Approval", "Approved by Manager", "Sent to Admin"
  val managerId: String,
  val managerEmail: String,
  val submittedAt: String,
  var approvedAt: String? = null,
  var sentToAdminAt: String? = null
)

data class ConsoleLog(
  val source: String, // "AUTH" | "FIRESTORE" | "CLOUD_FUNCTIONS" | "SYSTEM"
  val message: String,
  val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

class MainActivity : ComponentActivity() {
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
  val sharedPrefs = remember { context.getSharedPreferences("simulation_prefs", Context.MODE_PRIVATE) }

  // 1. Setup Mock Firestore Collections (In-memory reactive DB states)
  val employeesCollection = remember {
    listOf(
      EmployeeUser("1234", "Jane Cooper", "employee", "9999", "mgr.chen@company.com", "abc12345"),
      EmployeeUser("5678", "Robert Johnson", "employee", "9999", "mgr.chen@company.com", "xyz12345"),
      EmployeeUser("9999", "Manager Chen", "manager", "0000", "admin-team@company.com", "mgr12345")
    )
  }

  val dailyActivitiesCollection = remember {
    mutableStateListOf<DailyActivity>(
      DailyActivity("1", "1234", "2026-06-01", "Completed responsive navigation bar components in Flutter."),
      DailyActivity("2", "1234", "2026-06-02", "Configured Firebase Google Identity platform parameters."),
      DailyActivity("3", "5678", "2026-06-01", "Refactored main controller schema classes to secure types.")
    )
  }

  val monthlyReportsCollection = remember {
    mutableStateListOf<MonthlyReport>()
  }

  // 2. State Indicators & UI Managers
  var currentLoggedInUserId by remember { mutableStateOf(sharedPrefs.getString("employeeId", "") ?: "") }
  var currentUserRole by remember { mutableStateOf(sharedPrefs.getString("userRole", "") ?: "") }
  var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("isLoggedIn", false)) }

  val consoleLogs = remember {
    mutableStateListOf<ConsoleLog>(
      ConsoleLog("SYSTEM", "Apex Approval workflow simulator initialized."),
      ConsoleLog("SYSTEM", "Mock database models loaded: 3 employees, 3 activity entries.")
    )
  }

  fun appendLog(source: String, msg: String) {
    consoleLogs.add(0, ConsoleLog(source, msg))
  }

  // Handle Logout
  val logoutAction = {
    sharedPrefs.edit()
      .clear()
      .apply()
    currentLoggedInUserId = ""
    currentUserRole = ""
    isLoggedIn = false
    appendLog("AUTH", "User cleared session credentials. Disconnected from live stream.")
  }

  // Main UI Shell
  Scaffold(
    modifier = Modifier.fillMaxSize()
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(MaterialTheme.colorScheme.background)
    ) {
      // Modern Top Unified Header Bar
      HeaderPanel(
        isLoggedIn = isLoggedIn,
        userId = currentLoggedInUserId,
        role = currentUserRole,
        onLogout = logoutAction
      )

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (!isLoggedIn) {
          // Authentication Mode Screen
          LocalLoginLayout(
            employees = employeesCollection,
            onAuthSuccess = { empId, role ->
              sharedPrefs.edit()
                .putBoolean("isLoggedIn", true)
                .putString("employeeId", empId)
                .putString("userRole", role)
                .apply()
              currentLoggedInUserId = empId
              currentUserRole = role
              isLoggedIn = true
              appendLog("AUTH", "Firebase Auth successful: $empId@company.com connected.")
              appendLog("FIRESTORE", "Reading employee metadata for employee #$empId...")
            },
            onAuthFailure = { error ->
              appendLog("AUTH", "Authentication validation failed: $error")
            }
          )
        } else {
          // Working Portal Mode Screen
          DashboardLayout(
            employeeId = currentLoggedInUserId,
            userRole = currentUserRole,
            employees = employeesCollection,
            dailyLogs = dailyActivitiesCollection,
            monthlyReports = monthlyReportsCollection,
            onLogActivity = { activitiesText ->
              val todayFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
              val log = DailyActivity(employeeId = currentLoggedInUserId, date = todayFormatted, activities = activitiesText)
              dailyActivitiesCollection.add(log)
              appendLog("FIRESTORE", "Successfully added document into `/daily_activities` path: ${log.id}")
            },
            onMonthlyCompile = { empId ->
              // Simulating End-Of-Month Scheduled PubSub process
              appendLog("SYSTEM", "Pub/Sub schedule task triggered. Executing aggregation engine...")
              val empInfo = employeesCollection.find { it.employeeId == empId }
              if (empInfo == null) {
                appendLog("SYSTEM", "Error compiling: employee not found")
                return@DashboardLayout
              }

              // Filter current month logs
              val curMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
              val empLogs = dailyActivitiesCollection.filter { it.employeeId == empId }
              if (empLogs.isEmpty()) {
                appendLog("SYSTEM", "Skip compile: Employee #$empId has 0 entries recorded this month.")
                Toast.makeText(context, "No daily logs found to compile!", Toast.LENGTH_SHORT).show()
                return@DashboardLayout
              }

              // Concatenate
              var aggregatedText = "Aggregated Activities for $curMonth:\n"
              empLogs.forEachIndexed { i, log ->
                aggregatedText += "• [${log.date}] - Day ${i + 1}: ${log.activities}\n"
              }

              val reportId = "${empId}_$curMonth"
              val existingReport = monthlyReportsCollection.find { it.id == reportId }
              if (existingReport != null) {
                monthlyReportsCollection.remove(existingReport)
              }

              val newReport = MonthlyReport(
                id = reportId,
                employeeId = empId,
                monthYear = curMonth,
                aggregatedActivities = aggregatedText,
                status = "Pending Manager Approval",
                managerId = empInfo.reportingManagerId,
                managerEmail = empInfo.managerEmail,
                submittedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
              )

              monthlyReportsCollection.add(newReport)
              appendLog("FIRESTORE", "Document generated under `/monthly_reports/$reportId` collection.")
              appendLog("CLOUD_FUNCTIONS", "[Schedule Trigger v2] Compiled logs for Employee #$empId. Initiating email dispatcher...")
              appendLog("CLOUD_FUNCTIONS", "[Email Dispatch] Sent review deep link to Reporting Manager at ${empInfo.managerEmail}")
              Toast.makeText(context, "Pub/Sub compilation finished! Report sent to Manager.", Toast.LENGTH_LONG).show()
            },
            onManagerAction = { reportId, updateStatus ->
              val report = monthlyReportsCollection.find { it.id == reportId }
              if (report != null) {
                // 1. Change to Approved by Manager
                report.status = "Approved by Manager"
                report.approvedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                appendLog("FIRESTORE", "Document updated `/monthly_reports/$reportId` status -> \"Approved by Manager\"")
                
                // 2. Trigger onUpdate simulation
                appendLog("CLOUD_FUNCTIONS", "[onUpdate Trigger v2] State change detected on report: $reportId")
                appendLog("CLOUD_FUNCTIONS", "[onUpdate Escalation] Changing database status model to 'Sent to Admin'...")
                
                // Complete update flow
                report.status = "Sent to Admin"
                report.sentToAdminAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                
                // Refresh list state manually to trigger recomposition
                val idx = monthlyReportsCollection.indexOf(report)
                if (idx != -1) {
                  monthlyReportsCollection[idx] = report.copy(status = "Sent to Admin")
                }

                appendLog("FIRESTORE", "Document updated `/monthly_reports/$reportId` status -> \"Sent to Admin\"")
                appendLog("CLOUD_FUNCTIONS", "[Email Escalated] Sent approved activity statement with payload to Admin Team: admin-team@company.com")
                Toast.makeText(context, "Manager Approved! Escalated automatically to Admin via Cloud Functions.", Toast.LENGTH_LONG).show()
              }
            },
            appendLog = ::appendLog
          )
        }
      }

      // Live Development & Firebase Console Log Console (Bottom Panel)
      BackendTerminalConsole(logs = consoleLogs)
    }
  }
}

// ==========================================
// HEADER COMPONENT PANEL
// ==========================================
@Composable
fun HeaderPanel(
  isLoggedIn: Boolean,
  userId: String,
  role: String,
  onLogout: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = TealSurface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Logo",
            tint = TealPrimary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "FLOWPORTAL",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TealPrimary,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.SansSerif
          )
        }
        Text(
          text = "Firebase Workflow Simulation Hub",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (isLoggedIn) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Status Pill Badge
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(TealSurfaceVariant)
              .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (role == "manager") StatusEscalated else TealPrimary)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${role.uppercase()}: #$userId",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(
            onClick = onLogout,
            modifier = Modifier
              .size(32.dp)
              .testTag("logout_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ExitToApp,
              contentDescription = "Log out",
              tint = Color(0xFFFF5F5F),
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

// ==========================================
// REQUIREMENT 2: NATIVE COMPOSE PORT RE-IMPLEMENTATION OF FLUTTER CUSTOM LOGIN
// ==========================================
@Composable
fun LocalLoginLayout(
  employees: List<EmployeeUser>,
  onAuthSuccess: (String, String) -> Unit,
  onAuthFailure: (String) -> Unit
) {
  var employeeIdInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }

  var employeeIdError by remember { mutableStateOf<String?>(null) }
  var passwordError by remember { mutableStateOf<String?>(null) }

  // Regex validators matching Flutter constraints
  val idRegex = Regex("^\\d{4}$")
  val passRegex = Regex("^.{8}$")

  val context = LocalContext.current

  fun performLoginSubmit() {
    var valid = true

    // Validate Employee ID
    if (employeeIdInput.trim().isEmpty()) {
      employeeIdError = "Employee ID is required."
      valid = false
    } else if (!idRegex.matches(employeeIdInput.trim())) {
      employeeIdError = "Must be exactly 4 numeric characters."
      valid = false
    } else {
      employeeIdError = null
    }

    // Validate Password
    if (passwordInput.isEmpty()) {
      passwordError = "Password is required."
      valid = false
    } else if (!passRegex.matches(passwordInput)) {
      passwordError = "Must be exactly 8 characters."
      valid = false
    } else {
      passwordError = null
    }

    if (!valid) {
      onAuthFailure("Local regex constraint check failed.")
      return
    }

    // Attempt Verification
    val matchedUser = employees.find {
      it.employeeId == employeeIdInput.trim() && it.psw == passwordInput
    }

    if (matchedUser != null) {
      onAuthSuccess(matchedUser.employeeId, matchedUser.role)
    } else {
      val errMsg = "Firebase Auth: Invalid email/password combination."
      onAuthFailure("No database match found for authentication email: ${employeeIdInput.trim()}@company.com")
      Toast.makeText(context, "Authentication Failed!\nCheck credentials details.", Toast.LENGTH_LONG).show()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = TealSurface),
      elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Creative Icon Badge
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
              Brush.linearGradient(
                listOf(TealPrimary, TealSecondary)
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock",
            tint = TealOnPrimary,
            modifier = Modifier.size(28.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "PORTAL ENTRANCE",
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 2.sp,
          color = Color.White
        )
        Text(
          text = "Verify credentials for organizational access",
          fontSize = 12.sp,
          color = TealTextSecondary,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Helper Tutorial Tooltip
        Card(
          colors = CardDefaults.cardColors(containerColor = TealSurfaceVariant),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "💡 Interactive Testing Accounts:",
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = TealPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "• Employee: ID [1234] Password [abc12345] (8 chars)\n• Manager: ID [9999] Password [mgr12345] (8 chars)",
              fontSize = 11.sp,
              lineHeight = 15.sp,
              color = TealTextPrimary
            )
          }
        }

        // Input 1: Employee ID Input (Regex validator: 4 characters)
        OutlinedTextField(
          value = employeeIdInput,
          onValueChange = {
            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
              employeeIdInput = it
            }
          },
          label = { Text("Employee ID") },
          placeholder = { Text("e.g. 1234") },
          isError = employeeIdError != null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_id_input"),
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealPrimary,
            unfocusedBorderColor = TealSurfaceVariant,
            errorBorderColor = ErrorRed
          )
        )
        if (employeeIdError != null) {
          Text(
            text = employeeIdError!!,
            color = ErrorRed,
            fontSize = 11.sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 4.dp, top = 2.dp, bottom = 8.dp)
          )
        } else {
          Text(
            text = "Must be exactly 4 numeric digits. Appends custom domain @company.com in OAuth",
            color = TealTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
          )
        }

        // Input 2: Pin / Password (Regex validator: 8 characters)
        OutlinedTextField(
          value = passwordInput,
          onValueChange = { passwordInput = it },
          label = { Text("Credential Password") },
          placeholder = { Text("********") },
          isError = passwordError != null,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("password_input"),
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary) },
          trailingIcon = {
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
              Text(
                text = if (passwordVisible) "HIDE" else "SHOW",
                fontSize = 11.sp,
                color = TealPrimary,
                fontWeight = FontWeight.Bold
              )
            }
          },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TealPrimary,
            unfocusedBorderColor = TealSurfaceVariant,
            errorBorderColor = ErrorRed
          )
        )
        if (passwordError != null) {
          Text(
            text = passwordError!!,
            color = ErrorRed,
            fontSize = 11.sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 4.dp, top = 2.dp)
          )
        } else {
          Text(
            text = "Must be exactly 8 characters.",
            color = TealTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier
              .fillMaxWidth()
              .padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = ::performLoginSubmit,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("login_button"),
          colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = "SIGN IN TO PORTAL",
            fontWeight = FontWeight.Bold,
            color = TealOnPrimary,
            letterSpacing = 1.sp
          )
        }
      }
    }
  }
}

// ==========================================
// WORKSPACE DASHBOARD (TAB-ROUTED LOGIC)
// ==========================================
@Composable
fun DashboardLayout(
  employeeId: String,
  userRole: String,
  employees: List<EmployeeUser>,
  dailyLogs: List<DailyActivity>,
  monthlyReports: List<MonthlyReport>,
  onLogActivity: (String) -> Unit,
  onMonthlyCompile: (String) -> Unit,
  onManagerAction: (String, String) -> Unit,
  appendLog: (String, String) -> Unit
) {
  var selectedTab by remember { mutableStateOf(if (userRole == "manager") 1 else 0) }

  Column(modifier = Modifier.fillMaxSize()) {
    // Top Tabs
    TabRow(
      selectedTabIndex = selectedTab,
      containerColor = TealSurface,
      contentColor = TealPrimary,
      indicator = { tabPositions ->
        TabRowDefaults.SecondaryIndicator(
          modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
          color = TealPrimary
        )
      }
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Employee Log Form") },
        icon = { Icon(Icons.Default.Create, contentDescription = null) }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Manager Review") },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
      )
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      if (selectedTab == 0) {
        // Employee Tab Frame
        EmployeeLogPanel(
          employeeId = employeeId,
          dailyLogs = dailyLogs.filter { it.employeeId == employeeId },
          onSubmitActivity = onLogActivity,
          onTriggerEOMAggregation = onMonthlyCompile
        )
      } else {
        // Manager Approval Frame
        ManagerPortalPanel(
          currentUserRole = userRole,
          currentManagerId = employeeId,
          reports = monthlyReports,
          onApproveReport = onManagerAction,
          appendLog = appendLog
        )
      }
    }
  }
}

// ==========================================
// REQUIREMENT 3 & 4: EMPLOYEE DAILY ACTIVITY LOGGER
// ==========================================
@Composable
fun EmployeeLogPanel(
  employeeId: String,
  dailyLogs: List<DailyActivity>,
  onSubmitActivity: (String) -> Unit,
  onTriggerEOMAggregation: (String) -> Unit
) {
  var activitiesInput by remember { mutableStateOf("") }
  val currentDateStr = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(Date()) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Current Badging Overview
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TealSurface)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(TealSurfaceVariant)
              .border(1.dp, TealPrimary, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary)
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Active Session: Employee #${employeeId}",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = currentDateStr,
              fontSize = 12.sp,
              color = TealTextSecondary
            )
          }
        }
      }
    }

    // Daily Activities Form
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TealSurface)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Text(
            text = "✍️ Write Daily Work Log",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TealPrimary
          )
          Spacer(modifier = Modifier.height(12.dp))

          // Fixed 5 lines TextField as requested in UI paradigms
          OutlinedTextField(
            value = activitiesInput,
            onValueChange = { activitiesInput = it },
            label = { Text("Activities Performed") },
            placeholder = { Text("Summarize work log accomplishments today... (e.g. built Flutter validation login hooks, deployed node index.js to Firebase Cloud functions)") },
            modifier = Modifier
              .fillMaxWidth()
              .height(135.dp) // Generous height to fit about 5 lines
              .testTag("activities_field"),
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = TealPrimary,
              unfocusedBorderColor = TealSurfaceVariant
            )
          )

          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              if (activitiesInput.trim().isNotEmpty()) {
                onSubmitActivity(activitiesInput)
                activitiesInput = ""
              }
            },
            modifier = Modifier
              .align(Alignment.End)
              .testTag("submit_activity_button"),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
          ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = TealOnPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("LOG ACTIVITY", color = TealOnPrimary, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Requirement 4 scheduled emulator trigger
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = TealSurfaceVariant),
        border = borderStroke(TealPrimary.copy(alpha = 0.4f))
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "End-Of-Month Aggregator",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
          Text(
            text = "Trigger Pub/Sub task instantly to aggregate daily entries, compile a single Report, and send deep links to Manager Chen.",
            fontSize = 11.sp,
            color = TealTextSecondary,
            modifier = Modifier.padding(vertical = 8.dp)
          )
          Button(
            onClick = { onTriggerEOMAggregation(employeeId) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealSecondary)
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("RUN SCHEDULED ACCUMULATOR CRON", fontWeight = FontWeight.SemiBold)
          }
        }
      }
    }

    // Historical Logs Feed
    item {
      Text(
        text = "Your Log Entries for current period",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textDecoration = null,
        color = TealPrimary,
        modifier = Modifier.padding(top = 8.dp)
      )
    }

    if (dailyLogs.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(TealSurface, RoundedCornerShape(12.dp))
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Text("No log documents recorded today.", color = TealTextSecondary, fontSize = 12.sp)
        }
      }
    } else {
      items(dailyLogs.reversed()) { log ->
        Card(
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = TealSurface),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(log.activities, fontSize = 13.sp, color = Color.White)
              Spacer(modifier = Modifier.height(4.dp))
              Text("Log Ref: ${log.id.take(8)}... | ${log.date}", fontSize = 11.sp, color = TealTextSecondary)
            }
          }
        }
      }
    }
  }
}

// ==========================================
// REQUIREMENT 5: MANAGER REVIEW & ONUPDATE TRIGGER
// ==========================================
@Composable
fun ManagerPortalPanel(
  currentUserRole: String,
  currentManagerId: String,
  reports: List<MonthlyReport>,
  onApproveReport: (String, String) -> Unit,
  appendLog: (String, String) -> Unit
) {
  val context = LocalContext.current

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Guard check warning if current user is Employee but viewing manager tab
    if (currentUserRole != "manager") {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = Color(0xFF3A211B)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "⚠️ Restricted Section - Role Manager Required",
              fontWeight = FontWeight.Bold,
              color = ErrorRed,
              fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "You are authenticated as an 'employee' role. Although you can view this simulation tab, your manager login credentials (ID '9999') are typically required to initiate approval workflows.",
              fontSize = 11.sp,
              color = Color(0xFFFFCDC5),
              lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
              onClick = {
                Toast.makeText(context, "Log in as Manager Chen (9999 / mgr12345)", Toast.LENGTH_LONG).show()
              },
              colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("ROLE DEMO REMINDER")
            }
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "📋 Pending Compiled Reports Approval",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = TealPrimary
        )
        Text(
          text = "Reports Count: ${reports.size}",
          fontSize = 11.sp,
          color = TealTextSecondary
        )
      }
    }

    if (reports.isEmpty()) {
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(TealSurface, RoundedCornerShape(12.dp))
            .padding(32.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealTextSecondary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No compiled logs available.", color = TealTextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            Text(
              "Go back to the Employee Log Form and click 'RUN SCHEDULED ACCUMULATOR CRON' to generate compile items.",
              fontSize = 10.sp,
              color = TealTextSecondary.copy(alpha = 0.7f),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
        }
      }
    } else {
      items(reports) { report ->
        Card(
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = TealSurface),
          border = borderStroke(
            if (report.status == "Sent to Admin") StatusEscalated.copy(alpha = 0.5f) else StatusPending.copy(alpha = 0.5f)
          )
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp)
          ) {
            // Header Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "Employee ID #${report.employeeId}",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = Color.White
                )
                Text(
                  text = "Period: ${report.monthYear}",
                  fontSize = 11.sp,
                  color = TealTextSecondary
                )
              }

              // Custom Status Pill
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(
                    when (report.status) {
                      "Sent to Admin" -> StatusEscalated.copy(alpha = 0.15f)
                      "Approved by Manager" -> StatusApproved.copy(alpha = 0.15f)
                      else -> StatusPending.copy(alpha = 0.15f)
                    }
                  )
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = report.status,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = when (report.status) {
                    "Sent to Admin" -> StatusEscalated
                    "Approved by Manager" -> StatusApproved
                    else -> StatusPending
                  }
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = TealSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))

            // Body Aggregated Activities Text Box
            Text(
              text = report.aggregatedActivities,
              fontSize = 12.sp,
              lineHeight = 16.sp,
              color = TealTextPrimary,
              maxLines = 10,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier
                .background(TealSurfaceVariant, RoundedCornerShape(6.dp))
                .padding(10.dp)
                .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (report.status == "Pending Manager Approval") {
              Button(
                onClick = {
                  onApproveReport(report.id, "Approved by Manager")
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("approve_button_${report.employeeId}"),
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TealOnPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("APPROVE & TRANSMIT", color = TealOnPrimary, fontWeight = FontWeight.Bold)
              }
            } else if (report.status == "Sent to Admin") {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(TealSurfaceVariant, RoundedCornerShape(6.dp))
                  .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Info,
                  contentDescription = null,
                  tint = StatusApproved,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Escalation Complete: Dispatched logs to admin-team@company.com",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = StatusApproved
                )
              }
            }
          }
        }
      }
    }
  }
}

// ==========================================
// STYLISH CONSOLE TERMINAL EXPLAINER LOG
// ==========================================
@Composable
fun BackendTerminalConsole(logs: List<ConsoleLog>) {
  var isExpanded by remember { mutableStateOf(true) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .padding(8.dp),
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF070B0A))
  ) {
    Column {
      // Small Debug Tab Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { isExpanded = !isExpanded }
          .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(Color(0xFF39FF14)) // Cyber Lime
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "FIREBASE EVENT TRAFFIC & LOGS MONITOR",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF39FF14),
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
          )
        }
        Icon(
          imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
          contentDescription = null,
          tint = Color(0xFF39FF14),
          modifier = Modifier.size(16.dp)
        )
      }

      if (isExpanded) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            reverseLayout = false
          ) {
            items(logs) { log ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
              ) {
                Text(
                  text = "[${log.timestamp}]",
                  fontSize = 9.sp,
                  color = Color.Gray,
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(end = 6.dp)
                )

                // Colored tag based on source Type
                Text(
                  text = when (log.source) {
                    "AUTH" -> "[FIREBASE_AUTH]"
                    "FIRESTORE" -> "[FIRESTORE]"
                    "CLOUD_FUNCTIONS" -> "[CLOUD_FUNCTIONS]"
                    "SYSTEM" -> "[SIMULATION]"
                    else -> "[SYSTEM]"
                  },
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = when (log.source) {
                    "AUTH" -> Color(0xFFFFB300)
                    "FIRESTORE" -> Color(0xFF29B6F6)
                    "CLOUD_FUNCTIONS" -> Color(0xFFAB47BC)
                    else -> Color(0xFF66BB6A)
                  },
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.padding(end = 6.dp)
                )

                Text(
                  text = log.message,
                  fontSize = 11.sp,
                  color = Color(0xFF76ECB7),
                  fontFamily = FontFamily.Monospace,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }
      }
    }
  }
}

// Border Stroke Utility
fun borderStroke(color: Color) = androidx.compose.foundation.BorderStroke(
  width = 1.dp,
  color = color
)
