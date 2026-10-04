import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:picquery_app/src/utils/app_logger.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:share_plus/share_plus.dart';

class LogsPage extends StatefulWidget {
  const LogsPage({super.key});

  @override
  State<LogsPage> createState() => _LogsPageState();
}

class _LogsPageState extends State<LogsPage> {
  static final _logRecordPattern = RegExp(
    r'^\d{4}-\d{2}-\d{2}T\S+ \[([A-Z]+)\] ',
  );

  late Future<String> _logs = AppLogger.instance.readAllLogs();
  bool _isExporting = false;
  bool _isClearing = false;

  Future<void> _reload() async {
    _logs = AppLogger.instance.readAllLogs();
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _clearLogs() async {
    if (_isClearing) return;
    setState(() => _isClearing = true);
    try {
      await AppLogger.instance.clearLogs();
      if (mounted) _reload();
    } finally {
      if (mounted) setState(() => _isClearing = false);
    }
  }

  Future<void> _exportLogs() async {
    if (_isExporting) return;
    setState(() => _isExporting = true);
    try {
      final logger = AppLogger.instance;
      if (Platform.isAndroid || Platform.isIOS) {
        final files = await logger.getLogFiles();
        if (files.isEmpty) {
          if (mounted) Toast.showMessage(context.l10n.noLogs);
          return;
        }
        if (!mounted) return;
        final box = context.findRenderObject() as RenderBox?;
        await Share.shareXFiles(
          files.map((file) => XFile(file.path)).toList(),
          subject: context.l10n.logExportSubject,
          sharePositionOrigin: box == null
              ? null
              : box.localToGlobal(Offset.zero) & box.size,
        );
      } else {
        await logger.flush();
        final result = await OpenFilex.open(logger.logDirectory.path);
        if (result.type != ResultType.done && mounted) {
          Toast.showMessage(context.l10n.exportLogsFailed(result.message));
        }
      }
    } catch (error) {
      if (mounted) Toast.showMessage(context.l10n.exportLogsFailed(error));
    } finally {
      if (mounted) setState(() => _isExporting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.logs),
        actions: [
          IconButton(
            tooltip: context.l10n.clear,
            onPressed: _isClearing ? null : _clearLogs,
            icon: _isClearing
                ? SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 1,
                      color: Theme.of(context).colorScheme.error,
                    ),
                  )
                : Icon(
                    Icons.delete_outline,
                    color: Theme.of(context).colorScheme.error,
                  ),
          ),
          IconButton(
            tooltip: context.l10n.refresh,
            onPressed: _reload,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: context.l10n.exportLogs,
            onPressed: _isExporting ? null : _exportLogs,
            icon: _isExporting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 1),
                  )
                : const Icon(Icons.ios_share_outlined),
          ),
        ],
      ),
      body: FutureBuilder<String>(
        future: _logs,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Text(context.l10n.loadLogsFailed(snapshot.error!)),
            );
          }
          final logs = snapshot.data ?? '';
          if (logs.isEmpty) return Center(child: Text(context.l10n.noLogs));
          final entries = _parseLogEntries(logs);
          final isDark = Theme.of(context).brightness == Brightness.dark;
          final warningColor = isDark
              ? Colors.amberAccent
              : Colors.amber.shade800;
          final errorColor = isDark ? Colors.redAccent : Colors.red.shade700;
          return SelectionArea(
            child: ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: entries.length,
              itemBuilder: (context, index) {
                final entry = entries[index];
                final color = switch (entry.severity) {
                  _LogSeverity.warning => warningColor,
                  _LogSeverity.error => errorColor,
                  _LogSeverity.normal => null,
                };
                return Text(
                  entry.text,
                  style: TextStyle(
                    color: color,
                    fontFamily: _monospaceFontFamily,
                    fontFamilyFallback: _monospaceFontFallback,
                    fontSize: 14,
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  String get _monospaceFontFamily => switch (defaultTargetPlatform) {
    TargetPlatform.macOS || TargetPlatform.iOS => 'Menlo',
    TargetPlatform.windows => 'Consolas',
    TargetPlatform.linux => 'DejaVu Sans Mono',
    TargetPlatform.android => 'monospace',
    TargetPlatform.fuchsia => 'Roboto Mono',
  };

  List<String> get _monospaceFontFallback => switch (defaultTargetPlatform) {
    TargetPlatform.macOS ||
    TargetPlatform.iOS => const ['Monaco', 'Courier New'],
    TargetPlatform.windows => const ['Cascadia Mono', 'Courier New'],
    TargetPlatform.linux => const ['Liberation Mono', 'Noto Sans Mono'],
    TargetPlatform.android || TargetPlatform.fuchsia => const ['Roboto Mono'],
  };

  List<_LogEntry> _parseLogEntries(String logs) {
    final lines = logs.split('\n');
    final entries = <_LogEntry>[];
    var buffer = StringBuffer();
    var severity = _LogSeverity.normal;

    void addBufferedEntry() {
      if (buffer.isEmpty) return;
      entries.add(_LogEntry(buffer.toString(), severity));
      buffer = StringBuffer();
    }

    for (var index = 0; index < lines.length; index++) {
      final line = lines[index];
      final match = _logRecordPattern.firstMatch(line);
      if (match != null || line.startsWith('===== ')) {
        addBufferedEntry();
        severity = switch (match?.group(1)) {
          'WARN' || 'WARNING' => _LogSeverity.warning,
          'ERROR' || 'SEVERE' || 'SHOUT' => _LogSeverity.error,
          _ => _LogSeverity.normal,
        };
      }

      buffer.write(line);
    }

    addBufferedEntry();
    return entries;
  }
}

enum _LogSeverity { normal, warning, error }

class _LogEntry {
  const _LogEntry(this.text, this.severity);

  final String text;
  final _LogSeverity severity;
}
