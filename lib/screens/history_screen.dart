import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../models/otp_entry.dart';
import '../services/history_repository.dart';

class HistoryScreen extends StatefulWidget {
  const HistoryScreen({super.key});

  @override
  State<HistoryScreen> createState() => _HistoryScreenState();
}

class _HistoryScreenState extends State<HistoryScreen> {
  final _searchController = TextEditingController();
  List<OtpEntry> _entries = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final entries = await HistoryRepository.instance.fetchRecent(
      query: _searchController.text,
    );
    if (!mounted) return;
    setState(() {
      _entries = entries;
      _loading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('History'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: UiSearchField(
              controller: _searchController,
              hint: 'Search by sender or code',
              onChanged: (_) => _load(),
              onFilter: _load,
            ),
          ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _entries.isEmpty
                ? const UiEmptyState(
                    title: 'No codes captured yet',
                    message:
                        'Codes detected in the last 24 hours will appear here',
                    icon: Icons.inbox_outlined,
                  )
                : UiRefresh(
                    onRefresh: _load,
                    child: ListView.separated(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: _entries.length,
                      separatorBuilder: (_, _) => const SizedBox(height: 8),
                      itemBuilder: (context, index) {
                        final entry = _entries[index];
                        return UiCard(
                          padding: EdgeInsets.zero,
                          child: UiListTile(
                            entry.code,
                            leading: Icon(
                              entry.source == 'sms'
                                  ? Icons.sms_outlined
                                  : Icons.notifications_outlined,
                            ),
                            subtitle:
                                '${entry.sender} · ${_formatTime(entry.timestamp)}',
                            trailing: UiCopyButton(
                              entry.code,
                              onCopied: () {
                                if (context.mounted) {
                                  context.showSnack('Code copied');
                                }
                              },
                            ),
                          ),
                        );
                      },
                    ),
                  ),
          ),
        ],
      ),
    );
  }

  String _formatTime(DateTime dt) {
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inMinutes < 1) return 'just now';
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
