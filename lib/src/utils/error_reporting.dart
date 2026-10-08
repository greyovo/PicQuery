import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:logging/logging.dart';
import 'package:http/http.dart' as http;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/utils/app_logger.dart';
import 'package:picquery_app/src/utils/log_redactor.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

enum ReportResult { submitted, unavailable, failed }

/// Automatic collection requires opt-in. Manual reports use an isolated Dart
/// hub so clicking Report never enables background/native crash collection.
class ErrorReporting {
  static const _dsn = String.fromEnvironment('SENTRY_DSN');
  static const _environment = String.fromEnvironment(
    'SENTRY_ENVIRONMENT',
    defaultValue: 'production',
  );
  static const _maxLogBytes = 256 * 1024;
  static final automaticReporting = ValueNotifier(false);
  static StreamSubscription<LogRecord>? _subscription;
  static bool _active = false;
  static String? _release;
  static Future<void> _pendingConfiguration = Future.value();
  @visibleForTesting
  static Hub Function(SentryOptions) createManualHub = Hub.new;

  static Future<void> initialize() async {
    try {
      final info = await PackageInfo.fromPlatform();
      _release = 'picquery@${info.version}+${info.buildNumber}';
    } catch (_) {
      // Error collection still works if package metadata is unavailable.
    }
    automaticReporting.value = SettingsStore.getAutomaticErrorReporting();
    await _configure();
    _subscription ??= Logger.root.onRecord.listen(_onRecord);
  }

  static Future<void> setAutomaticReporting(bool enabled) async {
    await SettingsStore.setAutomaticErrorReporting(enabled);
    automaticReporting.value = enabled;
    _pendingConfiguration = _pendingConfiguration.then((_) => _configure());
    await _pendingConfiguration;
  }

  static Future<void> _configure() async {
    if (!automaticReporting.value || _dsn.isEmpty) {
      if (_active) await Sentry.close();
      _active = false;
      return;
    }
    if (_active) return;
    try {
      await SentryFlutter.init((options) {
        configureOptions(options);
        options.enableAutoNativeBreadcrumbs = false;
        options.enableAutoPerformanceTracing = false;
        options.enableAutoSessionTracking = false;
        options.attachScreenshot = false;
        options.reportSilentFlutterErrors = true;
        // View hierarchy collection is disabled by default.
      });
      _active = true;
    } catch (_) {
      // Monitoring must not prevent the offline app from starting.
      _active = false;
    }
  }

  @visibleForTesting
  static void configureOptions(SentryOptions options) {
    options.dsn = _dsn;
    options.environment = _environment;
    options.release = _release;
    options.sendDefaultPii = false;
    options.sendClientReports = false;
    options.beforeSend = prepareEvent;
    options.beforeBreadcrumb = (breadcrumb, hint) => breadcrumb == null
        ? null
        : Breadcrumb.fromJson(
            Map<String, dynamic>.from(
              LogRedactor.redactJson(breadcrumb.toJson()),
            ),
          );
  }

  @visibleForTesting
  static Future<SentryEvent?> prepareEvent(SentryEvent event, Hint hint) async {
    // Strip identity/request fields and recursively redact exception values,
    // source frames, breadcrumbs and contexts before Dart events leave the app.
    final data =
        Map<String, dynamic>.from(LogRedactor.redactJson(event.toJson()))
          ..remove('user')
          ..remove('request')
          ..remove('server_name');
    try {
      final files = await AppLogger.instance.getLogFiles();
      if (files.isNotEmpty) {
        final file = await files.first.open();
        late String logs;
        try {
          final length = await file.length();
          final truncated = length > _maxLogBytes;
          await file.setPosition(truncated ? length - _maxLogBytes : 0);
          final bytes = await file.read(_maxLogBytes);
          // Drop a partial first line so truncation cannot leave the tail of
          // a sensitive path without the prefix needed by the redactor.
          final firstNewline = truncated ? bytes.indexOf(10) : -1;
          logs = LogRedactor.redact(
            utf8.decode(
              truncated
                  ? (firstNewline < 0
                        ? <int>[]
                        : bytes.sublist(firstNewline + 1))
                  : bytes,
              allowMalformed: true,
            ),
          );
        } finally {
          await file.close();
        }
        hint.attachments.add(
          SentryAttachment.fromIntList(
            _boundedLogBytes(logs),
            'picquery-diagnostics.log',
            contentType: 'text/plain',
          ),
        );
      }
    } catch (_) {
      // A disk/logging failure must not suppress the original exception.
    }
    return SentryEvent.fromJson(data);
  }

  static List<int> _boundedLogBytes(String logs) {
    final bytes = utf8.encode(logs);
    if (bytes.length <= _maxLogBytes) return bytes;
    final newline = bytes.indexOf(10, bytes.length - _maxLogBytes);
    return newline < 0 ? [] : bytes.sublist(newline + 1);
  }

  static void _onRecord(LogRecord record) {
    if (!_active || !automaticReporting.value) return;
    // The Flutter SDK already captures these errors through its own handlers.
    if (record.loggerName == 'main' &&
        (record.message == 'Unhandled Flutter framework error.' ||
            record.message == 'Unhandled platform error.')) {
      return;
    }
    if (record.error != null || record.level >= Level.SEVERE) {
      unawaited(_captureRecord(record));
    } else {
      unawaited(
        Sentry.addBreadcrumb(
          Breadcrumb(
            message: LogRedactor.redact(record.message),
            category: record.loggerName,
            level: record.level >= Level.WARNING
                ? SentryLevel.warning
                : SentryLevel.info,
          ),
        ),
      );
    }
  }

  static Future<void> _captureRecord(LogRecord record) async {
    try {
      await Sentry.captureEvent(
        SentryEvent(
          logger: record.loggerName,
          message: SentryMessage(LogRedactor.redact(record.message)),
          throwable: record.error,
          level: SentryLevel.error,
        ),
        stackTrace: record.stackTrace,
      );
    } catch (_) {
      // Do not log a transport error here: it would recursively report itself.
    }
  }

  static Future<ReportResult> reportProblem({
    required String source,
    Object? error,
    Map<String, dynamic> diagnostics = const {},
  }) async {
    if (_dsn.isEmpty) return ReportResult.unavailable;
    // No raw search text, image content, album names or paths in diagnostics.
    final options = SentryOptions();
    configureOptions(options);
    options.httpClient = http.Client();
    final hub = createManualHub(options);
    try {
      final id = await hub
          .captureEvent(
            SentryEvent(
              message: SentryMessage('User reported a problem: $source'),
              throwable: error,
              level: SentryLevel.warning,
              tags: {'source': source, 'user_report': 'true'},
              contexts: Contexts.fromJson({
                'diagnostics': {
                  ...diagnostics,
                  'platform': Platform.operatingSystem,
                },
              }),
            ),
          )
          .timeout(const Duration(seconds: 15));
      return id == SentryId.empty()
          ? ReportResult.failed
          : ReportResult.submitted;
    } catch (_) {
      return ReportResult.failed;
    } finally {
      await hub.close();
    }
  }
}
