import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import 'screens/dashboard_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ClipcodeApp());
}

class ClipcodeApp extends StatelessWidget {
  const ClipcodeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const UiApp(
      title: 'Clipcode',
      home: DashboardScreen(),
    );
  }
}
