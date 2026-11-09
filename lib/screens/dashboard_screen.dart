import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../services/platform_channel_service.dart';
import '../services/permission_service.dart';
import 'history_screen.dart';
import 'permissions_screen.dart';
import 'settings_screen.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});

  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen>
    with WidgetsBindingObserver {
  bool _masterEnabled = true;
  bool _loading = true;
  bool _notificationAccess = false;
  bool _overlayGranted = false;
  bool _smsGranted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _load();
  }

  Future<void> _load() async {
    final enabled = await PlatformChannelService.instance.getMasterEnabled();
    final notif = await PermissionService.instance
        .isNotificationAccessGranted();
    final overlay = await PermissionService.instance.isOverlayGranted();
    final sms = await PermissionService.instance.isSmsGranted();
    if (!mounted) return;
    setState(() {
      _masterEnabled = enabled;
      _notificationAccess = notif;
      _overlayGranted = overlay;
      _smsGranted = sms;
      _loading = false;
    });
  }

  bool get _allPermissionsGranted =>
      _notificationAccess && _overlayGranted && _smsGranted;

  Future<void> _toggleMaster(bool value) async {
    setState(() => _masterEnabled = value);
    await PlatformChannelService.instance.setMasterEnabled(value);
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.colors;
    final text = context.text;

    return Scaffold(
      appBar: AppBar(title: const Text('SnapOTP')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : UiRefresh(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  UiCard(
                    padding: const EdgeInsets.all(20),
                    child: Row(
                      children: [
                        UiIconBox(
                          Icons.shield_moon_outlined,
                          status: _masterEnabled
                              ? UiStatus.accent
                              : UiStatus.neutral,
                          size: 52,
                        ),
                        const UiGap(UiSpace.lg),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('OTP capture', style: text.titleMedium),
                              const UiGap(UiSpace.xs),
                              Text(
                                _masterEnabled
                                    ? 'Active — watching for codes'
                                    : 'Paused',
                                style: text.bodySmall,
                              ),
                            ],
                          ),
                        ),
                        UiSwitch(_masterEnabled, onChanged: _toggleMaster),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (!_allPermissionsGranted)
                    UiCard(
                      padding: EdgeInsets.zero,
                      child: UiListTile(
                        'Setup incomplete',
                        subtitle: 'Grant all permissions to enable capture',
                        icon: Icons.warning_amber_rounded,
                        iconStatus: UiStatus.danger,
                        chevron: true,
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(
                            builder: (_) => const PermissionsScreen(),
                          ),
                        ),
                      ),
                    ),
                  const SizedBox(height: 16),
                  const UiSectionHeader('Quick actions'),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.verified_user_outlined,
                          label: 'Permissions',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const PermissionsScreen(),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.history,
                          label: 'History',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const HistoryScreen(),
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.tune,
                          label: 'Settings',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(
                              builder: (_) => const SettingsScreen(),
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  UiCard(
                    child: Row(
                      children: [
                        Icon(Icons.privacy_tip_outlined, color: colors.accent),
                        const UiGap(UiSpace.md),
                        const Expanded(
                          child: Text(
                            'All processing happens on-device. No network calls, no analytics.',
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return UiCard(
      onTap: onTap,
      padding: const EdgeInsets.symmetric(vertical: 18),
      child: Column(
        children: [
          Icon(icon, color: context.colors.accent),
          const UiGap(UiSpace.sm),
          Text(label, style: context.text.bodySmall),
        ],
      ),
    );
  }
}
