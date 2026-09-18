import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import 'screens/dashboard_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SnapOtpApp());
}

class SnapOtpApp extends StatelessWidget {
  const SnapOtpApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SnapOTP',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme(),
      darkTheme: AppTheme.darkTheme(),
      themeMode: ThemeMode.system,
      home: const DashboardScreen(),
    );
  }
}
