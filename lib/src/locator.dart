import 'package:get_it/get_it.dart';
import 'package:picquery_app/src/managers/theme_manager.dart';
import 'package:picquery_app/src/managers/album_manager.dart';
import 'package:picquery_app/src/managers/indexing_manager.dart';
import 'package:picquery_app/src/managers/locale_manager.dart';
import 'package:picquery_app/src/managers/search_manager.dart';

void configureDependencies() {
  GetIt.I.registerLazySingleton<ThemeManager>(() => ThemeManager());
  GetIt.I.registerLazySingleton<LocaleManager>(() => LocaleManager());
  GetIt.I.registerLazySingleton<AlbumManager>(() => AlbumManager());
  GetIt.I.registerLazySingleton<IndexingManager>(
    () => IndexingManager(),
    dispose: (m) => m.dispose(),
  );
  GetIt.I.registerLazySingleton<SearchManager>(() => SearchManager());
}
