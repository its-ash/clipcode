import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../services/platform_channel_service.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  double _timeoutSec = 10;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final timeout = await PlatformChannelService.instance
        .getOverlayTimeoutSec();
    if (!mounted) return;
    setState(() {
      _timeoutSec = timeout.toDouble().clamp(5, 30);
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final text = context.text;
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                UiCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Overlay timeout', style: text.titleSmall),
                      const UiGap(UiSpace.xs),
                      Text(
                        '${_timeoutSec.round()} seconds before the pop-up hides. Tap Copy on the pop-up to copy the code.',
                        style: text.bodySmall,
                      ),
                      UiSlider(
                        value: _timeoutSec,
                        min: 5,
                        max: 30,
                        divisions: 25,
                        showValue: false,
                        onChanged: (value) =>
                            setState(() => _timeoutSec = value),
                      ),
                      Align(
                        alignment: Alignment.centerRight,
                        child: UiButton(
                          'Save',
                          variant: UiButtonVariant.ghost,
                          status: UiStatus.accent,
                          onPressed: () => PlatformChannelService.instance
                              .setOverlayTimeoutSec(_timeoutSec.round()),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                const UiAppearanceSection(),
                const SizedBox(height: 16),
                // No network permission: the row opens the latest APK download in the browser.
                const UiUpdateSection(storeId: 'clipcode', checkForUpdates: false),
                const SizedBox(height: 16),
                const UiDeveloperSection(),
              ],
            ),
    );
  }
}
