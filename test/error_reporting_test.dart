import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/utils/app_logger.dart';
import 'package:picquery_app/src/utils/error_reporting.dart';
import 'package:picquery_app/src/widgets/privacy_agreement_gate.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late Box settings;
  const configured = String.fromEnvironment('SENTRY_DSN') != '';

  setUpAll(() async {
    directory = await Directory.systemTemp.createTemp('picquery-diagnostics-');
    Hive.init(directory.path);
    settings = await Hive.openBox('settings');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.flutter.io/path_provider'),
          (_) async => directory.path,
        );
    await AppLogger.instance.initialize();
  });

  setUp(() async {
    await settings.clear();
    ErrorReporting.automaticReporting.value = false;
    ErrorReporting.createManualHub = Hub.new;
    await AppLogger.instance.clearLogs();
  });

  tearDownAll(() async {
    await Hive.close();
    await directory.delete(recursive: true);
  });

  test('legacy acceptance never implicitly enables diagnostics', () async {
    await settings.put('privacy_agreement_accepted', true);
    await settings.put('automatic_error_reporting', true);
    expect(SettingsStore.getPrivacyAgreementAccepted(), isFalse);
    expect(SettingsStore.getAutomaticErrorReporting(), isFalse);
    await SettingsStore.acceptPrivacyAgreement();
    await SettingsStore.setAutomaticErrorReporting(false);
    expect(SettingsStore.getPrivacyAgreementAccepted(), isTrue);
    expect(SettingsStore.getAutomaticErrorReporting(), isFalse);
  });

  testWidgets(
    'declining diagnostics continues into the app and saves opt-out',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const PrivacyAgreementGate(child: Text('Ready to search')),
        ),
      );
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await tester.tap(find.text('Continue without reporting'));
        await Future<void>.delayed(const Duration(milliseconds: 100));
      });
      await tester.pumpAndSettle();
      expect(find.text('Ready to search'), findsOneWidget);
      expect(SettingsStore.getPrivacyAgreementAccepted(), isTrue);
      expect(SettingsStore.getAutomaticErrorReporting(), isFalse);
      expect(ErrorReporting.automaticReporting.value, isFalse);
    },
  );

  test(
    'event filtering removes identities, paths and attaches flushed logs',
    () async {
      Logger('test').severe(
        'Cannot open /Users/alice/Pictures/private.jpg',
        const FileSystemException(
          'read failed',
          '/Users/alice/Pictures/private.jpg',
        ),
      );
      final hint = Hint();
      final event = await ErrorReporting.prepareEvent(
        SentryEvent.fromJson({
          ...SentryEvent().toJson(),
          'message': {'formatted': 'Failed to read /Users/alice/private.jpg'},
          'user': {'email': 'alice@example.com', 'ip_address': '127.0.0.1'},
          'request': {'url': 'https://example.com/private'},
          'server_name': 'alice-laptop',
          'exception': {
            'values': [
              {
                'type': 'FileSystemException',
                'value': 'path="C:\\Users\\alice\\Pictures\\private.jpg"',
                'stacktrace': {
                  'frames': [
                    {
                      'abs_path': '/Users/alice/project/lib/main.dart',
                      'filename': 'package:picquery_app/main.dart',
                      'lineno': 42,
                    },
                  ],
                },
              },
            ],
          },
        }),
        hint,
      );
      final data = event!.toJson();
      expect(data.containsKey('user'), isFalse);
      expect(data.containsKey('request'), isFalse);
      expect(data.containsKey('server_name'), isFalse);
      expect(data.toString(), isNot(contains('alice')));
      expect(data.toString(), contains('package:picquery_app/main.dart'));
      expect(hint.attachments, hasLength(1));
      final logs = utf8.decode(await hint.attachments.single.bytes);
      expect(logs, contains('read failed'));
      expect(logs, isNot(contains('alice')));
      expect(logs, contains('[redacted path]'));
    },
  );

  test('attachment truncation cannot leak a partial path at the byte boundary', () async {
    Logger('test').info('start');
    final file = (await AppLogger.instance.getLogFiles()).first;
    await file.writeAsString(
      '${'x' * (256 * 1024)}/Users/alice/secret folder/image.jpg\nlatest diagnostic\n',
    );
    final hint = Hint();
    await ErrorReporting.prepareEvent(SentryEvent(), hint);
    final bytes = await hint.attachments.single.bytes;
    expect(bytes.length, lessThanOrEqualTo(256 * 1024));
    final logs = utf8.decode(bytes);
    expect(logs, isNot(contains('alice')));
    expect(logs, contains('latest diagnostic'));
  });

  test(
    'attachment remains bounded after short paths expand during redaction',
    () async {
      Logger('test').info('start');
      final file = (await AppLogger.instance.getLogFiles()).first;
      await file.writeAsString('${'/a\n' * 100000}latest diagnostic\n');
      final hint = Hint();
      await ErrorReporting.prepareEvent(SentryEvent(), hint);
      final bytes = await hint.attachments.single.bytes;
      expect(bytes.length, lessThanOrEqualTo(256 * 1024));
      expect(utf8.decode(bytes), contains('latest diagnostic'));
    },
  );

  test(
    'without DSN reports are unavailable and never enable collection',
    () async {
      expect(
        await ErrorReporting.reportProblem(source: 'empty_search'),
        ReportResult.unavailable,
      );
      expect(Sentry.isEnabled, isFalse);
      expect(ErrorReporting.automaticReporting.value, isFalse);
    },
    skip: configured,
  );

  test(
    'manual report sends an attachment without enabling automatic SDK',
    () async {
      Logger('test').info('indexing count=10');
      String? body;
      ErrorReporting.createManualHub = (options) {
        options.httpClient.close();
        options.httpClient = MockClient((request) async {
          List<int> bytes = request.bodyBytes;
          if (request.headers['content-encoding'] == 'gzip') {
            bytes = gzip.decode(bytes);
          }
          body = utf8.decode(bytes);
          return http.Response(
            '{"id":"0123456789abcdef0123456789abcdef"}',
            200,
          );
        });
        return Hub(options);
      };
      final originalHub = Sentry.currentHub;
      expect(
        await ErrorReporting.reportProblem(
          source: 'empty_search',
          diagnostics: {'result_count': 0, 'search_mode': 'text'},
        ),
        ReportResult.submitted,
      );
      expect(body, contains('picquery-diagnostics.log'));
      expect(body, contains('indexing count=10'));
      expect(body, contains('result_count'));
      expect(identical(Sentry.currentHub, originalHub), isTrue);
      expect(Sentry.isEnabled, isFalse);
      expect(ErrorReporting.automaticReporting.value, isFalse);
    },
    skip: !configured,
  );

  test('manual report surfaces transport failure', () async {
    ErrorReporting.createManualHub = (options) {
      options.httpClient.close();
      options.httpClient = MockClient(
        (_) async => http.Response('unavailable', 503),
      );
      return Hub(options);
    };
    expect(
      await ErrorReporting.reportProblem(source: 'indexing_error'),
      ReportResult.failed,
    );
  }, skip: !configured);
}
