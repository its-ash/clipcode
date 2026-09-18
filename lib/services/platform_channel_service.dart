import 'package:flutter/services.dart';

/// Thin Dart wrapper around the native platform channel contract declared in
/// android/.../MainActivity.kt. All prefs reads/writes here are backed by
/// the same Android SharedPreferences file the native services read
/// (SnapPrefs), so toggles set in Flutter take effect immediately for the
/// NotificationListenerService / SmsReceiver / OverlayService even when the
/// Flutter engine is later killed.
class PlatformChannelService {
  PlatformChannelService._();

  static final PlatformChannelService instance = PlatformChannelService._();

  static const MethodChannel _control = MethodChannel('com.itsash.clipotp/control');
  static const EventChannel _events = EventChannel('com.itsash.clipotp/events');

  Stream<dynamic>? _eventStream;

  Stream<dynamic> get liveEvents => _eventStream ??= _events.receiveBroadcastStream();

  Future<bool> getMasterEnabled() async =>
      (await _control.invokeMethod<bool>('getMasterEnabled')) ?? true;

  Future<void> setMasterEnabled(bool enabled) =>
      _control.invokeMethod('setMasterEnabled', {'enabled': enabled});

  Future<bool> getAutoCopy() async =>
      (await _control.invokeMethod<bool>('getAutoCopy')) ?? true;

  Future<void> setAutoCopy(bool enabled) =>
      _control.invokeMethod('setAutoCopy', {'enabled': enabled});

  Future<int> getOverlayTimeoutSec() async =>
      (await _control.invokeMethod<int>('getOverlayTimeoutSec')) ?? 10;

  Future<void> setOverlayTimeoutSec(int seconds) =>
      _control.invokeMethod('setOverlayTimeoutSec', {'seconds': seconds});

  Future<bool> isNotificationAccessGranted() async =>
      (await _control.invokeMethod<bool>('isNotificationAccessGranted')) ?? false;

  Future<bool> isOverlayPermissionGranted() async =>
      (await _control.invokeMethod<bool>('isOverlayPermissionGranted')) ?? false;

  Future<void> openNotificationAccessSettings() =>
      _control.invokeMethod('openNotificationAccessSettings');

  Future<void> openOverlayPermissionSettings() =>
      _control.invokeMethod('openOverlayPermissionSettings');
}
