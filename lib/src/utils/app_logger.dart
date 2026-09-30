import 'dart:developer' as developer;

import 'package:logging/logging.dart';

/// Configures the application's logging pipeline.
void configureLogging() {
  Logger.root.level = Level.ALL;
  Logger.root.onRecord.listen((record) {
    developer.log(
      record.message,
      name: record.loggerName,
      level: record.level.value,
      time: record.time,
      sequenceNumber: record.sequenceNumber,
    );
  });
}
