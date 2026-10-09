// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String pausedPhotoCount(int current, int total) {
    return 'Paused · $current/$total photos';
  }

  @override
  String get appTitle => 'PicQuery';

  @override
  String get search => 'Search';

  @override
  String get albums => 'Albums';

  @override
  String get settings => 'Settings';

  @override
  String get currentVersion => 'Version';

  @override
  String get buildDate => 'Build date';

  @override
  String get githubRepository => 'GitHub';

  @override
  String get openSourceLicenses => 'Licenses';

  @override
  String get cancel => 'Cancel';

  @override
  String get confirm => 'Confirm';

  @override
  String get remove => 'Remove';

  @override
  String get delete => 'Delete';

  @override
  String get update => 'Update';

  @override
  String get continueAction => 'Continue';

  @override
  String get stop => 'Stop';

  @override
  String get ok => 'OK';

  @override
  String get addAlbum => 'Add album';

  @override
  String get checkUpdates => 'Check for updates';

  @override
  String get checking => 'Checking';

  @override
  String updatePhotos(int count) {
    return 'Update $count photos';
  }

  @override
  String indexingAlbumsSummary(int count) {
    return 'Building image index · $count albums';
  }

  @override
  String albumsPhotosSummary(int albumCount, int photoCount) {
    return '$albumCount albums · $photoCount photos';
  }

  @override
  String incompletePhotoCount(int current, int total) {
    return 'Incomplete · $current/$total photos';
  }

  @override
  String indexingFailedPhotoCount(int current, int total) {
    return 'Indexing failed · $current/$total photos';
  }

  @override
  String updatesPhotoCount(int count) {
    return 'Updates available · $count photos';
  }

  @override
  String photoCount(int count) {
    return '$count photos';
  }

  @override
  String get buildPhotoLibrary => 'Build your photo library';

  @override
  String get buildPhotoLibraryDescription =>
      'Add an album and finish indexing to start searching.';

  @override
  String get addFirstAlbum => 'Add your first album';

  @override
  String get desktopOnlyOpenAlbum =>
      'Opening an album location is only supported on desktop';

  @override
  String get albumUnavailable =>
      'The album does not exist or cannot be accessed';

  @override
  String openAlbumFailed(Object error) {
    return 'Could not open album: $error';
  }

  @override
  String get removeAlbumIndex => 'Remove album index';

  @override
  String get removeAlbumIndexMessage =>
      'Remove this album index? Your original photos will not be deleted.';

  @override
  String get albumActions => 'Album actions';

  @override
  String get cancelIndexing => 'Cancel indexing';

  @override
  String get pauseIndexing => 'Pause indexing';

  @override
  String get continueIndexing => 'Continue indexing';

  @override
  String indexingPercent(int percent) {
    return 'Indexing · $percent%';
  }

  @override
  String indexingProgress(int current, int total, Object speed) {
    return '$current/$total ($speed photos/sec)';
  }

  @override
  String get indexUpToDate => 'Album index is up to date';

  @override
  String checkUpdatesFailed(Object error) {
    return 'Could not check for updates: $error';
  }

  @override
  String get incrementalUpdate => 'Incremental update';

  @override
  String get alreadyIndexed => 'Already indexed';

  @override
  String get alreadyIndexedMessage =>
      'This album is already indexed. Would you like to update it?';

  @override
  String indexingError(Object error) {
    return 'Indexing failed: $error';
  }

  @override
  String get albumUnavailableReAdd =>
      'This album cannot be accessed. Please add it again.';

  @override
  String get dcimNotFound => 'No accessible photos or DCIM album found';

  @override
  String get cancelIndexingTitle => 'Cancel indexing';

  @override
  String get cancelIndexingMessage =>
      'Cancel the current indexing operation? Photos already indexed will be kept.';

  @override
  String get pauseIndexingTitle => 'Pause indexing';

  @override
  String get pauseIndexingMessage =>
      'Pause the current indexing operation? The album and photos already indexed will be kept, and remaining photos can be indexed later.';

  @override
  String get keepIndexing => 'Keep indexing';

  @override
  String get privacyAgreementTitle => '欢迎使用图搜';

  @override
  String get privacyAgreementMessage =>
      '❤️ Thanks for installing PicQuery! During your usage with our application, we follow our PicQuery Privacy Policy.    We may collect information about the operation of PicQuery (e.g., crash logs), in order to better improve your experience. The collected data does not contain any of your personal information (not including any pictures either). You can also turn off the upload function of these anonymous information at any time in settings.';

  @override
  String get privacyAgreementAgree => 'Enable reporting';

  @override
  String get privacyAgreementDecline => 'Continue without reporting';

  @override
  String get searchPhotosTitle => 'Search your photo library';

  @override
  String get searchPhotosTitleMobile => 'Search your photos';

  @override
  String get searchPhotosDescription =>
      'Find your memories with simple words or an image. All search happens privately on your device.';

  @override
  String get searchPhotosDescriptionMobile => 'Private, offline image search';

  @override
  String get searchPhotosHint => 'Search photos';

  @override
  String get searchByImage => 'Search by image';

  @override
  String get recentSearches => 'Recent searches';

  @override
  String get allAlbums => 'All albums';

  @override
  String selectedAlbumsCount(int count) {
    return '$count albums selected';
  }

  @override
  String get searchResultCount => 'Number of search results';

  @override
  String get clearFilters => 'Clear filters';

  @override
  String searchFailed(Object error) {
    return 'Search failed: $error';
  }

  @override
  String imageSearchFailed(Object error) {
    return 'Image search failed: $error';
  }

  @override
  String get indexingResultsWarning =>
      'Indexing is in progress. Search results may be incomplete.';

  @override
  String get noMatchesFound => 'No matches found';

  @override
  String get selectImageToSearch => 'Select an image to search';

  @override
  String get searchScope => 'Search scope';

  @override
  String get all => 'All';

  @override
  String get custom => 'Custom';

  @override
  String get selectScope => 'Select scope';

  @override
  String get selectAll => 'Select all';

  @override
  String get filterAlbums => 'Filter albums…';

  @override
  String get noAlbumsFound => 'No albums found';

  @override
  String get selectAlbumToIndex => 'Select album to index';

  @override
  String get permissionDenied => 'Permission denied';

  @override
  String get photoPermissionMessage =>
      'Photo library access is required to select albums for indexing.';

  @override
  String get noAlbumsMessage => 'No photo albums were found on your device.';

  @override
  String get noImagesFound => 'No images found';

  @override
  String get noImagesMessage => 'No images were found in the selected album.';

  @override
  String get selectAlbum => 'Select album';

  @override
  String get previous => 'Previous';

  @override
  String get next => 'Next';

  @override
  String get info => 'Info';

  @override
  String get share => 'Share';

  @override
  String get open => 'Open';

  @override
  String get fileNotFound => 'File not found';

  @override
  String shareFailed(Object error) {
    return 'Share failed: $error';
  }

  @override
  String openFailed(Object error) {
    return 'Open failed: $error';
  }

  @override
  String get imageDetails => 'Image details';

  @override
  String get fileName => 'File name';

  @override
  String get filePath => 'File path';

  @override
  String get format => 'Format';

  @override
  String get dimensions => 'Dimensions';

  @override
  String get fileSize => 'File size';

  @override
  String get modified => 'Modified';

  @override
  String get similarity => 'Similarity';

  @override
  String get unknown => 'Unknown';

  @override
  String get general => 'General';

  @override
  String get appearance => 'Appearance';

  @override
  String get light => 'Light';

  @override
  String get dark => 'Dark';

  @override
  String get followSystem => 'Follow system';

  @override
  String get language => 'Language';

  @override
  String get systemLanguage => 'Follow system';

  @override
  String get simplifiedChinese => '简体中文';

  @override
  String get traditionalChinese => '繁體中文';

  @override
  String get english => 'English';

  @override
  String get indexUpdates => 'Index updates';

  @override
  String get autoUpdateOnStartup => 'Update index on startup';

  @override
  String get autoUpdateOnStartupDescription =>
      'Automatically update indexed albums when photo changes are detected';

  @override
  String get model => 'Model';

  @override
  String get dataManagement => 'Other';

  @override
  String get clearAllIndexes => 'Clear all indexes';

  @override
  String get clearRecentSearches => 'Clear recent searches';

  @override
  String get deleteAllIndexesTitle => 'Delete all indexes';

  @override
  String get deleteAllIndexesMessage =>
      'Delete all index data? This action cannot be undone.';

  @override
  String get clearRecentSearchesTitle => 'Clear recent searches';

  @override
  String get clearRecentSearchesMessage => 'Clear all recent search history?';

  @override
  String get clear => 'Clear';

  @override
  String get recentSearchesCleared => 'Recent searches cleared';

  @override
  String get reloadModels => 'Reload models';

  @override
  String get modelsReloaded => 'Models reloaded successfully';

  @override
  String modelsReloadFailed(Object error) {
    return 'Could not reload models: $error';
  }

  @override
  String get timeAny => 'Any time';

  @override
  String get timeDay => 'Past day';

  @override
  String get timeWeek => 'Past week';

  @override
  String get timeMonth => 'Past month';

  @override
  String get timeYear => 'Past year';

  @override
  String get addAlbumWhileIndexing =>
      'Please wait until indexing finishes before adding another album';

  @override
  String get logs => 'Logs';

  @override
  String get viewLogs => 'View logs';

  @override
  String get exportLogs => 'Export logs';

  @override
  String get refresh => 'Refresh';

  @override
  String get noLogs => 'No logs yet';

  @override
  String get logExportSubject => 'PicQuery logs';

  @override
  String loadLogsFailed(Object error) {
    return 'Could not load logs: $error';
  }

  @override
  String exportLogsFailed(Object error) {
    return 'Could not export logs: $error';
  }

  @override
  String get dropFolderRequired => 'Please drop a folder';

  @override
  String get dropFolderRequiredMessage =>
      'Adding an album requires a folder. Please drag a single folder instead of a file.';

  @override
  String get dropFolderNoImagesMessage =>
      'This folder and its subfolders contain no supported images (JPG, JPEG, PNG, WebP or BMP).';

  @override
  String get dragFolderIndexHint =>
      'Drag a folder here to quickly index an album.';

  @override
  String get selectOrDropFolder => 'Select or drop a folder';

  @override
  String get checkAppUpdates => 'Check for updates';

  @override
  String get appUpdateTitle => 'App update';

  @override
  String get appUpdateChecking => 'Checking GitHub releases…';

  @override
  String get appUpdateCurrent => 'You are using the latest version.';

  @override
  String appUpdateAvailable(String version) {
    return 'New version: $version';
  }

  @override
  String get appUpdateFailed =>
      'Update failed. Check your connection and try again.';

  @override
  String get appUpdateUnsupported =>
      'No compatible installer is available for this device. View the release page for download options.';

  @override
  String get appUpdateDownload => 'Download and install';

  @override
  String get appUpdateInstall => 'Install downloaded update';

  @override
  String appUpdateDownloading(String percent) {
    return 'Downloading… $percent%';
  }

  @override
  String get appUpdateInstalling => 'Opening installer…';

  @override
  String get appUpdateOpened =>
      'The package has been opened. Complete installation in the system window, then restart PicQuery.';

  @override
  String get appUpdateMacGuidance =>
      'In the disk image window, quit PicQuery and drag the new app to Applications to replace the existing app.';

  @override
  String get appUpdateLinuxGuidance =>
      'Extract the archive, quit PicQuery and replace your existing installation with the extracted files.';

  @override
  String get appUpdatePermission =>
      'Allow PicQuery to install apps in system settings, then return and tap Install downloaded update.';

  @override
  String get appUpdateSkip => 'Skip this version';

  @override
  String get appUpdateLater => 'Later';

  @override
  String get appUpdateRetry => 'Try again';

  @override
  String get appUpdateReleasePage => 'View release page';

  @override
  String get appUpdateClose => 'Close';

  @override
  String get reportProblemPrompt =>
      'Having trouble? Click to report error logs';

  @override
  String get errorReportSubmitted => 'Report submitted';

  @override
  String get errorReportingUnavailable =>
      'Error reporting is not configured yet.';

  @override
  String get errorReportFailed =>
      'Could not submit the report. Check your connection and retry.';

  @override
  String get automaticErrorReporting =>
      'Automatic crash and exception reporting';

  @override
  String get errorReportingDescription =>
      'Send diagnostics and redacted logs to Sentry. Manual reports are sent only when you click Report.';

  @override
  String get privacyPolicy => 'Privacy Policy';

  @override
  String get indexingReportHint =>
      'Indexing failed. You can report error logs below.';

  @override
  String get privacyPolicyLoadFailed => 'Could not load the Privacy Policy.';

  @override
  String get startupTitle => 'Preparing PicQuery';

  @override
  String get startupMessage =>
      'The first launch needs to prepare local model resources and may take a little longer. Everything stays on your device.';

  @override
  String get startupDatabase => 'Opening local database…';

  @override
  String get startupClipAssets => 'Preparing image search resources…';

  @override
  String get startupClipLoading => 'Loading image search models…';

  @override
  String get startupTranslationAssets => 'Preparing translation resources…';

  @override
  String get startupTranslationLoading => 'Loading translation model…';

  @override
  String get startupFailed => 'Preparation failed';

  @override
  String get startupFailedMessage =>
      'Could not prepare local resources. Check that your device has enough free space, then retry. If the problem persists, restart the app.';

  @override
  String get startupRetry => 'Retry';
}
