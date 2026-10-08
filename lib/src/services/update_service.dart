import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:pub_semver/pub_semver.dart';
import 'package:url_launcher/url_launcher.dart';

Version parseReleaseVersion(String value) =>
    Version.parse(value.replaceFirst(RegExp(r'^[vV]'), ''));

// pub_semver orders build metadata; release precedence must ignore it.
bool isNewerRelease(Version release, Version installed) {
  Version withoutBuild(Version v) => Version(
    v.major,
    v.minor,
    v.patch,
    pre: v.preRelease.isEmpty ? null : v.preRelease.join('.'),
  );
  return withoutBuild(release) > withoutBuild(installed);
}

class UpdateAsset {
  const UpdateAsset({
    required this.id,
    required this.name,
    required this.url,
    required this.size,
    this.digest,
  });
  final int id;
  final String name;
  final Uri url;
  final int size;
  final String? digest;

  factory UpdateAsset.fromJson(Map<String, dynamic> json) => UpdateAsset(
    id: json['id'] as int,
    name: json['name'] as String,
    url: Uri.parse(json['browser_download_url'] as String),
    size: json['size'] as int,
    digest: json['digest'] as String?,
  );
}

class AppRelease {
  const AppRelease({
    required this.version,
    required this.notes,
    required this.page,
    required this.assets,
  });
  final Version version;
  final String notes;
  final Uri page;
  final List<UpdateAsset> assets;

  factory AppRelease.fromJson(Map<String, dynamic> json) => AppRelease(
    version: parseReleaseVersion(json['tag_name'] as String),
    notes: json['body'] as String? ?? '',
    page: Uri.parse(json['html_url'] as String),
    assets: (json['assets'] as List)
        .map((a) => UpdateAsset.fromJson(a as Map<String, dynamic>))
        .toList(),
  );

  UpdateAsset? assetFor(String os, String architecture) {
    final suffix = switch ((os, architecture)) {
      ('android', 'arm64') => '-android-arm64.apk',
      ('macos', 'arm64') => '-macos.dmg',
      ('windows', 'x64') => '-windows-x64-setup.exe',
      ('linux', 'x64') => '-linux-x64.tar.gz',
      _ => null,
    };
    if (suffix == null) return null;
    for (final asset in assets) {
      if (asset.name == 'PicQuery-$version$suffix' &&
          asset.size > 0 &&
          asset.url.scheme == 'https' &&
          asset.url.host == 'github.com' &&
          asset.url.path.startsWith('/greyovo/PicQuery/releases/download/')) {
        return asset;
      }
    }
    return null;
  }
}

/// Network, persistent package verification and OS installer handoff.
class UpdateService {
  UpdateService({
    HttpClient? client,
    Future<Directory> Function()? directory,
    Uri? releaseEndpoint,
  }) : _client = client ?? HttpClient(),
       _directory =
           directory ??
           (() async => Directory(
             p.join((await getApplicationSupportDirectory()).path, 'updates'),
           )),
       _releaseEndpoint =
           releaseEndpoint ??
           Uri.https(
             'api.github.com',
             '/repos/greyovo/PicQuery/releases/latest',
           ) {
    _client.connectionTimeout = const Duration(seconds: 20);
  }
  final HttpClient _client;
  final Future<Directory> Function() _directory;
  final Uri _releaseEndpoint;
  static const _idleTimeout = Duration(seconds: 30);
  static const _channel = MethodChannel('picquery/updates');

  Future<Version> installedVersion() async =>
      parseReleaseVersion((await PackageInfo.fromPlatform()).version);

  Future<String> architecture() async {
    String raw;
    if (Platform.isAndroid) {
      raw = await _channel.invokeMethod<String>('architecture') ?? '';
    } else if (Platform.isWindows) {
      raw =
          Platform.environment['PROCESSOR_ARCHITEW6432'] ??
          Platform.environment['PROCESSOR_ARCHITECTURE'] ??
          '';
    } else if (Platform.isMacOS || Platform.isLinux) {
      final result = await Process.run('/usr/bin/uname', ['-m']);
      if (result.exitCode != 0) return 'unknown';
      raw = result.stdout.toString().trim();
    } else {
      return 'unknown';
    }
    return switch (raw.toLowerCase()) {
      'arm64' || 'arm64-v8a' || 'aarch64' => 'arm64',
      'x86_64' || 'amd64' || 'x64' => 'x64',
      _ => 'unknown',
    };
  }

  Future<HttpClientResponse> _get(Uri uri) async {
    final request = await _client.getUrl(uri).timeout(_idleTimeout);
    request.headers.set(HttpHeaders.userAgentHeader, 'PicQuery-Updater');
    if (uri == _releaseEndpoint) {
      request.headers.set(
        HttpHeaders.acceptHeader,
        'application/vnd.github+json',
      );
      request.headers.set('X-GitHub-Api-Version', '2022-11-28');
    }
    final response = await request.close().timeout(_idleTimeout);
    if (response.statusCode != HttpStatus.ok) {
      await response.drain<void>().timeout(_idleTimeout);
      throw HttpException('GitHub HTTP ${response.statusCode}', uri: uri);
    }
    return response;
  }

  Future<AppRelease?> latestRelease() async {
    final response = await _get(_releaseEndpoint);
    final body = await utf8.decoder.bind(response.timeout(_idleTimeout)).join();
    final json = jsonDecode(body) as Map<String, dynamic>;
    if (json['draft'] == true || json['prerelease'] == true) return null;
    final release = AppRelease.fromJson(json);
    return release.version.isPreRelease ? null : release;
  }

  Future<File> _package(UpdateAsset asset) async {
    final directory = await _directory();
    await directory.create(recursive: true);
    // Asset ID binds the cache to a particular upload; names never become paths.
    final extension = asset.name.endsWith('.tar.gz')
        ? '.tar.gz'
        : p.extension(asset.name);
    return File(p.join(directory.path, '${asset.id}$extension'));
  }

  Future<String> _hash(File file) async =>
      (await sha256.bind(file.openRead()).first).toString();

  Future<File?> cachedPackage(UpdateAsset asset) async {
    final file = await _package(asset);
    final receipt = File('${file.path}.json');
    try {
      if (!await file.exists() ||
          !await receipt.exists() ||
          await file.length() != asset.size) {
        return null;
      }
      final metadata = jsonDecode(await receipt.readAsString()) as Map;
      if (metadata['url'] != asset.url.toString() ||
          metadata['size'] != asset.size) {
        return null;
      }
      final hash = await _hash(file);
      if (hash != metadata['sha256'] ||
          (asset.digest != null && asset.digest != 'sha256:$hash')) {
        return null;
      }
      return file;
    } on FileSystemException {
      return null;
    } on FormatException {
      return null;
    } on TypeError {
      return null;
    }
  }

  Future<File> download(
    UpdateAsset asset,
    void Function(double) onProgress,
  ) async {
    final cached = await cachedPackage(asset);
    if (cached != null) return cached;
    final file = await _package(asset);
    final partial = File('${file.path}.part');
    final receipt = File('${file.path}.json');
    if (await receipt.exists()) await receipt.delete();
    final response = await _get(asset.url);
    final sink = partial.openWrite(); // Truncates any interrupted download.
    var received = 0;
    try {
      await sink.addStream(
        response.timeout(_idleTimeout).map((bytes) {
          received += bytes.length;
          if (received > asset.size) {
            throw const FormatException('Package exceeds release size');
          }
          onProgress(received / asset.size);
          return bytes;
        }),
      );
      await sink.flush();
    } finally {
      await sink.close();
    }
    if (received != asset.size) {
      throw const FormatException('Incomplete update download');
    }
    final hash = await _hash(partial);
    if (asset.digest != null && asset.digest != 'sha256:$hash') {
      throw const FormatException('Update checksum mismatch');
    }
    if (await file.exists()) await file.delete();
    await partial.rename(file.path);
    await receipt.writeAsString(
      jsonEncode({
        'url': asset.url.toString(),
        'size': asset.size,
        'sha256': hash,
      }),
      flush: true,
    );
    return file;
  }

  /// Returns false when Android first needs unknown-source permission.
  Future<bool> install(UpdateAsset asset) async {
    final file = await cachedPackage(asset);
    if (file == null) {
      throw const FormatException('Update package is no longer valid');
    }
    if (Platform.isAndroid) {
      return await _channel.invokeMethod<bool>('install', {
            'path': file.path,
          }) ??
          false;
    }
    if (Platform.isWindows) {
      await Process.start(file.path, [], mode: ProcessStartMode.detached);
      return true;
    }
    final result = await Process.run(
      Platform.isMacOS ? '/usr/bin/open' : 'xdg-open',
      [file.path],
    );
    if (result.exitCode != 0) {
      throw ProcessException('installer', [], result.stderr.toString());
    }
    return true;
  }

  Future<void> openRelease(AppRelease release) async {
    if (release.page.scheme != 'https' ||
        release.page.host != 'github.com' ||
        !await launchUrl(release.page, mode: LaunchMode.externalApplication)) {
      throw const FormatException('Cannot open release page');
    }
  }

  void dispose() => _client.close(force: true);
}
