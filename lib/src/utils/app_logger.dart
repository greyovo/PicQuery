import 'dart:async';
import 'dart:developer' as developer;
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';
import 'package:path_provider/path_provider.dart';

/// Captures only records emitted through [Logger] and persists them by day.
class AppLogger with WidgetsBindingObserver {
  AppLogger._();

  static final AppLogger instance = AppLogger._();
  static const _maximumLogFiles = 7;
  static const _flushThreshold = 100;
  static const _flushInterval = Duration(seconds: 10);
  static final _logFilePattern = RegExp(r'^picquery-(\d{4}-\d{2}-\d{2})\.log$');

  final List<_BufferedLogRecord> _buffer = [];
  Future<void> _pendingWrite = Future.value();
  late final Directory _logDirectory;

  Directory get logDirectory => _logDirectory;

  Future<void> initialize() async {
    final appDirectory = await getApplicationDocumentsDirectory();
    _logDirectory = Directory(
      '${appDirectory.path}${Platform.pathSeparator}logs',
    );
    await _logDirectory.create(recursive: true);
    await _removeOldLogFiles();

    Logger.root.level = Level.ALL;
    Logger.root.onRecord.listen(_handleRecord);
    Timer.periodic(_flushInterval, (_) => unawaited(flush()));
    WidgetsBinding.instance.addObserver(this);
  }

  void _handleRecord(LogRecord record) {
    developer.log(
      record.message,
      name: record.loggerName,
      level: record.level.value,
      time: record.time,
      sequenceNumber: record.sequenceNumber,
      error: record.error,
      stackTrace: record.stackTrace,
    );
    _buffer.add(_BufferedLogRecord(record.time.toLocal(), _format(record)));
    if (_buffer.length >= _flushThreshold) unawaited(flush());
  }

  String _format(LogRecord record) {
    final output = StringBuffer()
      ..write(record.time.toLocal().toIso8601String())
      ..write(' [${record.level.name}] ')
      ..write('${record.loggerName}: ${record.message}');
    if (record.error != null) output.write('\nError: ${record.error}');
    if (record.stackTrace != null) output.write('\n${record.stackTrace}');
    return output.toString();
  }

  Future<void> flush() {
    if (_buffer.isEmpty) return _pendingWrite;
    final records = List<_BufferedLogRecord>.of(_buffer);
    _buffer.clear();
    _pendingWrite = _pendingWrite.then((_) => _writeRecords(records));
    return _pendingWrite;
  }

  Future<void> _writeRecords(List<_BufferedLogRecord> records) async {
    final recordsByDay = <String, List<String>>{};
    for (final record in records) {
      recordsByDay
          .putIfAbsent(_dateKey(record.time), () => [])
          .add(record.text);
    }
    for (final entry in recordsByDay.entries) {
      final file = File(
        '${_logDirectory.path}${Platform.pathSeparator}picquery-${entry.key}.log',
      );
      await file.writeAsString(
        '${entry.value.join('\n')}\n',
        mode: FileMode.append,
        flush: true,
      );
    }
    await _removeOldLogFiles();
  }

  Future<List<File>> getLogFiles() async {
    await flush();
    final files = await _listLogFiles();
    files.sort((a, b) => b.path.compareTo(a.path));
    return files;
  }

  Future<String> readAllLogs() async {
    final files = await getLogFiles();
    if (files.isEmpty) return '';
    final sections = <String>[];
    for (final file in files) {
      sections.add(
        '===== ${file.uri.pathSegments.last} =====\n${await file.readAsString()}',
      );
    }
    return sections.join('\n');
  }

  Future<List<File>> _listLogFiles() async {
    if (!await _logDirectory.exists()) return [];
    return _logDirectory
        .list()
        .where(
          (entity) =>
              entity is File &&
              _logFilePattern.hasMatch(entity.uri.pathSegments.last),
        )
        .cast<File>()
        .toList();
  }

  Future<void> _removeOldLogFiles() async {
    final files = await _listLogFiles();
    files.sort((a, b) => b.path.compareTo(a.path));
    for (final file in files.skip(_maximumLogFiles)) {
      await file.delete();
    }
  }

  String _dateKey(DateTime time) =>
      '${time.year.toString().padLeft(4, '0')}-'
      '${time.month.toString().padLeft(2, '0')}-'
      '${time.day.toString().padLeft(2, '0')}';

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached ||
        state == AppLifecycleState.hidden) {
      unawaited(flush());
    }
  }
}

class _BufferedLogRecord {
  const _BufferedLogRecord(this.time, this.text);
  final DateTime time;
  final String text;
}

Future<void> configureLogging() => AppLogger.instance.initialize();
