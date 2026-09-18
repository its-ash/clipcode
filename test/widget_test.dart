import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:clipotp/main.dart';

void main() {
  testWidgets('App renders dashboard title', (WidgetTester tester) async {
    await tester.pumpWidget(const SnapOtpApp());
    await tester.pump();
    expect(find.text('SnapOTP'), findsWidgets);
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
