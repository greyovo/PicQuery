// ignore: unused_import
import 'package:intl/intl.dart' as intl;

import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Chinese (`zh`).
class AppLocalizationsZh extends AppLocalizations {
  AppLocalizationsZh([String locale = 'zh']) : super(locale);

  @override
  String get appTitle => 'PicQuery';

  @override
  String get search => '搜索';

  @override
  String get albums => '相册';

  @override
  String get settings => '设置';

  @override
  String get cancel => '取消';

  @override
  String get confirm => '确认';

  @override
  String get remove => '移除';

  @override
  String get delete => '删除';

  @override
  String get update => '更新';

  @override
  String get continueAction => '继续';

  @override
  String get stop => '停止';

  @override
  String get ok => '确定';

  @override
  String get addAlbum => '添加相册';

  @override
  String get checkUpdates => '检查更新';

  @override
  String get checking => '正在检查';

  @override
  String updatePhotos(int count) {
    return '更新 $count 张';
  }

  @override
  String indexingAlbumsSummary(int count) {
    return '正在建立图片索引 · $count 个相册';
  }

  @override
  String albumsPhotosSummary(int albumCount, int photoCount) {
    return '$albumCount 个相册 · $photoCount 张照片';
  }

  @override
  String incompletePhotoCount(int current, int total) {
    return '未完成 · $current/$total 张';
  }

  @override
  String updatesPhotoCount(int count) {
    return '有更新 · $count 张照片';
  }

  @override
  String photoCount(int count) {
    return '$count 张照片';
  }

  @override
  String get buildPhotoLibrary => '建立你的图片库';

  @override
  String get buildPhotoLibraryDescription => '添加相册并索引完成后，即可开始搜索';

  @override
  String get addFirstAlbum => '添加第一个相册';

  @override
  String get desktopOnlyOpenAlbum => '仅支持在桌面端打开相册位置';

  @override
  String get albumUnavailable => '相册不存在或无法访问';

  @override
  String openAlbumFailed(Object error) {
    return '无法打开相册：$error';
  }

  @override
  String get removeAlbumIndex => '移除相册索引';

  @override
  String get removeAlbumIndexMessage => '确定要移除这个相册的索引吗？原始照片不会被删除。';

  @override
  String get albumActions => '相册操作';

  @override
  String get cancelIndexing => '取消索引';

  @override
  String get pauseIndexing => '暂停索引';

  @override
  String get continueIndexing => '继续索引';

  @override
  String indexingPercent(int percent) {
    return '正在索引 · $percent%';
  }

  @override
  String indexingProgress(int current, int total, Object speed) {
    return '$current/$total（$speed 张/秒）';
  }

  @override
  String get indexUpToDate => '相册索引已是最新';

  @override
  String checkUpdatesFailed(Object error) {
    return '检查更新失败：$error';
  }

  @override
  String get incrementalUpdate => '增量更新';

  @override
  String get alreadyIndexed => '已建立索引';

  @override
  String get alreadyIndexedMessage => '这个相册已经建立索引。是否更新？';

  @override
  String indexingError(Object error) {
    return '索引失败：$error';
  }

  @override
  String get albumUnavailableReAdd => '无法访问该相册，请重新添加';

  @override
  String get dcimNotFound => '未找到 DCIM 相册或没有可访问的照片';

  @override
  String get cancelIndexingTitle => '取消索引';

  @override
  String get cancelIndexingMessage => '确定要取消当前的索引操作吗？已索引的图片将被保留。';

  @override
  String get pauseIndexingTitle => '暂停索引';

  @override
  String get pauseIndexingMessage => '确定要暂停当前索引吗？相册和已索引图片会保留，剩余图片可稍后继续索引。';

  @override
  String get keepIndexing => '继续索引';

  @override
  String get indexDcimTitle => '索引 DCIM 相册？';

  @override
  String get indexDcimMessage => '首次使用建议索引手机 DCIM 相册，以便按图片内容搜索。';

  @override
  String get notNow => '暂不';

  @override
  String get startIndexing => '开始索引';

  @override
  String get searchPhotosTitle => '搜索你的图片库';

  @override
  String get searchPhotosTitleMobile => '搜索你的照片';

  @override
  String get searchPhotosDescription => '用简单的文字或图片寻找回忆。所有搜索均在设备上私密完成。';

  @override
  String get searchPhotosDescriptionMobile => '私密、离线的图片搜索';

  @override
  String get searchPhotosHint => '搜索照片';

  @override
  String get searchByImage => '以图搜图';

  @override
  String get recentSearches => '最近搜索';

  @override
  String get allAlbums => '全部相册';

  @override
  String selectedAlbumsCount(int count) {
    return '已选择 $count 个相册';
  }

  @override
  String get searchResultCount => '搜索结果数量';

  @override
  String get clearFilters => '清除筛选';

  @override
  String searchFailed(Object error) {
    return '搜索失败：$error';
  }

  @override
  String imageSearchFailed(Object error) {
    return '图片搜索失败：$error';
  }

  @override
  String get indexingResultsWarning => '正在索引中，搜索结果可能不完整。';

  @override
  String get noMatchesFound => '没有找到匹配结果';

  @override
  String get selectImageToSearch => '选择用于搜索的图片';

  @override
  String get searchScope => '搜索范围';

  @override
  String get all => '全部';

  @override
  String get custom => '自定义';

  @override
  String get selectScope => '选择范围';

  @override
  String get selectAll => '全选';

  @override
  String get filterAlbums => '筛选相册…';

  @override
  String get noAlbumsFound => '没有找到相册';

  @override
  String get selectAlbumToIndex => '选择要索引的相册';

  @override
  String get permissionDenied => '权限被拒绝';

  @override
  String get photoPermissionMessage => '选择相册进行索引需要照片库访问权限。';

  @override
  String get noAlbumsMessage => '设备上没有找到照片相册。';

  @override
  String get noImagesFound => '未找到图片';

  @override
  String get noImagesMessage => '所选相册中没有图片。';

  @override
  String get selectAlbum => '选择相册';

  @override
  String get previous => '上一张';

  @override
  String get next => '下一张';

  @override
  String get info => '信息';

  @override
  String get share => '分享';

  @override
  String get open => '打开';

  @override
  String get fileNotFound => '文件不存在';

  @override
  String shareFailed(Object error) {
    return '分享失败：$error';
  }

  @override
  String openFailed(Object error) {
    return '打开失败：$error';
  }

  @override
  String get imageDetails => '图片详情';

  @override
  String get fileName => '文件名';

  @override
  String get filePath => '文件路径';

  @override
  String get format => '格式';

  @override
  String get dimensions => '尺寸';

  @override
  String get fileSize => '文件大小';

  @override
  String get modified => '修改时间';

  @override
  String get similarity => '相似度';

  @override
  String get unknown => '未知';

  @override
  String get general => '通用';

  @override
  String get appearance => '外观';

  @override
  String get light => '浅色';

  @override
  String get dark => '深色';

  @override
  String get followSystem => '跟随系统';

  @override
  String get language => '语言';

  @override
  String get systemLanguage => '跟随系统';

  @override
  String get simplifiedChinese => '简体中文';

  @override
  String get traditionalChinese => '繁體中文';

  @override
  String get english => 'English';

  @override
  String get indexUpdates => '索引更新';

  @override
  String get autoUpdateOnStartup => '启动时自动更新索引';

  @override
  String get autoUpdateOnStartupDescription => '检查到已索引相册中的照片变化后自动更新';

  @override
  String get model => '模型';

  @override
  String get dataManagement => '数据管理';

  @override
  String get clearAllIndexes => '清空所有索引';

  @override
  String get clearRecentSearches => '清空最近搜索';

  @override
  String get deleteAllIndexesTitle => '删除所有索引';

  @override
  String get deleteAllIndexesMessage => '确定要删除所有索引数据吗？此操作不可恢复。';

  @override
  String get clearRecentSearchesTitle => '清空最近搜索';

  @override
  String get clearRecentSearchesMessage => '确定要清空所有最近搜索记录吗？';

  @override
  String get clear => '清空';

  @override
  String get recentSearchesCleared => '最近搜索已清空';

  @override
  String get reloadModels => '重新加载模型';

  @override
  String get modelsReloaded => '模型重新加载成功';

  @override
  String modelsReloadFailed(Object error) {
    return '模型重新加载失败：$error';
  }

  @override
  String get timeAny => '不限时间';

  @override
  String get timeDay => '过去一天';

  @override
  String get timeWeek => '过去一周';

  @override
  String get timeMonth => '过去一个月';

  @override
  String get timeYear => '过去一年';

  @override
  String get addAlbumWhileIndexing => '索引图片中，请稍后再添加相册';

  @override
  String get logs => '日志';

  @override
  String get viewLogs => '查看日志';

  @override
  String get exportLogs => '导出日志';

  @override
  String get refresh => '刷新';

  @override
  String get noLogs => '暂无日志';

  @override
  String get logExportSubject => 'PicQuery 日志';

  @override
  String loadLogsFailed(Object error) {
    return '加载日志失败：$error';
  }

  @override
  String exportLogsFailed(Object error) {
    return '导出日志失败：$error';
  }
}

/// The translations for Chinese, using the Han script (`zh_Hant`).
class AppLocalizationsZhHant extends AppLocalizationsZh {
  AppLocalizationsZhHant() : super('zh_Hant');

  @override
  String get appTitle => 'PicQuery';

  @override
  String get search => '搜尋';

  @override
  String get albums => '相簿';

  @override
  String get settings => '設定';

  @override
  String get cancel => '取消';

  @override
  String get confirm => '確認';

  @override
  String get remove => '移除';

  @override
  String get delete => '刪除';

  @override
  String get update => '更新';

  @override
  String get continueAction => '繼續';

  @override
  String get stop => '停止';

  @override
  String get ok => '確定';

  @override
  String get addAlbum => '新增相簿';

  @override
  String get checkUpdates => '檢查更新';

  @override
  String get checking => '正在檢查';

  @override
  String updatePhotos(int count) {
    return '更新 $count 張';
  }

  @override
  String indexingAlbumsSummary(int count) {
    return '正在建立圖片索引 · $count 個相簿';
  }

  @override
  String albumsPhotosSummary(int albumCount, int photoCount) {
    return '$albumCount 個相簿 · $photoCount 張照片';
  }

  @override
  String incompletePhotoCount(int current, int total) {
    return '未完成 · $current/$total 張';
  }

  @override
  String updatesPhotoCount(int count) {
    return '有更新 · $count 張照片';
  }

  @override
  String photoCount(int count) {
    return '$count 張照片';
  }

  @override
  String get buildPhotoLibrary => '建立你的圖片庫';

  @override
  String get buildPhotoLibraryDescription => '新增相簿並完成索引後，即可開始搜尋';

  @override
  String get addFirstAlbum => '新增第一個相簿';

  @override
  String get desktopOnlyOpenAlbum => '僅支援在桌面端開啟相簿位置';

  @override
  String get albumUnavailable => '相簿不存在或無法存取';

  @override
  String openAlbumFailed(Object error) {
    return '無法開啟相簿：$error';
  }

  @override
  String get removeAlbumIndex => '移除相簿索引';

  @override
  String get removeAlbumIndexMessage => '確定要移除這個相簿的索引嗎？原始照片不會被刪除。';

  @override
  String get albumActions => '相簿操作';

  @override
  String get cancelIndexing => '取消索引';

  @override
  String get pauseIndexing => '暫停索引';

  @override
  String get continueIndexing => '繼續索引';

  @override
  String indexingPercent(int percent) {
    return '正在索引 · $percent%';
  }

  @override
  String indexingProgress(int current, int total, Object speed) {
    return '$current/$total（$speed 張/秒）';
  }

  @override
  String get indexUpToDate => '相簿索引已是最新';

  @override
  String checkUpdatesFailed(Object error) {
    return '檢查更新失敗：$error';
  }

  @override
  String get incrementalUpdate => '增量更新';

  @override
  String get alreadyIndexed => '已建立索引';

  @override
  String get alreadyIndexedMessage => '這個相簿已經建立索引。是否更新？';

  @override
  String indexingError(Object error) {
    return '索引失敗：$error';
  }

  @override
  String get albumUnavailableReAdd => '無法存取該相簿，請重新新增';

  @override
  String get dcimNotFound => '未找到 DCIM 相簿或沒有可存取的照片';

  @override
  String get cancelIndexingTitle => '取消索引';

  @override
  String get cancelIndexingMessage => '確定要取消目前的索引操作嗎？已索引的圖片將被保留。';

  @override
  String get pauseIndexingTitle => '暫停索引';

  @override
  String get pauseIndexingMessage => '確定要暫停目前索引嗎？相簿和已索引圖片會保留，剩餘圖片可稍後繼續索引。';

  @override
  String get keepIndexing => '繼續索引';

  @override
  String get indexDcimTitle => '索引 DCIM 相簿？';

  @override
  String get indexDcimMessage => '首次使用建議索引手機 DCIM 相簿，以便按圖片內容搜尋。';

  @override
  String get notNow => '暫不';

  @override
  String get startIndexing => '開始索引';

  @override
  String get searchPhotosTitle => '搜尋你的圖片庫';

  @override
  String get searchPhotosTitleMobile => '搜尋你的照片';

  @override
  String get searchPhotosDescription => '用簡單的文字或圖片尋找回憶。所有搜尋均在裝置上私密完成。';

  @override
  String get searchPhotosDescriptionMobile => '私密、離線的圖片搜尋';

  @override
  String get searchPhotosHint => '搜尋照片';

  @override
  String get searchByImage => '以圖搜圖';

  @override
  String get recentSearches => '最近搜尋';

  @override
  String get allAlbums => '全部相簿';

  @override
  String selectedAlbumsCount(int count) {
    return '已選擇 $count 個相簿';
  }

  @override
  String get searchResultCount => '搜尋結果數量';

  @override
  String get clearFilters => '清除篩選';

  @override
  String searchFailed(Object error) {
    return '搜尋失敗：$error';
  }

  @override
  String imageSearchFailed(Object error) {
    return '圖片搜尋失敗：$error';
  }

  @override
  String get indexingResultsWarning => '正在索引中，搜尋結果可能不完整。';

  @override
  String get noMatchesFound => '沒有找到符合結果';

  @override
  String get selectImageToSearch => '選擇用於搜尋的圖片';

  @override
  String get searchScope => '搜尋範圍';

  @override
  String get all => '全部';

  @override
  String get custom => '自訂';

  @override
  String get selectScope => '選擇範圍';

  @override
  String get selectAll => '全選';

  @override
  String get filterAlbums => '篩選相簿…';

  @override
  String get noAlbumsFound => '沒有找到相簿';

  @override
  String get selectAlbumToIndex => '選擇要索引的相簿';

  @override
  String get permissionDenied => '權限被拒絕';

  @override
  String get photoPermissionMessage => '選擇相簿進行索引需要照片庫存取權限。';

  @override
  String get noAlbumsMessage => '裝置上沒有找到照片相簿。';

  @override
  String get noImagesFound => '未找到圖片';

  @override
  String get noImagesMessage => '所選相簿中沒有圖片。';

  @override
  String get selectAlbum => '選擇相簿';

  @override
  String get previous => '上一張';

  @override
  String get next => '下一張';

  @override
  String get info => '資訊';

  @override
  String get share => '分享';

  @override
  String get open => '開啟';

  @override
  String get fileNotFound => '檔案不存在';

  @override
  String shareFailed(Object error) {
    return '分享失敗：$error';
  }

  @override
  String openFailed(Object error) {
    return '開啟失敗：$error';
  }

  @override
  String get imageDetails => '圖片詳情';

  @override
  String get fileName => '檔案名稱';

  @override
  String get filePath => '檔案路徑';

  @override
  String get format => '格式';

  @override
  String get dimensions => '尺寸';

  @override
  String get fileSize => '檔案大小';

  @override
  String get modified => '修改時間';

  @override
  String get similarity => '相似度';

  @override
  String get unknown => '未知';

  @override
  String get general => '通用';

  @override
  String get appearance => '外觀';

  @override
  String get light => '淺色';

  @override
  String get dark => '深色';

  @override
  String get followSystem => '跟隨系統';

  @override
  String get language => '語言';

  @override
  String get systemLanguage => '跟隨系統';

  @override
  String get simplifiedChinese => '简体中文';

  @override
  String get traditionalChinese => '繁體中文';

  @override
  String get english => 'English';

  @override
  String get indexUpdates => '索引更新';

  @override
  String get autoUpdateOnStartup => '啟動時自動更新索引';

  @override
  String get autoUpdateOnStartupDescription => '檢查到已索引相簿中的照片變化後自動更新';

  @override
  String get model => '模型';

  @override
  String get dataManagement => '資料管理';

  @override
  String get clearAllIndexes => '清空所有索引';

  @override
  String get clearRecentSearches => '清空最近搜尋';

  @override
  String get deleteAllIndexesTitle => '刪除所有索引';

  @override
  String get deleteAllIndexesMessage => '確定要刪除所有索引資料嗎？此操作無法復原。';

  @override
  String get clearRecentSearchesTitle => '清空最近搜尋';

  @override
  String get clearRecentSearchesMessage => '確定要清空所有最近搜尋記錄嗎？';

  @override
  String get clear => '清空';

  @override
  String get recentSearchesCleared => '最近搜尋已清空';

  @override
  String get reloadModels => '重新載入模型';

  @override
  String get modelsReloaded => '模型重新載入成功';

  @override
  String modelsReloadFailed(Object error) {
    return '模型重新載入失敗：$error';
  }

  @override
  String get timeAny => '不限時間';

  @override
  String get timeDay => '過去一天';

  @override
  String get timeWeek => '過去一週';

  @override
  String get timeMonth => '過去一個月';

  @override
  String get timeYear => '過去一年';

  @override
  String get addAlbumWhileIndexing => '索引圖片中，請稍後再新增相簿';

  @override
  String get logs => '日誌';

  @override
  String get viewLogs => '查看日誌';

  @override
  String get exportLogs => '匯出日誌';

  @override
  String get refresh => '重新整理';

  @override
  String get noLogs => '暫無日誌';

  @override
  String get logExportSubject => 'PicQuery 日誌';

  @override
  String loadLogsFailed(Object error) {
    return '載入日誌失敗：$error';
  }

  @override
  String exportLogsFailed(Object error) {
    return '匯出日誌失敗：$error';
  }
}
