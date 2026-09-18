enum PermissionKind { notificationAccess, overlay, sms }

class AppPermissionStatus {
  const AppPermissionStatus({required this.kind, required this.granted});

  final PermissionKind kind;
  final bool granted;

  AppPermissionStatus copyWith({bool? granted}) =>
      AppPermissionStatus(kind: kind, granted: granted ?? this.granted);
}
