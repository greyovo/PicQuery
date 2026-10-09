import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/models_config.dart';

enum StartupStage {
  database,
  clipAssets,
  clipLoading,
  translationAssets,
  translationLoading,
}

class StartupProgress {
  const StartupProgress(this.stage, {this.completed = 0, this.total = 0});

  final StartupStage stage;
  final int completed;
  final int total;
}

typedef AppInitializer = Future<void> Function(
  ValueChanged<StartupProgress> onProgress,
);

/// Sequential preparation limits peak memory and prevents searches or automatic
/// indexing from racing model initialization.
Future<void> initializeApp(ValueChanged<StartupProgress> onProgress) async {
  onProgress(const StartupProgress(StartupStage.database));
  final appDir = await getApplicationDocumentsDirectory();
  await initDb(dbPath: '${appDir.path}/picquery_v2.db');
  await initClipModels(
    onProgress: (completed, total) => onProgress(
      StartupProgress(
        StartupStage.clipAssets,
        completed: completed,
        total: total,
      ),
    ),
    onLoading: () =>
        onProgress(const StartupProgress(StartupStage.clipLoading)),
  );
  await initTranslationModel(
    onProgress: (completed, total) => onProgress(
      StartupProgress(
        StartupStage.translationAssets,
        completed: completed,
        total: total,
      ),
    ),
    onLoading: () =>
        onProgress(const StartupProgress(StartupStage.translationLoading)),
  );
}
