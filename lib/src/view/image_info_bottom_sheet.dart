import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

class ImageInfoBottomSheet extends StatelessWidget {
  final SearchResult searchResult;

  const ImageInfoBottomSheet({super.key, required this.searchResult});

  Future<String> _loadDimensions() async {
    final buffer = await ui.ImmutableBuffer.fromFilePath(searchResult.filePath);
    try {
      final descriptor = await ui.ImageDescriptor.encoded(buffer);
      try {
        return '${descriptor.width} x ${descriptor.height}';
      } finally {
        descriptor.dispose();
      }
    } finally {
      buffer.dispose();
    }
  }

  Future<String> _loadFileSize() async {
    final stat = await File(searchResult.filePath).stat();
    if (stat.type == FileSystemEntityType.notFound) {
      throw FileSystemException('File not found', searchResult.filePath);
    }
    return _formatFileSize(stat.size);
  }

  Future<String> _loadModifyTime() async {
    final stat = await File(searchResult.filePath).stat();
    if (stat.type == FileSystemEntityType.notFound) {
      throw FileSystemException('File not found', searchResult.filePath);
    }
    return _formatDateTime(stat.modified);
  }

  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String _formatDateTime(DateTime dt) {
    return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')} '
        '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(24, 14, 24, 24),
      decoration: BoxDecoration(
        color: context.colors.surfaceContainerHigh,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                context.l10n.imageDetails,
                style: Theme.of(context).textTheme.titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold),
              ),
              IconButton(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _DetailRow(
            label: context.l10n.fileName,
            value: searchResult.fileName,
          ),
          _DetailRow(
            label: context.l10n.filePath,
            value: searchResult.filePath,
          ),
          _DetailRow(
            label: context.l10n.format,
            value: searchResult.filePath.split('.').last.toUpperCase(),
          ),
          _AsyncDetailRow(
            key: ValueKey('dimensions:${searchResult.filePath}'),
            label: context.l10n.dimensions,
            loadValue: _loadDimensions,
          ),
          _AsyncDetailRow(
            key: ValueKey('fileSize:${searchResult.filePath}'),
            label: context.l10n.fileSize,
            loadValue: _loadFileSize,
          ),
          _AsyncDetailRow(
            key: ValueKey('modified:${searchResult.filePath}'),
            label: context.l10n.modified,
            loadValue: _loadModifyTime,
          ),
          _DetailRow(
            label: context.l10n.similarity,
            value: '${(searchResult.similarity * 100).toStringAsFixed(1)}%',
          ),
        ],
      ),
    );
  }
}

class _AsyncDetailRow extends StatefulWidget {
  final String label;
  final Future<String> Function() loadValue;

  const _AsyncDetailRow({
    super.key,
    required this.label,
    required this.loadValue,
  });

  @override
  State<_AsyncDetailRow> createState() => _AsyncDetailRowState();
}

class _AsyncDetailRowState extends State<_AsyncDetailRow> {
  late final Future<String> _value = widget.loadValue();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String>(
      future: _value,
      builder: (context, snapshot) => _DetailRow(
        label: widget.label,
        value: snapshot.data ?? context.l10n.unknown,
        isLoading: snapshot.connectionState != ConnectionState.done,
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  final bool isLoading;

  const _DetailRow({
    required this.label,
    required this.value,
    this.isLoading = false,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(
              '$label:',
              style: Theme.of(context).textTheme.bodySmall
                  ?.copyWith(color: context.colors.onSurfaceVariant),
            ),
          ),
          Expanded(
            child: isLoading
                ? const Align(
                    alignment: Alignment.centerLeft,
                    child: SizedBox(
                      width: 120,
                      height: 14,
                      child: Center(
                        child: LinearProgressIndicator(minHeight: 2),
                      ),
                    ),
                  )
                : SelectableText(
                    value,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
          ),
        ],
      ),
    );
  }
}
