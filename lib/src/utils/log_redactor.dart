/// Removes filesystem locations before diagnostics reach disk or a console.
/// Package/SDK frame locations remain useful for debugging.
class LogRedactor {
  static final _quotedPath = RegExp(
    r'''(["'])(?:file:/+|[A-Za-z]:[\\/]|\\\\|/|~/|\.{1,2}/)[^\r\n]*?\1''',
  );
  static final _path = RegExp(
    r'''(?:file:/+|[A-Za-z]:[\\/]|\\\\|~/|\.{1,2}/|(?<![\w:/])/(?!/))[^\r\n"'<>\)\],;]*''',
  );

  static String redact(String text) => text
      .replaceAllMapped(_quotedPath, (_) => '[redacted path]')
      .replaceAllMapped(_path, (_) => '[redacted path]');

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
