import 'dart:io';

import 'package:flutter/material.dart';
import 'package:open_filex/open_filex.dart';
import 'package:picquery_app/src/utils/app_logger.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';
import 'package:share_plus/share_plus.dart';

class LogsPage extends StatefulWidget {
  const LogsPage({super.key});

  @override
  State<LogsPage> createState() => _LogsPageState();
}

class _LogsPageState extends State<LogsPage> {
  late Future<String> _logs = AppLogger.instance.readAllLogs();
  bool _isExporting = false;

  void _reload() => setState(() => _logs = AppLogger.instance.readAllLogs());

  Future<void> _exportLogs() async {
    if (_isExporting) return;
    setState(() => _isExporting = true);
    try {
      final logger = AppLogger.instance;
      if (Platform.isAndroid || Platform.isIOS) {
        final files = await logger.getLogFiles();
        if (files.isEmpty) {
          if (mounted) Toast.showMessage(context.l10n.noLogs);
          return;
        }
        if (!mounted) return;
        final box = context.findRenderObject() as RenderBox?;
        await Share.shareXFiles(
          files.map((file) => XFile(file.path)).toList(),
          subject: context.l10n.logExportSubject,
          sharePositionOrigin: box == null
              ? null
              : box.localToGlobal(Offset.zero) & box.size,
        );
      } else {
        await logger.flush();
        final result = await OpenFilex.open(logger.logDirectory.path);
        if (result.type != ResultType.done && mounted) {
          Toast.showMessage(context.l10n.exportLogsFailed(result.message));
        }
      }
    } catch (error) {
      if (mounted) Toast.showMessage(context.l10n.exportLogsFailed(error));
    } finally {
      if (mounted) setState(() => _isExporting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.logs),
        actions: [
          IconButton(
            tooltip: context.l10n.refresh,
            onPressed: _reload,
            icon: const Icon(Icons.refresh),
          ),
          IconButton(
            tooltip: context.l10n.exportLogs,
            onPressed: _isExporting ? null : _exportLogs,
            icon: _isExporting
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.ios_share_outlined),
          ),
        ],
      ),
      body: FutureBuilder<String>(
        future: _logs,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Text(context.l10n.loadLogsFailed(snapshot.error!)),
            );
          }
          final logs = snapshot.data ?? '';
          if (logs.isEmpty) return Center(child: Text(context.l10n.noLogs));
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: SelectableText(
              logs,
              style: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 12,
                height: 1.4,
              ),
            ),
          );
        },
      ),
    );
  }
}
