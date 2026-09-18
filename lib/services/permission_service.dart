import 'package:permission_handler/permission_handler.dart';

import 'platform_channel_service.dart';

/// Wraps runtime SMS permission requests (permission_handler) and the two
/// settings-deep-link special permissions (notification access, overlay)
/// which are queried/opened via the native platform channel.
class PermissionService {
  PermissionService._();

  static final PermissionService instance = PermissionService._();

  Future<bool> isSmsGranted() async {
    final receive = await Permission.sms.status;
    return receive.isGranted;
  }

  Future<bool> requestSms() async {
    final result = await [Permission.sms].request();
    return result[Permission.sms]?.isGranted ?? false;
  }

  Future<bool> isNotificationAccessGranted() =>
      PlatformChannelService.instance.isNotificationAccessGranted();

  Future<bool> isOverlayGranted() =>
      PlatformChannelService.instance.isOverlayPermissionGranted();

  Future<void> openNotificationAccessSettings() =>
      PlatformChannelService.instance.openNotificationAccessSettings();

  Future<void> openOverlaySettings() =>
      PlatformChannelService.instance.openOverlayPermissionSettings();

  Future<bool> requestPostNotifications() async {
    final status = await Permission.notification.request();
    return status.isGranted;
  }
}
