import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';
import 'app_localizations_zh.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'generated/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('en'),
    Locale('zh'),
    Locale.fromSubtags(languageCode: 'zh', scriptCode: 'Hant'),
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'PicQuery'**
  String get appTitle;

  /// No description provided for @search.
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get search;

  /// No description provided for @albums.
  ///
  /// In en, this message translates to:
  /// **'Albums'**
  String get albums;

  /// No description provided for @settings.
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get settings;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @remove.
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get remove;

  /// No description provided for @delete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get delete;

  /// No description provided for @update.
  ///
  /// In en, this message translates to:
  /// **'Update'**
  String get update;

  /// No description provided for @continueAction.
  ///
  /// In en, this message translates to:
  /// **'Continue'**
  String get continueAction;

  /// No description provided for @stop.
  ///
  /// In en, this message translates to:
  /// **'Stop'**
  String get stop;

  /// No description provided for @ok.
  ///
  /// In en, this message translates to:
  /// **'OK'**
  String get ok;

  /// No description provided for @addAlbum.
  ///
  /// In en, this message translates to:
  /// **'Add album'**
  String get addAlbum;

  /// No description provided for @checkUpdates.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get checkUpdates;

  /// No description provided for @checking.
  ///
  /// In en, this message translates to:
  /// **'Checking'**
  String get checking;

  /// No description provided for @updatePhotos.
  ///
  /// In en, this message translates to:
  /// **'Update {count} photos'**
  String updatePhotos(int count);

  /// No description provided for @indexingAlbumsSummary.
  ///
  /// In en, this message translates to:
  /// **'Building image index · {count} albums'**
  String indexingAlbumsSummary(int count);

  /// No description provided for @albumsPhotosSummary.
  ///
  /// In en, this message translates to:
  /// **'{albumCount} albums · {photoCount} photos'**
  String albumsPhotosSummary(int albumCount, int photoCount);

  /// No description provided for @incompletePhotoCount.
  ///
  /// In en, this message translates to:
  /// **'Incomplete · {current}/{total} photos'**
  String incompletePhotoCount(int current, int total);

  /// No description provided for @updatesPhotoCount.
  ///
  /// In en, this message translates to:
  /// **'Updates available · {count} photos'**
  String updatesPhotoCount(int count);

  /// No description provided for @photoCount.
  ///
  /// In en, this message translates to:
  /// **'{count} photos'**
  String photoCount(int count);

  /// No description provided for @buildPhotoLibrary.
  ///
  /// In en, this message translates to:
  /// **'Build your photo library'**
  String get buildPhotoLibrary;

  /// No description provided for @buildPhotoLibraryDescription.
  ///
  /// In en, this message translates to:
  /// **'Add local albums and PicQuery will index them privately on your device.'**
  String get buildPhotoLibraryDescription;

  /// No description provided for @addFirstAlbum.
  ///
  /// In en, this message translates to:
  /// **'Add your first album'**
  String get addFirstAlbum;

  /// No description provided for @desktopOnlyOpenAlbum.
  ///
  /// In en, this message translates to:
  /// **'Opening an album location is only supported on desktop'**
  String get desktopOnlyOpenAlbum;

  /// No description provided for @folderUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The folder does not exist or cannot be accessed'**
  String get folderUnavailable;

  /// No description provided for @openFolderFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not open folder: {error}'**
  String openFolderFailed(Object error);

  /// No description provided for @removeAlbumIndex.
  ///
  /// In en, this message translates to:
  /// **'Remove album index'**
  String get removeAlbumIndex;

  /// No description provided for @removeAlbumIndexMessage.
  ///
  /// In en, this message translates to:
  /// **'Remove this album index? Your original photos will not be deleted.'**
  String get removeAlbumIndexMessage;

  /// No description provided for @albumActions.
  ///
  /// In en, this message translates to:
  /// **'Album actions'**
  String get albumActions;

  /// No description provided for @cancelIndexing.
  ///
  /// In en, this message translates to:
  /// **'Cancel indexing'**
  String get cancelIndexing;

  /// No description provided for @continueIndexing.
  ///
  /// In en, this message translates to:
  /// **'Continue indexing'**
  String get continueIndexing;

  /// No description provided for @indexingPercent.
  ///
  /// In en, this message translates to:
  /// **'Indexing · {percent}%'**
  String indexingPercent(int percent);

  /// No description provided for @indexingProgress.
  ///
  /// In en, this message translates to:
  /// **'{current}/{total} ({speed} photos/sec)'**
  String indexingProgress(int current, int total, Object speed);

  /// No description provided for @indexUpToDate.
  ///
  /// In en, this message translates to:
  /// **'Album index is up to date'**
  String get indexUpToDate;

  /// No description provided for @checkUpdatesFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not check for updates: {error}'**
  String checkUpdatesFailed(Object error);

  /// No description provided for @incrementalUpdate.
  ///
  /// In en, this message translates to:
  /// **'Incremental update'**
  String get incrementalUpdate;

  /// No description provided for @alreadyIndexed.
  ///
  /// In en, this message translates to:
  /// **'Already indexed'**
  String get alreadyIndexed;

  /// No description provided for @alreadyIndexedMessage.
  ///
  /// In en, this message translates to:
  /// **'This album is already indexed. Would you like to update it?'**
  String get alreadyIndexedMessage;

  /// No description provided for @indexingError.
  ///
  /// In en, this message translates to:
  /// **'Indexing failed: {error}'**
  String indexingError(Object error);

  /// No description provided for @albumUnavailableReAdd.
  ///
  /// In en, this message translates to:
  /// **'This album cannot be accessed. Please add it again.'**
  String get albumUnavailableReAdd;

  /// No description provided for @dcimNotFound.
  ///
  /// In en, this message translates to:
  /// **'No accessible photos or DCIM album found'**
  String get dcimNotFound;

  /// No description provided for @cancelIndexingTitle.
  ///
  /// In en, this message translates to:
  /// **'Cancel indexing'**
  String get cancelIndexingTitle;

  /// No description provided for @cancelIndexingMessage.
  ///
  /// In en, this message translates to:
  /// **'Cancel the current indexing operation? Photos already indexed will be kept.'**
  String get cancelIndexingMessage;

  /// No description provided for @keepIndexing.
  ///
  /// In en, this message translates to:
  /// **'Keep indexing'**
  String get keepIndexing;

  /// No description provided for @indexDcimTitle.
  ///
  /// In en, this message translates to:
  /// **'Index the DCIM album?'**
  String get indexDcimTitle;

  /// No description provided for @indexDcimMessage.
  ///
  /// In en, this message translates to:
  /// **'For your first use, index the phone\'s DCIM album to search photos by content.'**
  String get indexDcimMessage;

  /// No description provided for @notNow.
  ///
  /// In en, this message translates to:
  /// **'Not now'**
  String get notNow;

  /// No description provided for @startIndexing.
  ///
  /// In en, this message translates to:
  /// **'Start indexing'**
  String get startIndexing;

  /// No description provided for @searchPhotosTitle.
  ///
  /// In en, this message translates to:
  /// **'Search your photo library'**
  String get searchPhotosTitle;

  /// No description provided for @searchPhotosTitleMobile.
  ///
  /// In en, this message translates to:
  /// **'Search your photos'**
  String get searchPhotosTitleMobile;

  /// No description provided for @searchPhotosDescription.
  ///
  /// In en, this message translates to:
  /// **'Find your memories with simple words or an image. All search happens privately on your device.'**
  String get searchPhotosDescription;

  /// No description provided for @searchPhotosDescriptionMobile.
  ///
  /// In en, this message translates to:
  /// **'Private, offline image search'**
  String get searchPhotosDescriptionMobile;

  /// No description provided for @searchPhotosHint.
  ///
  /// In en, this message translates to:
  /// **'Search photos'**
  String get searchPhotosHint;

  /// No description provided for @searchByImage.
  ///
  /// In en, this message translates to:
  /// **'Search by image'**
  String get searchByImage;

  /// No description provided for @recentSearches.
  ///
  /// In en, this message translates to:
  /// **'Recent searches'**
  String get recentSearches;

  /// No description provided for @allFolders.
  ///
  /// In en, this message translates to:
  /// **'All folders'**
  String get allFolders;

  /// No description provided for @resultCount.
  ///
  /// In en, this message translates to:
  /// **'{count} results'**
  String resultCount(int count);

  /// No description provided for @clearFilters.
  ///
  /// In en, this message translates to:
  /// **'Clear filters'**
  String get clearFilters;

  /// No description provided for @searchFailed.
  ///
  /// In en, this message translates to:
  /// **'Search failed: {error}'**
  String searchFailed(Object error);

  /// No description provided for @imageSearchFailed.
  ///
  /// In en, this message translates to:
  /// **'Image search failed: {error}'**
  String imageSearchFailed(Object error);

  /// No description provided for @indexingResultsWarning.
  ///
  /// In en, this message translates to:
  /// **'Indexing is in progress. Search results may be incomplete.'**
  String get indexingResultsWarning;

  /// No description provided for @noMatchesFound.
  ///
  /// In en, this message translates to:
  /// **'No matches found'**
  String get noMatchesFound;

  /// No description provided for @selectImageToSearch.
  ///
  /// In en, this message translates to:
  /// **'Select an image to search'**
  String get selectImageToSearch;

  /// No description provided for @searchScope.
  ///
  /// In en, this message translates to:
  /// **'Search scope'**
  String get searchScope;

  /// No description provided for @all.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get all;

  /// No description provided for @custom.
  ///
  /// In en, this message translates to:
  /// **'Custom'**
  String get custom;

  /// No description provided for @selectScope.
  ///
  /// In en, this message translates to:
  /// **'Select scope'**
  String get selectScope;

  /// No description provided for @selectAll.
  ///
  /// In en, this message translates to:
  /// **'Select all'**
  String get selectAll;

  /// No description provided for @filterFolders.
  ///
  /// In en, this message translates to:
  /// **'Filter folders…'**
  String get filterFolders;

  /// No description provided for @noFoldersFound.
  ///
  /// In en, this message translates to:
  /// **'No folders found'**
  String get noFoldersFound;

  /// No description provided for @selectFolderToIndex.
  ///
  /// In en, this message translates to:
  /// **'Select folder to index'**
  String get selectFolderToIndex;

  /// No description provided for @permissionDenied.
  ///
  /// In en, this message translates to:
  /// **'Permission denied'**
  String get permissionDenied;

  /// No description provided for @photoPermissionMessage.
  ///
  /// In en, this message translates to:
  /// **'Photo library access is required to select albums for indexing.'**
  String get photoPermissionMessage;

  /// No description provided for @noAlbumsFound.
  ///
  /// In en, this message translates to:
  /// **'No albums found'**
  String get noAlbumsFound;

  /// No description provided for @noAlbumsMessage.
  ///
  /// In en, this message translates to:
  /// **'No photo albums were found on your device.'**
  String get noAlbumsMessage;

  /// No description provided for @noImagesFound.
  ///
  /// In en, this message translates to:
  /// **'No images found'**
  String get noImagesFound;

  /// No description provided for @noImagesMessage.
  ///
  /// In en, this message translates to:
  /// **'No images were found in the selected album.'**
  String get noImagesMessage;

  /// No description provided for @selectAlbum.
  ///
  /// In en, this message translates to:
  /// **'Select album'**
  String get selectAlbum;

  /// No description provided for @previous.
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get previous;

  /// No description provided for @next.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get next;

  /// No description provided for @info.
  ///
  /// In en, this message translates to:
  /// **'Info'**
  String get info;

  /// No description provided for @share.
  ///
  /// In en, this message translates to:
  /// **'Share'**
  String get share;

  /// No description provided for @open.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get open;

  /// No description provided for @fileNotFound.
  ///
  /// In en, this message translates to:
  /// **'File not found'**
  String get fileNotFound;

  /// No description provided for @shareFailed.
  ///
  /// In en, this message translates to:
  /// **'Share failed: {error}'**
  String shareFailed(Object error);

  /// No description provided for @openFailed.
  ///
  /// In en, this message translates to:
  /// **'Open failed: {error}'**
  String openFailed(Object error);

  /// No description provided for @imageDetails.
  ///
  /// In en, this message translates to:
  /// **'Image details'**
  String get imageDetails;

  /// No description provided for @fileName.
  ///
  /// In en, this message translates to:
  /// **'File name'**
  String get fileName;

  /// No description provided for @filePath.
  ///
  /// In en, this message translates to:
  /// **'File path'**
  String get filePath;

  /// No description provided for @format.
  ///
  /// In en, this message translates to:
  /// **'Format'**
  String get format;

  /// No description provided for @dimensions.
  ///
  /// In en, this message translates to:
  /// **'Dimensions'**
  String get dimensions;

  /// No description provided for @fileSize.
  ///
  /// In en, this message translates to:
  /// **'File size'**
  String get fileSize;

  /// No description provided for @modified.
  ///
  /// In en, this message translates to:
  /// **'Modified'**
  String get modified;

  /// No description provided for @similarity.
  ///
  /// In en, this message translates to:
  /// **'Similarity'**
  String get similarity;

  /// No description provided for @unknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get unknown;

  /// No description provided for @appearance.
  ///
  /// In en, this message translates to:
  /// **'Appearance'**
  String get appearance;

  /// No description provided for @light.
  ///
  /// In en, this message translates to:
  /// **'Light'**
  String get light;

  /// No description provided for @dark.
  ///
  /// In en, this message translates to:
  /// **'Dark'**
  String get dark;

  /// No description provided for @followSystem.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get followSystem;

  /// No description provided for @language.
  ///
  /// In en, this message translates to:
  /// **'Language'**
  String get language;

  /// No description provided for @systemLanguage.
  ///
  /// In en, this message translates to:
  /// **'Follow system'**
  String get systemLanguage;

  /// No description provided for @simplifiedChinese.
  ///
  /// In en, this message translates to:
  /// **'简体中文'**
  String get simplifiedChinese;

  /// No description provided for @traditionalChinese.
  ///
  /// In en, this message translates to:
  /// **'繁體中文'**
  String get traditionalChinese;

  /// No description provided for @english.
  ///
  /// In en, this message translates to:
  /// **'English'**
  String get english;

  /// No description provided for @indexUpdates.
  ///
  /// In en, this message translates to:
  /// **'Index updates'**
  String get indexUpdates;

  /// No description provided for @autoUpdateOnStartup.
  ///
  /// In en, this message translates to:
  /// **'Update index on startup'**
  String get autoUpdateOnStartup;

  /// No description provided for @autoUpdateOnStartupDescription.
  ///
  /// In en, this message translates to:
  /// **'Automatically update indexed albums when photo changes are detected'**
  String get autoUpdateOnStartupDescription;

  /// No description provided for @model.
  ///
  /// In en, this message translates to:
  /// **'Model'**
  String get model;

  /// No description provided for @dataManagement.
  ///
  /// In en, this message translates to:
  /// **'Data management'**
  String get dataManagement;

  /// No description provided for @clearAllIndexes.
  ///
  /// In en, this message translates to:
  /// **'Clear all indexes'**
  String get clearAllIndexes;

  /// No description provided for @clearRecentSearches.
  ///
  /// In en, this message translates to:
  /// **'Clear recent searches'**
  String get clearRecentSearches;

  /// No description provided for @deleteAllIndexesTitle.
  ///
  /// In en, this message translates to:
  /// **'Delete all indexes'**
  String get deleteAllIndexesTitle;

  /// No description provided for @deleteAllIndexesMessage.
  ///
  /// In en, this message translates to:
  /// **'Delete all index data? This action cannot be undone.'**
  String get deleteAllIndexesMessage;

  /// No description provided for @clearRecentSearchesTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear recent searches'**
  String get clearRecentSearchesTitle;

  /// No description provided for @clearRecentSearchesMessage.
  ///
  /// In en, this message translates to:
  /// **'Clear all recent search history?'**
  String get clearRecentSearchesMessage;

  /// No description provided for @clear.
  ///
  /// In en, this message translates to:
  /// **'Clear'**
  String get clear;

  /// No description provided for @recentSearchesCleared.
  ///
  /// In en, this message translates to:
  /// **'Recent searches cleared'**
  String get recentSearchesCleared;

  /// No description provided for @reloadModels.
  ///
  /// In en, this message translates to:
  /// **'Reload models'**
  String get reloadModels;

  /// No description provided for @modelsReloaded.
  ///
  /// In en, this message translates to:
  /// **'Models reloaded successfully'**
  String get modelsReloaded;

  /// No description provided for @modelsReloadFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not reload models: {error}'**
  String modelsReloadFailed(Object error);

  /// No description provided for @timeAny.
  ///
  /// In en, this message translates to:
  /// **'Any time'**
  String get timeAny;

  /// No description provided for @timeDay.
  ///
  /// In en, this message translates to:
  /// **'Past day'**
  String get timeDay;

  /// No description provided for @timeWeek.
  ///
  /// In en, this message translates to:
  /// **'Past week'**
  String get timeWeek;

  /// No description provided for @timeMonth.
  ///
  /// In en, this message translates to:
  /// **'Past month'**
  String get timeMonth;

  /// No description provided for @timeYear.
  ///
  /// In en, this message translates to:
  /// **'Past year'**
  String get timeYear;

  /// No description provided for @addAlbumWhileIndexing.
  ///
  /// In en, this message translates to:
  /// **'Please wait until indexing finishes before adding another album'**
  String get addAlbumWhileIndexing;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en', 'zh'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when language+script codes are specified.
  switch (locale.languageCode) {
    case 'zh':
      {
        switch (locale.scriptCode) {
          case 'Hant':
            return AppLocalizationsZhHant();
        }
        break;
      }
  }

  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
    case 'zh':
      return AppLocalizationsZh();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}
