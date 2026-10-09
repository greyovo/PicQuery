// Run: dart ci/extract_release_notes.dart <tag> <output>
import 'dart:convert';
import 'dart:io';

String extractReleaseNotes(String text, String tag) {
  final headings = RegExp(
    r'^## ([^\r\n]+)(?=\r?$)',
    multiLine: true,
  ).allMatches(text).toList();
  final matches = [
    for (var i = 0; i < headings.length; i++)
      if (headings[i].group(1) == tag) i,
  ];
  if (matches.length != 1) {
    throw FormatException('Expected exactly one changelog section for $tag');
  }
  final index = matches.single;
  final end = index + 1 < headings.length
      ? headings[index + 1].start
      : text.length;
  final section = text.substring(headings[index].end, end);
  final separator = RegExp(r'^---\r?$', multiLine: true).firstMatch(section);
  if (separator == null || section.substring(separator.end).trim().isNotEmpty) {
    throw FormatException('Changelog section for $tag must end with ---');
  }
  final body = section.substring(0, separator.start);
  if (body.trim().isEmpty) {
    throw FormatException('Changelog section for $tag is empty');
  }
  return body;
}

void main(List<String> args) {
  if (args.length != 2) {
    stderr.writeln('Usage: dart ci/extract_release_notes.dart <tag> <output>');
    exitCode = 1;
    return;
  }
  try {
    final notes = StringBuffer();
    for (final language in ['en', 'zh']) {
      final source = File('assets/CHANGELOG_$language.md');
      late String body;
      try {
        body = extractReleaseNotes(source.readAsStringSync(), args[0]);
      } on FormatException catch (error) {
        throw FormatException('${source.path}: ${error.message}');
      }
      if (language == 'zh') notes.write('---');
      notes.write(body);
    }
    // Write bytes to preserve the source's line endings on every platform.
    File(args[1]).writeAsBytesSync(utf8.encode(notes.toString()));
  } on FormatException catch (error) {
    stderr.writeln(error.message);
    exitCode = 1;
  } on FileSystemException catch (error) {
    stderr.writeln(error);
    exitCode = 1;
  }
}
