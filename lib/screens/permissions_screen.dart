import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../services/permission_service.dart';

class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen>
    with WidgetsBindingObserver {
  bool _notificationAccess = false;
  bool _overlayGranted = false;
  bool _smsGranted = false;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    final notif = await PermissionService.instance
        .isNotificationAccessGranted();
    final overlay = await PermissionService.instance.isOverlayGranted();
    final sms = await PermissionService.instance.isSmsGranted();
    if (!mounted) return;
    setState(() {
      _notificationAccess = notif;
      _overlayGranted = overlay;
      _smsGranted = sms;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Permissions')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : UiRefresh(
              onRefresh: _refresh,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _PermissionCard(
                    icon: Icons.notifications_active_outlined,
                    title: 'Notification Access',
                    description:
                        'Required to read incoming notifications and detect OTP codes.',
                    granted: _notificationAccess,
                    onTap: () async {
                      await PermissionService.instance
                          .openNotificationAccessSettings();
                      _refresh();
                    },
                  ),
                  const SizedBox(height: 12),
                  _PermissionCard(
                    icon: Icons.picture_in_picture_alt_outlined,
                    title: 'Draw Over Other Apps',
                    description:
                        'Required to show the floating OTP overlay banner.',
                    granted: _overlayGranted,
                    onTap: () async {
                      await PermissionService.instance.openOverlaySettings();
                      _refresh();
                    },
                  ),
                  const SizedBox(height: 12),
                  _PermissionCard(
                    icon: Icons.sms_outlined,
                    title: 'SMS Access',
                    description:
                        'Required to read incoming SMS messages for OTP codes.',
                    granted: _smsGranted,
                    onTap: () async {
                      await PermissionService.instance.requestSms();
                      _refresh();
                    },
                  ),
                ],
              ),
            ),
    );
  }
}

class _PermissionCard extends StatelessWidget {
  const _PermissionCard({
    required this.icon,
    required this.title,
    required this.description,
    required this.granted,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String description;
  final bool granted;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = context.text;
    return UiCard(
      onTap: granted ? null : onTap,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          UiIconBox(icon, size: 44),
          const UiGap(14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: text.titleSmall),
                const UiGap(UiSpace.xs),
                Text(description, style: text.bodySmall),
              ],
            ),
          ),
          const UiGap(UiSpace.sm),
          UiStatusPill(
            granted ? 'Granted' : 'Grant',
            status: granted ? UiStatus.success : UiStatus.warning,
          ),
        ],
      ),
    );
  }
}
