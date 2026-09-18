import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

import '../models/otp_entry.dart';

/// Reads/writes the SAME on-disk SQLite file the native Kotlin side writes
/// to (android/.../SnapDb.kt, SnapDbContract). sqflite's `getDatabasesPath()`
/// resolves to the app's `/databases` directory on Android, which is
/// identical to `Context.getDatabasePath(name)`'s parent directory — so both
/// sides open the exact same file by using the same file name. This lets
/// SnapNotificationService / SnapSmsReceiver insert history rows natively
/// even when the Flutter engine is not running, while the History screen
/// here reads/searches that data directly with no platform channel needed.
class HistoryRepository {
  HistoryRepository._();

  static final HistoryRepository instance = HistoryRepository._();

  static const _dbName = 'snapotp_history.db';
  static const _table = 'otp_history';
  static const _retention = Duration(hours: 24);

  Database? _db;

  Future<Database> _open() async {
    if (_db != null) return _db!;
    final dir = await getDatabasesPath();
    final path = p.join(dir, _dbName);
    _db = await openDatabase(
      path,
      version: 1,
      onCreate: (db, version) async {
        await db.execute('''
          CREATE TABLE IF NOT EXISTS $_table (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sender TEXT NOT NULL,
            code TEXT NOT NULL,
            source TEXT NOT NULL,
            app_label TEXT,
            timestamp INTEGER NOT NULL
          )
        ''');
        await db.execute('CREATE INDEX IF NOT EXISTS idx_otp_history_ts ON $_table(timestamp)');
      },
    );
    return _db!;
  }

  Future<List<OtpEntry>> fetchRecent({String query = ''}) async {
    final db = await _open();
    await pruneOld();
    final cutoff = DateTime.now().subtract(_retention).millisecondsSinceEpoch;
    final rows = await db.query(
      _table,
      where: 'timestamp >= ?',
      whereArgs: [cutoff],
      orderBy: 'timestamp DESC',
    );
    final entries = rows.map(OtpEntry.fromMap).toList();
    if (query.trim().isEmpty) return entries;
    return entries.where((e) => e.matchesQuery(query.trim())).toList();
  }

  Future<void> pruneOld() async {
    final db = await _open();
    final cutoff = DateTime.now().subtract(_retention).millisecondsSinceEpoch;
    await db.delete(_table, where: 'timestamp < ?', whereArgs: [cutoff]);
  }

  Future<void> clearAll() async {
    final db = await _open();
    await db.delete(_table);
  }
}
