import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:intl/intl.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();
  runApp(const MonthlyApprovalApp());
}

class MonthlyApprovalApp extends StatelessWidget {
  const MonthlyApprovalApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Approval Portal',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.teal,
          brightness: Brightness.dark,
        ),
      ),
      initialRoute: '/',
      routes: {
        '/': (context) => const AuthSplashGate(),
        '/login': (context) => const LoginScreen(),
        '/daily_activity': (context) => const DailyActivityScreen(),
        '/manager_approval': (context) => const ManagerApprovalScreen(),
      },
    );
  }
}

/// A launcher screen to route users to the appropriate screen depending on login state.
class AuthSplashGate extends StatefulWidget {
  const AuthSplashGate({super.key});

  @override
  State<AuthSplashGate> createState() => _AuthSplashGateState();
}

class _AuthSplashGateState extends State<AuthSplashGate> {
  @override
  void initState() {
    super.initState();
    _checkLoginStatus();
  }

  Future<void> _checkLoginStatus() async {
    final prefs = await SharedPreferences.getInstance();
    final bool isLoggedIn = prefs.getBool('isLoggedIn') ?? false;
    final String? userRole = prefs.getString('userRole'); // 'employee' or 'manager'

    if (mounted) {
      if (isLoggedIn && FirebaseAuth.instance.currentUser != null) {
        if (userRole == 'manager') {
          Navigator.pushReplacementNamed(context, '/manager_approval');
        } else {
          Navigator.pushReplacementNamed(context, '/daily_activity');
        }
      } else {
        Navigator.pushReplacementNamed(context, '/login');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: Center(
        child: CircularProgressIndicator(),
      ),
    );
  }
}

// ==========================================
// REQUIREMENT 2: FLUTTER CUSTOM LOGIN SCREEN
// ==========================================
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _employeeIdController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false;

  @override
  void dispose() {
    _employeeIdController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);
    final String employeeId = _employeeIdController.text.trim();
    final String password = _passwordController.text;

    // 1. Synthesize email by appending hidden organizational domain
    final String secureEmail = '$employeeId@company.com';

    try {
      // 2. Perform authentication via Firebase Auth
      final UserCredential credential = await FirebaseAuth.instance
          .signInWithEmailAndPassword(email: secureEmail, password: password);

      // 3. Fetch user information & role configuration from Firestore "employees" collection
      final DocumentSnapshot employeeData = await FirebaseFirestore.instance
          .collection('employees')
          .doc(employeeId)
          .get();

      String role = 'employee';
      if (employeeData.exists) {
        final data = employeeData.data() as Map<String, dynamic>?;
        role = data?['role'] ?? 'employee';
      }

      // 4. Save metadata states securely into local device SharedPreferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('isLoggedIn', true);
      await prefs.setString('employeeId', employeeId);
      await prefs.setString('userRole', role);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('⚡ Authenticated successfully.'),
            backgroundColor: Colors.teal,
          ),
        );
        // Navigate to appropriate panel based on user's identity role
        if (role == 'manager') {
          Navigator.pushReplacementNamed(context, '/manager_approval');
        } else {
          Navigator.pushReplacementNamed(context, '/daily_activity');
        }
      }
    } on FirebaseAuthException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Authentication Failed: ${e.message ?? "Unknown Error"}'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('An unexpected system error occurred: $e'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 400),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Icon(Icons.security, size: 80, color: Colors.teal),
                  const SizedBox(height: 24),
                  const Text(
                    'PORTAL ACCESS',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 2,
                    ),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Please fill in your custom credentials below',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.grey),
                  ),
                  const SizedBox(height: 32),
                  // Employee ID: Exactly 4 numeric digits matching regex validator
                  TextFormField(
                    controller: _employeeIdController,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Employee ID',
                      helperText: 'Must be exactly 4 numeric characters',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.badge_outlined),
                    ),
                    validator: (value) {
                      if (value == null || value.trim().isEmpty) {
                        return 'Employee ID is required';
                      }
                      if (!RegExp(r'^\d{4}$').hasMatch(value.trim())) {
                        return 'ID must be exactly 4 numeric characters';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 20),
                  // Password: Must be exactly 8 characters matching regex validator
                  TextFormField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: 'Pin / Password',
                      helperText: 'Must be exactly 8 characters',
                      border: OutlineInputBorder(),
                      prefixIcon: Icon(Icons.lock_outline),
                    ),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Password is required';
                      }
                      if (!RegExp(r'^.{8}$').hasMatch(value)) {
                        return 'Password must be exactly 8 characters';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 32),
                  _isLoading
                      ? const Center(child: CircularProgressIndicator())
                      : ElevatedButton(
                          onPressed: _handleLogin,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.teal,
                            foregroundColor: Colors.white,
                            padding: const EdgeInsets.symmetric(vertical: 16),
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(8),
                            ),
                          ),
                          child: const Text('LOG IN', style: TextStyle(fontWeight: FontWeight.bold)),
                        ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// ===============================================
// REQUIREMENT 3: FLUTTER DAILY ACTIVITY ENTRY SCREEN
// ===============================================
class DailyActivityScreen extends StatefulWidget {
  const DailyActivityScreen({super.key});

  @override
  State<DailyActivityScreen> createState() => _DailyActivityScreenState();
}

class _DailyActivityScreenState extends State<DailyActivityScreen> {
  final _activityFormKey = GlobalKey<FormState>();
  final _activityController = TextEditingController();
  String _employeeId = '';
  final String _currentDateStr = DateFormat('yyyy-MM-dd').format(DateTime.now());
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    _loadEmployeeData();
  }

  @override
  void dispose() {
    _activityController.dispose();
    super.dispose();
  }

  Future<void> _loadEmployeeData() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _employeeId = prefs.getString('employeeId') ?? '0000';
    });
  }

  Future<void> _submitActivity() async {
    if (!_activityFormKey.currentState!.validate()) return;
    setState(() => _isSaving = true);

    try {
      final now = DateTime.now();
      final monthString = DateFormat('yyyy-MM').format(now);

      // Create log and save directly to 'daily_activities' Firestore collection
      await FirebaseFirestore.instance.collection('daily_activities').add({
        'employeeId': _employeeId,
        'date': Timestamp.fromDate(now),
        'monthString': monthString,
        'activities': _activityController.text.trim(),
        'createdAt': FieldValue.serverTimestamp(),
      });

      _activityController.clear();

      if (mounted) {
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: const Row(
              children: [
                Icon(Icons.check_circle_outline, color: Colors.teal, size: 28),
                SizedBox(width: 10),
                Text('Activity Saved'),
              ],
            ),
            content: const Text(
              'Your daily activity task log has been successfully committed to Firestore under daily_activities.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('OK', style: TextStyle(color: Colors.teal)),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Firebase write failure: $e'),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _isSaving = false);
    }
  }

  Future<void> _logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    await FirebaseAuth.instance.signOut();
    if (mounted) {
      Navigator.pushReplacementNamed(context, '/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Daily Work Log'),
        backgroundColor: Colors.teal.shade900,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Log Out',
            onPressed: _logout,
          )
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24.0),
        child: Form(
          key: _activityFormKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Card(
                elevation: 4,
                margin: const EdgeInsets.only(bottom: 24),
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      Column(
                        children: [
                          const Text('EMPLOYEE ID', style: TextStyle(color: Colors.grey, fontSize: 12)),
                          const SizedBox(height: 4),
                          Text(
                            _employeeId,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18, color: Colors.teal),
                          ),
                        ],
                      ),
                      const VerticalDivider(width: 20, thickness: 1),
                      Column(
                        children: [
                          const Text('WORK DATE', style: TextStyle(color: Colors.grey, fontSize: 12)),
                          const SizedBox(height: 4),
                          Text(
                            _currentDateStr,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                          ),
                        ],
                      )
                    ],
                  ),
                ),
              ),
              const Text(
                'Reporting Entry Form',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              // Activities Performed TextFormField: 5 fixed display/max lines
              TextFormField(
                controller: _activityController,
                maxLines: 5,
                decoration: const InputDecoration(
                  labelText: 'Activities Performed',
                  hintText: 'Enter a comprehensive detailed summary of work done today...',
                  border: OutlineInputBorder(),
                  alignLabelWithHint: true,
                ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Please enter details of tasks completed today';
                  }
                  if (value.trim().length < 10) {
                    return 'Please provide a more descriptive summary (min 10 characters)';
                  }
                  return null;
                },
              ),
              const SizedBox(height: 24),
              _isSaving
                  ? const Center(child: CircularProgressIndicator())
                  : ElevatedButton.icon(
                      onPressed: _submitActivity,
                      icon: const Icon(Icons.save),
                      label: const Text('SUBMIT DAY WORK LOG'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.teal,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
            ],
          ),
        ),
      ),
    );
  }
}

// ===============================================
// ROUTE MODULE: MANAGER APPROVAL UI
// ===============================================
class ManagerApprovalScreen extends StatefulWidget {
  const ManagerApprovalScreen({super.key});

  @override
  State<ManagerApprovalScreen> createState() => _ManagerApprovalScreenState();
}

class _ManagerApprovalScreenState extends State<ManagerApprovalScreen> {
  String _managerId = '';

  @override
  void initState() {
    super.initState();
    _loadManagerId();
  }

  Future<void> _loadManagerId() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _managerId = prefs.getString('employeeId') ?? '9999';
    });
  }

  Future<void> _updateReportStatus(String reportId, String newStatus) async {
    try {
      await FirebaseFirestore.instance
          .collection('monthly_reports')
          .doc(reportId)
          .update({'status': newStatus, 'approvedByManagerAt': FieldValue.serverTimestamp()});

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Report successfully updated to: $newStatus'),
            backgroundColor: Colors.teal,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Approval update failed: $e'), backgroundColor: Colors.redAccent),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Manager Approval Portal'),
        backgroundColor: Colors.teal.shade900,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              final prefs = await SharedPreferences.getInstance();
              await prefs.clear();
              Navigator.pushReplacementNamed(context, '/login');
            },
          )
        ],
      ),
      body: StreamBuilder<QuerySnapshot>(
        stream: FirebaseFirestore.instance
            .collection('monthly_reports')
            .where('managerId', isEqualTo: _managerId)
            .snapshots(),
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (!snapshot.hasData || snapshot.data!.docs.isEmpty) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.assignment_turned_in, size: 64, color: Colors.grey.shade600),
                    const SizedBox(height: 16),
                    const Text(
                      'No Reports Found',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'There are no pending monthly compiled logs registered for Manager ID $_managerId currently.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.grey),
                    ),
                  ],
                ),
              ),
            );
          }

          final reports = snapshot.data!.docs;

          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: reports.length,
            itemBuilder: (context, index) {
              final reportDoc = reports[index];
              final data = reportDoc.data() as Map<String, dynamic>;
              final String reportId = reportDoc.id;
              final String empId = data['employeeId'] ?? 'Unknown';
              final String monthYear = data['monthYear'] ?? 'Unknown';
              final String status = data['status'] ?? 'Pending Manager Approval';
              final String activities = data['aggregatedActivities'] ?? 'No aggregated items.';

              return Card(
                elevation: 3,
                margin: const EdgeInsets.only(bottom: 16),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Employee #$empId', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                          Chip(
                            label: Text(status),
                            backgroundColor: status == 'Pending Manager Approval'
                                ? Colors.deepOrange.withOpacity(0.2)
                                : Colors.teal.withOpacity(0.2),
                          )
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text('Period: $monthYear', style: const TextStyle(color: Colors.tealAccent, fontSize: 13)),
                      const Divider(height: 20),
                      Text(activities, style: const TextStyle(fontFamily: 'monospace', fontSize: 12)),
                      const SizedBox(height: 16),
                      if (status == 'Pending Manager Approval')
                        ElevatedButton.icon(
                          onPressed: () => _updateReportStatus(reportId, 'Approved by Manager'),
                          icon: const Icon(Icons.verified),
                          label: const Text('APPROVE & ESCALATE TO ADMIN'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.teal,
                            foregroundColor: Colors.white,
                          ),
                        ),
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
