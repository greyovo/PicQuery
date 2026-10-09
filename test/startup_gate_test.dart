import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/utils/app_startup.dart';
import 'package:picquery_app/src/widgets/startup_gate.dart';

Widget app(AppInitializer initialize) => MaterialApp(
  locale: const Locale('en'),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: StartupGate(initialize: initialize, child: const Text('App ready')),
);

void main() {
  testWidgets('paints preparation UI before initializing and gates the app', (
    tester,
  ) async {
    final completion = Completer<void>();
    late ValueChanged<StartupProgress> report;
    await tester.pumpWidget(
      app((onProgress) {
        // The initializer must run after the loading UI has been built.
        expect(find.text('Preparing PicQuery'), findsOneWidget);
        expect(find.text('App ready'), findsNothing);
        report = onProgress;
        return completion.future;
      }),
    );
    report(
      const StartupProgress(StartupStage.clipAssets, completed: 1, total: 2),
    );
    await tester.pump();
    expect(find.text('Preparing image search resources…'), findsOneWidget);
    expect(find.text('1 / 2'), findsOneWidget);
    expect(find.text('App ready'), findsNothing);
    completion.complete();
    await tester.pump();
    expect(find.text('App ready'), findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsNothing);
  });

  testWidgets('shows errors and retries without starting duplicate work', (
    tester,
  ) async {
    var attempts = 0;
    final completion = Completer<void>();
    await tester.pumpWidget(
      app((onProgress) async {
        attempts++;
        if (attempts == 1) throw StateError('Disk full');
        await completion.future;
      }),
    );
    await tester.pump();
    expect(find.text('Preparation failed'), findsOneWidget);
    expect(find.text('App ready'), findsNothing);
    await tester.tap(find.text('Retry'));
    await tester.pump();
    expect(attempts, 2);
    expect(find.text('Retry'), findsNothing);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    completion.complete();
    await tester.pump();
    expect(find.text('App ready'), findsOneWidget);
  });

  testWidgets('ignores progress and completion after disposal', (tester) async {
    final completion = Completer<void>();
    late ValueChanged<StartupProgress> report;
    await tester.pumpWidget(
      app((onProgress) {
        report = onProgress;
        return completion.future;
      }),
    );
    await tester.pumpWidget(const SizedBox());
    report(const StartupProgress(StartupStage.translationLoading));
    completion.complete();
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
