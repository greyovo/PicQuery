// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'PicQuery';

  @override
  String get search => 'Search';

  @override
  String get albums => 'Albums';

  @override
  String get settings => 'Settings';

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
      'Add local albums and PicQuery will index them privately on your device.';

  @override
  String get addFirstAlbum => 'Add your first album';

  @override
  String get desktopOnlyOpenAlbum =>
      'Opening an album location is only supported on desktop';

  @override
  String get folderUnavailable =>
      'The folder does not exist or cannot be accessed';

  @override
  String openFolderFailed(Object error) {
    return 'Could not open folder: $error';
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
  String get keepIndexing => 'Keep indexing';

  @override
  String get indexDcimTitle => 'Index the DCIM album?';

  @override
  String get indexDcimMessage =>
      'For your first use, index the phone\'s DCIM album to search photos by content.';

  @override
  String get notNow => 'Not now';

  @override
  String get startIndexing => 'Start indexing';

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
  String get allFolders => 'All folders';

  @override
  String selectedAlbumsCount(int count) {
    return '$count albums selected';
  }

  @override
  String resultCount(int count) {
    return '$count results';
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
  String get filterFolders => 'Filter folders…';

  @override
  String get noFoldersFound => 'No folders found';

  @override
  String get selectFolderToIndex => 'Select folder to index';

  @override
  String get permissionDenied => 'Permission denied';

  @override
  String get photoPermissionMessage =>
      'Photo library access is required to select albums for indexing.';

  @override
  String get noAlbumsFound => 'No albums found';

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
  String get dataManagement => 'Data management';

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
}
