enum SearchMode { text, image }

enum SearchTimeRange {
  anyTime,
  pastWeek,
  pastMonth,
  pastYear;

  int? modifiedAfterSeconds(DateTime now) => switch (this) {
    SearchTimeRange.anyTime => null,
    SearchTimeRange.pastWeek =>
      now.subtract(const Duration(days: 7)).millisecondsSinceEpoch ~/ 1000,
    SearchTimeRange.pastMonth =>
      now.subtract(const Duration(days: 30)).millisecondsSinceEpoch ~/ 1000,
    SearchTimeRange.pastYear =>
      now.subtract(const Duration(days: 365)).millisecondsSinceEpoch ~/ 1000,
  };
}
