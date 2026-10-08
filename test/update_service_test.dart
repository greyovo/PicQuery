import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/services/update_service.dart';
import 'package:pub_semver/pub_semver.dart';

void main() {
  test('release precedence handles numeric segments, prereleases and build metadata', () {
    expect(
      isNewerRelease(parseReleaseVersion('v2.10.0'), Version.parse('2.9.9')),
      isTrue,
    );
    expect(
      isNewerRelease(Version.parse('2.0.0'), Version.parse('2.0.0-beta.1')),
      isTrue,
    );
    expect(
      isNewerRelease(Version.parse('2.0.0+123'), Version.parse('2.0.0')),
      isFalse,
    );
    expect(
      isNewerRelease(Version.parse('1.9.0'), Version.parse('2.0.0')),
      isFalse,
    );
    expect(() => parseReleaseVersion('not-a-version'), throwsFormatException);
  });

  test('selects only compatible official installers, not source archives or other architectures', () {
    final version = Version.parse('2.1.0');
    final assets =
        [
              'android-arm64.apk',
              'macos.dmg',
              'windows-x64-setup.exe',
              'linux-x64.tar.gz',
              'android.aab',
            ].indexed
            .map(
              (entry) => UpdateAsset(
                id: entry.$1,
                name: 'PicQuery-2.1.0-${entry.$2}',
                size: 10,
                url: Uri.parse(
                  'https://github.com/greyovo/PicQuery/releases/download/v2.1.0/PicQuery-2.1.0-${entry.$2}',
                ),
              ),
            )
            .toList();
    final release = AppRelease(
      version: version,
      notes: '',
      page: Uri.parse('https://github.com'),
      assets: assets,
    );
    expect(release.assetFor('android', 'arm64')!.name, endsWith('.apk'));
    expect(release.assetFor('macos', 'arm64')!.name, endsWith('.dmg'));
    expect(release.assetFor('windows', 'x64')!.name, endsWith('.exe'));
    expect(release.assetFor('linux', 'x64')!.name, endsWith('.tar.gz'));
    expect(release.assetFor('macos', 'x64'), isNull);
    expect(release.assetFor('android', 'x64'), isNull);
    expect(release.assetFor('windows', 'arm64'), isNull);
    expect(release.assetFor('ios', 'arm64'), isNull);
    final malicious = AppRelease(
      version: version,
      notes: '',
      page: release.page,
      assets: [
        UpdateAsset(
          id: 100,
          name: 'PicQuery-2.1.0-android-arm64.apk',
          size: 10,
          url: Uri.parse('https://example.com/app.apk'),
        ),
      ],
    );
    expect(malicious.assetFor('android', 'arm64'), isNull);
  });

  group('release API and cache', () {
    late HttpServer server;
    late Directory directory;
    late UpdateService service;
    late Uri base;
    var requests = 0;
    var payload = <int>[];
    var status = 200;
    var releaseJson = <String, Object?>{};

    setUp(() async {
      server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      directory = await Directory.systemTemp.createTemp(
        'picquery-update-test-',
      );
      base = Uri.parse('http://127.0.0.1:${server.port}');
      requests = 0;
      status = 200;
      payload = utf8.encode('verified package bytes');
      releaseJson = {
        'tag_name': 'v2.1.0',
        'draft': false,
        'prerelease': false,
        'body': 'Release notes',
        'html_url': 'https://github.com/greyovo/PicQuery/releases/tag/v2.1.0',
        'assets': [],
      };
      server.listen((request) async {
        requests++;
        request.response.statusCode = status;
        request.response.add(
          request.uri.path == '/latest'
              ? utf8.encode(jsonEncode(releaseJson))
              : payload,
        );
        await request.response.close();
      });
      service = UpdateService(
        directory: () async => directory,
        releaseEndpoint: base.resolve('/latest'),
      );
    });
    tearDown(() async {
      service.dispose();
      await server.close(force: true);
      await directory.delete(recursive: true);
    });

    UpdateAsset asset({int? size, String? digest}) => UpdateAsset(
      id: 42,
      name: 'PicQuery-2.1.0-android-arm64.apk',
      url: base.resolve('/package'),
      size: size ?? payload.length,
      digest: digest,
    );

    test(
      'reads stable metadata and ignores draft/prerelease releases',
      () async {
        expect((await service.latestRelease())!.version.toString(), '2.1.0');
        releaseJson['prerelease'] = true;
        expect(await service.latestRelease(), isNull);
        releaseJson['prerelease'] = false;
        releaseJson['draft'] = true;
        expect(await service.latestRelease(), isNull);
        releaseJson['draft'] = false;
        releaseJson['tag_name'] = 'v2.1.0-beta.1';
        expect(await service.latestRelease(), isNull);
      },
    );

    test('HTTP failures surface to callers', () async {
      status = 403;
      await expectLater(service.latestRelease(), throwsA(isA<HttpException>()));
    });

    test(
      'complete download verifies digest, reports progress and reuses cache',
      () async {
        final selected = asset(digest: 'sha256:${sha256.convert(payload)}');
        final progress = <double>[];
        final file = await service.download(selected, progress.add);
        expect(await file.readAsBytes(), payload);
        expect(progress.last, 1);
        expect((await service.cachedPackage(selected))!.path, file.path);
        await service.download(
          selected,
          (_) => fail('cache must not download'),
        );
        expect(requests, 1);
      },
    );

    test(
      'partial download is discarded and restarted, without appending',
      () async {
        final partial = File('${directory.path}/42.apk.part');
        await partial.writeAsString('old incomplete data');
        final file = await service.download(asset(), (_) {});
        expect(await file.readAsBytes(), payload);
        expect(await partial.exists(), isFalse);
      },
    );

    test(
      'interruption and checksum mismatch never create an installable cache',
      () async {
        await expectLater(
          service.download(asset(size: payload.length + 5), (_) {}),
          throwsFormatException,
        );
        expect(await service.cachedPackage(asset()), isNull);
        await expectLater(
          service.download(asset(digest: 'sha256:incorrect'), (_) {}),
          throwsFormatException,
        );
        expect(await File('${directory.path}/42.apk').exists(), isFalse);
        final file = await service.download(asset(), (_) {});
        expect(await file.readAsBytes(), payload);
      },
    );

    test(
      'same-size corruption or missing receipt forces a new download',
      () async {
        final selected = asset();
        final file = await service.download(selected, (_) {});
        await file.writeAsBytes(List.filled(payload.length, 0));
        expect(await service.cachedPackage(selected), isNull);
        await service.download(selected, (_) {});
        expect(requests, 2);
        await File('${file.path}.json').delete();
        await service.download(selected, (_) {});
        expect(requests, 3);
      },
    );

    test('invalid cached package cannot reach an installer', () async {
      await expectLater(service.install(asset()), throwsFormatException);
    });
  });
}
