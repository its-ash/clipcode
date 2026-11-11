import 'package:shared_preferences/shared_preferences.dart';

/// App category presets used for the notification whitelist/blacklist
/// selector. Kept simple (category toggles) rather than enumerating every
/// installed package, per spec.
enum AppCategory { banking, messaging, email, shopping, social, other }

extension AppCategoryLabel on AppCategory {
  String get label => switch (this) {
    AppCategory.banking => 'Banking & Finance',
    AppCategory.messaging => 'Messaging',
    AppCategory.email => 'Email',
    AppCategory.shopping => 'Shopping & Delivery',
    AppCategory.social => 'Social',
    AppCategory.other => 'Other apps',
  };
}

/// Local (Dart-only) preferences that don't need to be read by native code:
/// category whitelist. Master toggle / overlay timeout live in
/// the native-shared prefs via PlatformChannelService instead.
class SettingsRepository {
  SettingsRepository._();

  static final SettingsRepository instance = SettingsRepository._();

  static const _categoryKeyPrefix = 'category_enabled_';

  Future<Map<AppCategory, bool>> loadCategoryToggles() async {
    final prefs = await SharedPreferences.getInstance();
    return {
      for (final c in AppCategory.values)
        c: prefs.getBool('$_categoryKeyPrefix${c.name}') ?? true,
    };
  }

  Future<void> setCategoryEnabled(AppCategory category, bool enabled) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('$_categoryKeyPrefix${category.name}', enabled);
  }
}
