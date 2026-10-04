import 'dart:io';

import 'package:desktop_drop/desktop_drop.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/utils/path_selector.dart';
import 'package:picquery_app/l10n/generated/app_localizations.dart';
import 'package:picquery_app/src/widgets/desktop_album_drop_target.dart';

void main() {
  late Directory folder;
  late List<PathSelectionResult> selections;
  final navigatorKey = GlobalKey<NavigatorState>();

  setUp(() async {
    folder = await Directory.systemTemp.createTemp('picquery-drop-');
    final nested = Directory('${folder.path}/nested')..createSync();
    File('${nested.path}/photo.PNG').writeAsStringSync('image');
    selections = [];
  });
  tearDown(() async => folder.delete(recursive: true));

  Future<void> mount(WidgetTester tester) async {
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigatorKey,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: DesktopAlbumDropTarget(
          onFolderDropped: (selection) async => selections.add(selection),
          child: const Scaffold(),
        ),
      ),
    );
  }

  Future<void> drop(WidgetTester tester, List<String> paths) async {
    final target = tester.widget<DropTarget>(find.byType(DropTarget));
    await tester.runAsync(() async {
      target.onDragDone!(
        DropDoneDetails(
          files: paths.map(DropItemFile.new).toList(),
          localPosition: Offset.zero,
          globalPosition: Offset.zero,
        ),
      );
      await Future<void>.delayed(const Duration(milliseconds: 200));
    });
    await tester.pump();
  }

  testWidgets('accepts a single directory with its path and name', (
    tester,
  ) async {
    await mount(tester);
    await drop(tester, [folder.path]);
    expect(selections.single.path, folder.path);
    expect(
      selections.single.displayName,
      folder.uri.pathSegments.where((s) => s.isNotEmpty).last,
    );
    expect(selections.single.imagePaths, isNull);
  });

  testWidgets('shows a dialog for files and missing paths', (tester) async {
    final file = File('${folder.path}/photo.png')..writeAsStringSync('image');
    await mount(tester);
    for (final path in [file.path, '${folder.path}/missing']) {
      await drop(tester, [path]);
      await tester.pumpAndSettle();
      expect(find.text('Please drop a folder'), findsOneWidget);
      expect(selections, isEmpty);
      await tester.tap(find.text('OK'));
      await tester.pumpAndSettle();
      await tester.runAsync(() async {
        await Future<void>.delayed(Duration.zero);
      });
    }
  });

  testWidgets('shows a dialog for folders with no supported images', (
    tester,
  ) async {
    Directory('${folder.path}/nested').deleteSync(recursive: true);
    File('${folder.path}/notes.txt').writeAsStringSync('text');
    await mount(tester);
    await drop(tester, [folder.path]);
    await tester.pumpAndSettle();
    expect(find.text('No images found'), findsOneWidget);
    expect(selections, isEmpty);
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
  });

  testWidgets('ignores multiple items', (tester) async {
    await mount(tester);
    await drop(tester, [folder.path, '${folder.path}/nested']);
    expect(selections, isEmpty);
    expect(find.byType(AlertDialog), findsNothing);
  });

  testWidgets(
    'disables drops on pushed routes and dialogs, then restores them',
    (tester) async {
      await mount(tester);
      navigatorKey.currentState!.push(
        MaterialPageRoute<void>(builder: (_) => const Scaffold()),
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<DropTarget>(find.byType(DropTarget, skipOffstage: false))
            .enable,
        isFalse,
      );
      navigatorKey.currentState!.pop();
      await tester.pumpAndSettle();
      expect(tester.widget<DropTarget>(find.byType(DropTarget)).enable, isTrue);
      showDialog<void>(
        context: navigatorKey.currentContext!,
        builder: (_) => const AlertDialog(),
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<DropTarget>(find.byType(DropTarget)).enable,
        isFalse,
      );
      await drop(tester, [folder.path]);
      expect(selections, isEmpty);
      navigatorKey.currentState!.pop();
      await tester.pumpAndSettle();
      await drop(tester, [folder.path]);
      expect(selections, hasLength(1));
    },
  );
}
