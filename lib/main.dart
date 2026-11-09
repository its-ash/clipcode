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
    return const UiApp(
      title: 'SnapOTP',
      themeMode: ThemeMode.system,
      home: DashboardScreen(),
    );
  }
}
