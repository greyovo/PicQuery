import 'package:flutter/foundation.dart';

/// Partially masks filesystem locations in diagnostics.
/// Package/SDK frame locations remain useful for debugging.
class LogRedactor {
  static final _quotedPath = RegExp(
    r'''(["'])(?:file:/+|[A-Za-z]:[\\/]|\\\\|/|~/|\.{1,2}/)[^\r\n]*?\1''',
  );
  static final _path = RegExp(
    r'''(?:file:/+|[A-Za-z]:[\\/]|\\\\|~/|\.{1,2}/|(?<![\w:/])/(?!/))[^\r\n"'<>\)\],;]*''',
  );

  static String forLogging(String text, {bool debugMode = kDebugMode}) =>
      debugMode ? text : redact(text);

  static String redact(String text) => text.replaceAllMapped(
    RegExp('${_quotedPath.pattern}|${_path.pattern}'),
    (match) => _maskPath(match[0]!),
  );

  static String _maskPath(String path) {
    // Preserve quotes, file URI prefixes, drive letters and separators.
    return path.replaceAllMapped(RegExp(r'''[^/\\"']+'''), (match) {
      final segment = match[0]!;
      if (segment == 'file:' ||
          RegExp(r'^[A-Za-z]:$').hasMatch(segment) ||
          segment == '~' ||
          segment == '.' ||
          segment == '..' ||
          segment.contains('***')) {
        return segment;
      }
      final isLast = !path.substring(match.end).contains(RegExp(r'[/\\]'));
      final extension = isLast
          ? RegExp(r'\.[A-Za-z0-9]+(?::\d+(?::\d+)?)?$').firstMatch(segment)
          : null;
      final suffix = extension?.group(0) ?? '';
      final name = segment.substring(0, segment.length - suffix.length);
      final masked = name.length <= 2
          ? '***'
          : '${name[0]}***${name[name.length - 1]}';
      return '$masked$suffix';
    });
  }

  static dynamic redactJson(dynamic value) {
    if (value is String) return redact(value);
    if (value is List) return value.map(redactJson).toList();
    if (value is Map) {
      return value.map(
        (key, value) => MapEntry(redact(key.toString()), redactJson(value)),
      );
    }
    return value;
  }
}
