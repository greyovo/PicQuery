import 'package:flutter_test/flutter_test.dart';
import 'package:picquery_app/src/utils/log_redactor.dart';

void main() {
  test('removes paths including quoted spaces, URI and Windows locations', () {
    const input = '''path=/Users/Alice Smith/My Photos/private.jpg
path=/Users/alice/Pictures/private.jpg
FileSystemException: Cannot open file, path = '/Users/Alice Smith/My Photos/a.jpg'
at main (file:///Users/alice/project/lib/main.dart:10)
path="C:\\Users\\Alice Smith\\Pictures\\a.jpg"
path=C:\\Users\\alice\\Pictures\\a.jpg
path=\\\\server\\private\\a.jpg
path=../private/a.jpg''';
    final output = LogRedactor.redact(input);
    expect(output, isNot(contains('alice')));
    expect(output, isNot(contains('Alice')));
    expect(output, isNot(contains('private')));
    expect(output, isNot(contains('server')));
    expect(output, contains('***'));
    expect(output, contains('.jpg'));
    expect(output, contains('.dart:10'));
  });

  test('logging preserves debug paths and partially masks release paths', () {
    const input = '/Users/alice/photo.jpg';
    expect(LogRedactor.forLogging(input, debugMode: true), input);
    expect(
      LogRedactor.forLogging(input, debugMode: false),
      '/U***s/a***e/p***o.jpg',
    );
    expect(
      LogRedactor.redact('/home/alice/archive.tar.gz'),
      '/h***e/a***e/a***r.gz',
    );
    expect(LogRedactor.redact('/home/alice/folder/'), '/h***e/a***e/f***r/');
    expect(LogRedactor.redact('/a/x.png'), '/***/***.png');
  });

  test('preserves useful source frames and aggregate diagnostics', () {
    const input =
        'package:picquery_app/src/engine/db.dart:42 '
        'dart:async 10/100 (2.5 P/s), hits=8';
    expect(LogRedactor.redact(input), input);
  });

  test('redacts nested diagnostic values', () {
    final output = LogRedactor.redactJson({
      'exception': {'value': 'Failed to open /home/alice/private.jpg'},
      'frames': [
        {'abs_path': 'C:\\Users\\alice\\main.dart'},
      ],
    });
    expect(output.toString(), isNot(contains('alice')));
    expect(
      LogRedactor.redact(LogRedactor.redact(output.toString())),
      output.toString(),
    );
  });
}
