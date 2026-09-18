/// A single captured OTP history row, mirroring the shared SQLite schema
/// defined natively in SnapDbContract (android/.../SnapDb.kt).
class OtpEntry {
  const OtpEntry({
    required this.id,
    required this.sender,
    required this.code,
    required this.source,
    required this.appLabel,
    required this.timestamp,
  });

  final int id;
  final String sender;
  final String code;
  final String source; // "sms" | "notification"
  final String? appLabel;
  final DateTime timestamp;

  factory OtpEntry.fromMap(Map<String, Object?> map) => OtpEntry(
    id: map['id'] as int,
    sender: map['sender'] as String,
    code: map['code'] as String,
    source: map['source'] as String,
    appLabel: map['app_label'] as String?,
    timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] as int),
  );

  bool matchesQuery(String query) {
    if (query.isEmpty) return true;
    final q = query.toLowerCase();
    return sender.toLowerCase().contains(q) || code.toLowerCase().contains(q);
  }
}
