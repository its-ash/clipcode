import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../services/platform_channel_service.dart';
import '../services/settings_repository.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _autoCopy = true;
  double _timeoutSec = 10;
  Map<AppCategory, bool> _categories = {};
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final autoCopy = await PlatformChannelService.instance.getAutoCopy();
    final timeout = await PlatformChannelService.instance
        .getOverlayTimeoutSec();
    final categories = await SettingsRepository.instance.loadCategoryToggles();
    if (!mounted) return;
    setState(() {
      _autoCopy = autoCopy;
      _timeoutSec = timeout.toDouble().clamp(5, 30);
      _categories = categories;
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
                  padding: EdgeInsets.zero,
                  child: UiSwitchTile(
                    'Auto-copy to clipboard',
                    subtitle: 'Copy the OTP as soon as it is detected',
                    value: _autoCopy,
                    onChanged: (value) async {
                      setState(() => _autoCopy = value);
                      await PlatformChannelService.instance.setAutoCopy(value);
                    },
                  ),
                ),
                const SizedBox(height: 16),
                UiCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Overlay timeout', style: text.titleSmall),
                      const UiGap(UiSpace.xs),
                      Text(
                        '${_timeoutSec.round()} seconds before the banner auto-dismisses',
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
                const UiSectionHeader('App categories'),
                const SizedBox(height: 8),
                UiCard(
                  padding: EdgeInsets.zero,
                  child: Column(
                    children: AppCategory.values.map((category) {
                      final enabled = _categories[category] ?? true;
                      return UiSwitchTile(
                        category.label,
                        value: enabled,
                        onChanged: (value) async {
                          setState(() => _categories[category] = value);
                          await SettingsRepository.instance.setCategoryEnabled(
                            category,
                            value,
                          );
                        },
                      );
                    }).toList(),
                  ),
                ),
              ],
            ),
    );
  }
}
