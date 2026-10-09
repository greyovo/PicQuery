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

  /// No description provided for @pausedPhotoCount.
  ///
  /// In en, this message translates to:
  /// **'Paused · {current}/{total} photos'**
  String pausedPhotoCount(int current, int total);

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

  /// No description provided for @currentVersion.
  ///
  /// In en, this message translates to:
  /// **'Version'**
  String get currentVersion;

  /// No description provided for @buildDate.
  ///
  /// In en, this message translates to:
  /// **'Build date'**
  String get buildDate;

  /// No description provided for @githubRepository.
  ///
  /// In en, this message translates to:
  /// **'GitHub'**
  String get githubRepository;

  /// No description provided for @openSourceLicenses.
  ///
  /// In en, this message translates to:
  /// **'Licenses'**
  String get openSourceLicenses;

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

  /// No description provided for @indexingFailedPhotoCount.
  ///
  /// In en, this message translates to:
  /// **'Indexing failed · {current}/{total} photos'**
  String indexingFailedPhotoCount(int current, int total);

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
  /// **'Add an album and finish indexing to start searching.'**
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

  /// No description provided for @albumUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The album does not exist or cannot be accessed'**
  String get albumUnavailable;

  /// No description provided for @openAlbumFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not open album: {error}'**
  String openAlbumFailed(Object error);

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

  /// No description provided for @pauseIndexing.
  ///
  /// In en, this message translates to:
  /// **'Pause indexing'**
  String get pauseIndexing;

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

  /// No description provided for @pauseIndexingTitle.
  ///
  /// In en, this message translates to:
  /// **'Pause indexing'**
  String get pauseIndexingTitle;

  /// No description provided for @pauseIndexingMessage.
  ///
  /// In en, this message translates to:
  /// **'Pause the current indexing operation? The album and photos already indexed will be kept, and remaining photos can be indexed later.'**
  String get pauseIndexingMessage;

  /// No description provided for @keepIndexing.
  ///
  /// In en, this message translates to:
  /// **'Keep indexing'**
  String get keepIndexing;

  /// No description provided for @privacyAgreementTitle.
  ///
  /// In en, this message translates to:
  /// **'欢迎使用图搜'**
  String get privacyAgreementTitle;

  /// No description provided for @privacyAgreementMessage.
  ///
  /// In en, this message translates to:
  /// **'❤️ Thanks for installing PicQuery! During your usage with our application, we follow our PicQuery Privacy Policy.    We may collect information about the operation of PicQuery (e.g., crash logs), in order to better improve your experience. The collected data does not contain any of your personal information (not including any pictures either). You can also turn off the upload function of these anonymous information at any time in settings.'**
  String get privacyAgreementMessage;

  /// No description provided for @privacyAgreementAgree.
  ///
  /// In en, this message translates to:
  /// **'Enable reporting'**
  String get privacyAgreementAgree;

  /// No description provided for @privacyAgreementDecline.
  ///
  /// In en, this message translates to:
  /// **'Continue without reporting'**
  String get privacyAgreementDecline;

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

  /// No description provided for @allAlbums.
  ///
  /// In en, this message translates to:
  /// **'All albums'**
  String get allAlbums;

  /// No description provided for @selectedAlbumsCount.
  ///
  /// In en, this message translates to:
  /// **'{count} albums selected'**
  String selectedAlbumsCount(int count);

  /// No description provided for @searchResultCount.
  ///
  /// In en, this message translates to:
  /// **'Number of search results'**
  String get searchResultCount;

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

  /// No description provided for @filterAlbums.
  ///
  /// In en, this message translates to:
  /// **'Filter albums…'**
  String get filterAlbums;

  /// No description provided for @noAlbumsFound.
  ///
  /// In en, this message translates to:
  /// **'No albums found'**
  String get noAlbumsFound;

  /// No description provided for @selectAlbumToIndex.
  ///
  /// In en, this message translates to:
  /// **'Select album to index'**
  String get selectAlbumToIndex;

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

  /// No description provided for @general.
  ///
  /// In en, this message translates to:
  /// **'General'**
  String get general;

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
  /// **'Other'**
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

  /// No description provided for @logs.
  ///
  /// In en, this message translates to:
  /// **'Logs'**
  String get logs;

  /// No description provided for @viewLogs.
  ///
  /// In en, this message translates to:
  /// **'View logs'**
  String get viewLogs;

  /// No description provided for @exportLogs.
  ///
  /// In en, this message translates to:
  /// **'Export logs'**
  String get exportLogs;

  /// No description provided for @refresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// No description provided for @noLogs.
  ///
  /// In en, this message translates to:
  /// **'No logs yet'**
  String get noLogs;

  /// No description provided for @logExportSubject.
  ///
  /// In en, this message translates to:
  /// **'PicQuery logs'**
  String get logExportSubject;

  /// No description provided for @loadLogsFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not load logs: {error}'**
  String loadLogsFailed(Object error);

  /// No description provided for @exportLogsFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not export logs: {error}'**
  String exportLogsFailed(Object error);

  /// No description provided for @dropFolderRequired.
  ///
  /// In en, this message translates to:
  /// **'Please drop a folder'**
  String get dropFolderRequired;

  /// No description provided for @dropFolderRequiredMessage.
  ///
  /// In en, this message translates to:
  /// **'Adding an album requires a folder. Please drag a single folder instead of a file.'**
  String get dropFolderRequiredMessage;

  /// No description provided for @dropFolderNoImagesMessage.
  ///
  /// In en, this message translates to:
  /// **'This folder and its subfolders contain no supported images (JPG, JPEG, PNG, WebP or BMP).'**
  String get dropFolderNoImagesMessage;

  /// No description provided for @dragFolderIndexHint.
  ///
  /// In en, this message translates to:
  /// **'Drag a folder here to quickly index an album.'**
  String get dragFolderIndexHint;

  /// No description provided for @selectOrDropFolder.
  ///
  /// In en, this message translates to:
  /// **'Select or drop a folder'**
  String get selectOrDropFolder;

  /// No description provided for @checkAppUpdates.
  ///
  /// In en, this message translates to:
  /// **'Check for updates'**
  String get checkAppUpdates;

  /// No description provided for @appUpdateTitle.
  ///
  /// In en, this message translates to:
  /// **'App update'**
  String get appUpdateTitle;

  /// No description provided for @appUpdateChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking GitHub releases…'**
  String get appUpdateChecking;

  /// No description provided for @appUpdateCurrent.
  ///
  /// In en, this message translates to:
  /// **'You are using the latest version.'**
  String get appUpdateCurrent;

  /// No description provided for @appUpdateAvailable.
  ///
  /// In en, this message translates to:
  /// **'New version: {version}'**
  String appUpdateAvailable(String version);

  /// No description provided for @appUpdateFailed.
  ///
  /// In en, this message translates to:
  /// **'Update failed. Check your connection and try again.'**
  String get appUpdateFailed;

  /// No description provided for @appUpdateUnsupported.
  ///
  /// In en, this message translates to:
  /// **'No compatible installer is available for this device. View the release page for download options.'**
  String get appUpdateUnsupported;

  /// No description provided for @appUpdateDownload.
  ///
  /// In en, this message translates to:
  /// **'Download and install'**
  String get appUpdateDownload;

  /// No description provided for @appUpdateInstall.
  ///
  /// In en, this message translates to:
  /// **'Install downloaded update'**
  String get appUpdateInstall;

  /// No description provided for @appUpdateDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading… {percent}%'**
  String appUpdateDownloading(String percent);

  /// No description provided for @appUpdateInstalling.
  ///
  /// In en, this message translates to:
  /// **'Opening installer…'**
  String get appUpdateInstalling;

  /// No description provided for @appUpdateOpened.
  ///
  /// In en, this message translates to:
  /// **'The package has been opened. Complete installation in the system window, then restart PicQuery.'**
  String get appUpdateOpened;

  /// No description provided for @appUpdateMacGuidance.
  ///
  /// In en, this message translates to:
  /// **'In the disk image window, quit PicQuery and drag the new app to Applications to replace the existing app.'**
  String get appUpdateMacGuidance;

  /// No description provided for @appUpdateLinuxGuidance.
  ///
  /// In en, this message translates to:
  /// **'Extract the archive, quit PicQuery and replace your existing installation with the extracted files.'**
  String get appUpdateLinuxGuidance;

  /// No description provided for @appUpdatePermission.
  ///
  /// In en, this message translates to:
  /// **'Allow PicQuery to install apps in system settings, then return and tap Install downloaded update.'**
  String get appUpdatePermission;

  /// No description provided for @appUpdateSkip.
  ///
  /// In en, this message translates to:
  /// **'Skip this version'**
  String get appUpdateSkip;

  /// No description provided for @appUpdateLater.
  ///
  /// In en, this message translates to:
  /// **'Later'**
  String get appUpdateLater;

  /// No description provided for @appUpdateRetry.
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get appUpdateRetry;

  /// No description provided for @appUpdateReleasePage.
  ///
  /// In en, this message translates to:
  /// **'View release page'**
  String get appUpdateReleasePage;

  /// No description provided for @appUpdateClose.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get appUpdateClose;

  /// No description provided for @reportProblemPrompt.
  ///
  /// In en, this message translates to:
  /// **'Having trouble? Click to report error logs'**
  String get reportProblemPrompt;

  /// No description provided for @errorReportSubmitted.
  ///
  /// In en, this message translates to:
  /// **'Report submitted'**
  String get errorReportSubmitted;

  /// No description provided for @errorReportingUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Error reporting is not configured yet.'**
  String get errorReportingUnavailable;

  /// No description provided for @errorReportFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not submit the report. Check your connection and retry.'**
  String get errorReportFailed;

  /// No description provided for @automaticErrorReporting.
  ///
  /// In en, this message translates to:
  /// **'Automatic crash and exception reporting'**
  String get automaticErrorReporting;

  /// No description provided for @errorReportingDescription.
  ///
  /// In en, this message translates to:
  /// **'Send diagnostics and redacted logs to Sentry. Manual reports are sent only when you click Report.'**
  String get errorReportingDescription;

  /// No description provided for @privacyPolicy.
  ///
  /// In en, this message translates to:
  /// **'Privacy Policy'**
  String get privacyPolicy;

  /// No description provided for @indexingReportHint.
  ///
  /// In en, this message translates to:
  /// **'Indexing failed. You can report error logs below.'**
  String get indexingReportHint;

  /// No description provided for @privacyPolicyLoadFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not load the Privacy Policy.'**
  String get privacyPolicyLoadFailed;

  /// No description provided for @startupTitle.
  ///
  /// In en, this message translates to:
  /// **'Preparing PicQuery'**
  String get startupTitle;

  /// No description provided for @startupMessage.
  ///
  /// In en, this message translates to:
  /// **'The first launch needs to prepare local model resources and may take a little longer. Everything stays on your device.'**
  String get startupMessage;

  /// No description provided for @startupDatabase.
  ///
  /// In en, this message translates to:
  /// **'Opening local database…'**
  String get startupDatabase;

  /// No description provided for @startupClipAssets.
  ///
  /// In en, this message translates to:
  /// **'Preparing image search resources…'**
  String get startupClipAssets;

  /// No description provided for @startupClipLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading image search models…'**
  String get startupClipLoading;

  /// No description provided for @startupTranslationAssets.
  ///
  /// In en, this message translates to:
  /// **'Preparing translation resources…'**
  String get startupTranslationAssets;

  /// No description provided for @startupTranslationLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading translation model…'**
  String get startupTranslationLoading;

  /// No description provided for @startupFailed.
  ///
  /// In en, this message translates to:
  /// **'Preparation failed'**
  String get startupFailed;

  /// No description provided for @startupFailedMessage.
  ///
  /// In en, this message translates to:
  /// **'Could not prepare local resources. Check that your device has enough free space, then retry. If the problem persists, restart the app.'**
  String get startupFailedMessage;

  /// No description provided for @startupRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get startupRetry;
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
