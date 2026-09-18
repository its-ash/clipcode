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

class _DashboardScreenState extends State<DashboardScreen> with WidgetsBindingObserver {
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
    final notif = await PermissionService.instance.isNotificationAccessGranted();
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

  bool get _allPermissionsGranted => _notificationAccess && _overlayGranted && _smsGranted;

  Future<void> _toggleMaster(bool value) async {
    setState(() => _masterEnabled = value);
    await PlatformChannelService.instance.setMasterEnabled(value);
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('SnapOTP')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  ThemeCard(
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Row(
                        children: [
                          Container(
                            width: 52,
                            height: 52,
                            decoration: BoxDecoration(
                              color: (_masterEnabled ? scheme.primary : scheme.outline)
                                  .withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(16),
                            ),
                            child: Icon(
                              Icons.shield_moon_outlined,
                              color: _masterEnabled ? scheme.primary : scheme.outline,
                            ),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  'OTP capture',
                                  style: Theme.of(context).textTheme.titleMedium,
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  _masterEnabled ? 'Active — watching for codes' : 'Paused',
                                  style: Theme.of(context).textTheme.bodySmall,
                                ),
                              ],
                            ),
                          ),
                          ThemeSwitch(value: _masterEnabled, onChanged: _toggleMaster),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (!_allPermissionsGranted)
                    ThemeCard(
                      color: scheme.errorContainer.withValues(alpha: 0.4),
                      child: ListTile(
                        leading: Icon(Icons.warning_amber_rounded, color: scheme.error),
                        title: const Text('Setup incomplete'),
                        subtitle: const Text('Grant all permissions to enable capture'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute(builder: (_) => const PermissionsScreen()),
                        ),
                      ),
                    ),
                  const SizedBox(height: 16),
                  ThemeSectionHeader(title: 'Quick actions', actionLabel: ''),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.verified_user_outlined,
                          label: 'Permissions',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const PermissionsScreen()),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.history,
                          label: 'History',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const HistoryScreen()),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: _QuickAction(
                          icon: Icons.tune,
                          label: 'Settings',
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute(builder: (_) => const SettingsScreen()),
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  ThemeCard(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Row(
                        children: [
                          Icon(Icons.privacy_tip_outlined, color: scheme.primary),
                          const SizedBox(width: 12),
                          const Expanded(
                            child: Text(
                              'All processing happens on-device. No network calls, no analytics.',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  const _QuickAction({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return ThemeCard(
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 18),
          child: Column(
            children: [
              Icon(icon, color: scheme.primary),
              const SizedBox(height: 8),
              Text(label, style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
        ),
      ),
    );
  }
}
