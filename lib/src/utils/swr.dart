import 'dart:async';

import 'package:flutter/widgets.dart';

/// 类似 SWR 的声明式缓存管理工具
class SWR<T> extends ChangeNotifier {
  // 全局缓存，按 key 复用
  static final Map<String, dynamic> _cache = {};

  final String key;
  final Future<T> Function() _fetcher;
  final Duration? staleTime;
  Timer? _refreshTimer;

  AsyncSnapshot<T> _snapshot = const AsyncSnapshot.nothing();

  T? get data => _snapshot.data;
  bool get isLoading =>
      !_snapshot.hasData &&
      _snapshot.connectionState == ConnectionState.waiting;
  bool get isReloading =>
      _snapshot.hasData && _snapshot.connectionState == ConnectionState.active;
  bool get isError => _snapshot.hasError;
  Object? get error => _snapshot.error;
  StackTrace? get stackTrace => _snapshot.stackTrace;

  SWR._(this.key, Future<T> Function() fetcher, {this.staleTime})
    : _fetcher = fetcher {
    _fetch(); // 创建时自动发起第一次请求
  }

  factory SWR(String key, Future<T> Function() fetcher, {Duration? staleTime}) {
    if (_cache.containsKey(key)) {
      return _cache[key] as SWR<T>;
    }
    final instance = SWR<T>._(key, fetcher, staleTime: staleTime);
    _cache[key] = instance;
    return instance;
  }

  Future<void> _fetch() async {
    if (_snapshot.hasData) {
      // 有旧数据：进入后台刷新状态
      _snapshot = AsyncSnapshot<T>.withData(
        ConnectionState.active,
        _snapshot.data as T,
      );
      notifyListeners();
    } else {
      // 无缓存：进入等待状态
      _snapshot = AsyncSnapshot<T>.waiting();
      notifyListeners();
    }

    try {
      final newData = await _fetcher();
      _snapshot = AsyncSnapshot<T>.withData(ConnectionState.done, newData);
      notifyListeners();
      _scheduleRefresh(); // 成功后安排下次自动刷新
    } catch (error, stackTrace) {
      if (_snapshot.hasData) {
        // 后台刷新失败：回退到旧数据（SWR 风格）
        _snapshot = AsyncSnapshot<T>.withData(
          ConnectionState.done,
          _snapshot.data as T,
        );
      } else {
        // 首次请求失败：进入错误状态
        _snapshot = AsyncSnapshot<T>.withError(
          ConnectionState.done,
          error,
          stackTrace,
        );
      }
      notifyListeners();
    }
  }

  void _scheduleRefresh() {
    _refreshTimer?.cancel();
    if (staleTime != null) {
      _refreshTimer = Timer(staleTime!, _fetch);
    }
  }

  /// 手动重新请求
  Future<void> revalidate() => _fetch();

  /// 乐观更新：立即替换数据，并重置陈旧计时器
  void mutate(T newData) {
    _snapshot = AsyncSnapshot<T>.withData(ConnectionState.done, newData);
    notifyListeners();
    _scheduleRefresh();
  }

  /// 清除指定 key 的缓存
  static void clearCache(String key) {
    final instance = _cache.remove(key);
    if (instance is SWR) {
      instance.dispose();
    }
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _cache.remove(key);
    super.dispose();
  }
}
