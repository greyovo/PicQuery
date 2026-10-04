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
  // Keep this as a getter so hot reload cannot retain an older RegExp whose
  // capture-group layout no longer matches the parser below.
  static RegExp get _logRecordPattern =>
      RegExp(r'^(\d{4}-\d{2}-\d{2}T\S+) \[([A-Z]+)\] (.*)$');

  late Future<String> _logs = AppLogger.instance.readAllLogs();
  final _horizontalScrollController = ScrollController();
  bool _isExporting = false;
  bool _isClearing = false;

  @override
  void dispose() {
    _horizontalScrollController.dispose();
    super.dispose();
  }

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
        await SharePlus.instance.share(
          ShareParams(
            files: files.map((file) => XFile(file.path)).toList(),
            subject: context.l10n.logExportSubject,
            sharePositionOrigin: box == null
                ? null
                : box.localToGlobal(Offset.zero) & box.size,
          ),
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
          return LayoutBuilder(
            builder: (context, constraints) {
              final contentWidth = _estimateContentWidth(
                context,
                entries,
              ).clamp(constraints.maxWidth, double.infinity).toDouble();
              return Scrollbar(
                controller: _horizontalScrollController,
                scrollbarOrientation: ScrollbarOrientation.bottom,
                child: SingleChildScrollView(
                  controller: _horizontalScrollController,
                  scrollDirection: Axis.horizontal,
                  child: SizedBox(
                    width: contentWidth,
                    height: constraints.maxHeight,
                    child: SelectionArea(
                      child: ListView.builder(
                        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
                        itemCount: entries.length,
                        itemBuilder: (context, index) {
                          final entry = entries[index];
                          return _LogEntryRow(
                            entry: entry,
                            color: switch (entry.severity) {
                              _LogSeverity.warning => warningColor,
                              _LogSeverity.error => errorColor,
                              _LogSeverity.normal => Theme.of(
                                context,
                              ).colorScheme.onSurface,
                            },
                            fontFamily: _monospaceFontFamily,
                            fontFamilyFallback: _monospaceFontFallback,
                          );
                        },
                      ),
                    ),
                  ),
                ),
              );
            },
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

  double _estimateContentWidth(BuildContext context, List<_LogEntry> entries) {
    var widestColumns = 0;
    for (final entry in entries) {
      final prefixColumns = entry.timestamp == null ? 0 : 27;
      final levelColumns = entry.level == null ? 0 : 5;
      for (final line in entry.message.split('\n')) {
        final columns = prefixColumns + levelColumns + _visualColumns(line);
        if (columns > widestColumns) widestColumns = columns;
      }
    }
    final characterWidth = MediaQuery.textScalerOf(context).scale(13) * 0.62;
    return widestColumns * characterWidth + 32;
  }

  int _visualColumns(String text) {
    var columns = 0;
    for (final rune in text.runes) {
      columns += switch (rune) {
        0x09 => 4,
        <= 0x7f => 1,
        _ => 2,
      };
    }
    return columns;
  }

  List<_LogEntry> _parseLogEntries(String logs) {
    final lines = logs.split('\n');
    final entries = <_LogEntry>[];
    var buffer = StringBuffer();
    var severity = _LogSeverity.normal;
    String? timestamp;
    String? level;

    void addBufferedEntry() {
      if (buffer.isEmpty) return;
      entries.add(
        _LogEntry(
          timestamp: timestamp,
          level: level,
          message: buffer.toString(),
          severity: severity,
        ),
      );
      buffer = StringBuffer();
    }

    for (final line in lines) {
      final match = _logRecordPattern.firstMatch(line);
      if (match != null && match.groupCount >= 3) {
        addBufferedEntry();
        timestamp = match.group(1);
        level = match.group(2);
        severity = switch (level) {
          'W' || 'WARN' || 'WARNING' => _LogSeverity.warning,
          'E' || 'ERROR' || 'SEVERE' || 'SHOUT' => _LogSeverity.error,
          _ => _LogSeverity.normal,
        };
        buffer.write(match.group(3));
        continue;
      }
      if (line.startsWith('===== ')) {
        addBufferedEntry();
        timestamp = null;
        level = null;
        severity = _LogSeverity.normal;
        buffer.write(line);
        continue;
      }

      if (buffer.isNotEmpty) buffer.write('\n');
      buffer.write(line);
    }

    addBufferedEntry();
    return entries;
  }
}

enum _LogSeverity { normal, warning, error }

class _LogEntry {
  const _LogEntry({
    required this.timestamp,
    required this.level,
    required this.message,
    required this.severity,
  });

  final String? timestamp;
  final String? level;
  final String message;
  final _LogSeverity severity;
}

class _LogEntryRow extends StatelessWidget {
  const _LogEntryRow({
    required this.entry,
    required this.color,
    required this.fontFamily,
    required this.fontFamilyFallback,
  });

  final _LogEntry entry;
  final Color color;
  final String fontFamily;
  final List<String> fontFamilyFallback;

  @override
  Widget build(BuildContext context) {
    final textStyle = TextStyle(
      color: color,
      fontFamily: fontFamily,
      fontFamilyFallback: fontFamilyFallback,
      fontSize: 13,
      height: 1.55,
    );
    final timestamp = entry.timestamp;
    final level = entry.level;

    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(
            color: Theme.of(context).dividerColor.withValues(alpha: 0.35),
          ),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 7, horizontal: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (timestamp != null) ...[
              Text(
                timestamp.replaceFirst('T', ' '),
                softWrap: false,
                style: textStyle.copyWith(
                  color: Theme.of(context).colorScheme.onSurface
                      .withValues(alpha: 0.45),
                ),
              ),
              const SizedBox(width: 10),
            ],
            if (level != null) ...[
              Container(
                constraints: const BoxConstraints(minWidth: 24),
                padding: const EdgeInsets.symmetric(horizontal: 5),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(4),
                ),
                alignment: Alignment.center,
                child: Text(
                  _displayLevel(level),
                  softWrap: false,
                  style: textStyle.copyWith(
                    color: color,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const SizedBox(width: 10),
            ],
            Text(entry.message, softWrap: false, style: textStyle),
          ],
        ),
      ),
    );
  }

  String _displayLevel(String level) => switch (level) {
    'INFO' => 'I',
    'WARN' || 'WARNING' => 'W',
    'ERROR' || 'SEVERE' || 'SHOUT' => 'E',
    _ => level,
  };
}
