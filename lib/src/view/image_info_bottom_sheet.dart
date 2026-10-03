import 'dart:io';

import 'package:flutter/material.dart';
import 'package:picquery_app/src/engine/api.dart';
import 'package:picquery_app/src/utils/color_scheme.dart';
import 'package:picquery_app/src/utils/localization.dart';

class ImageDetail {
  final String filePath;
  final String fileName;
  final String format;
  final String dimensions;
  final String fileSize;
  final String modifyTime;
  final double similarity;

  const ImageDetail({
    required this.filePath,
    required this.fileName,
    required this.format,
    required this.dimensions,
    required this.fileSize,
    required this.modifyTime,
    required this.similarity,
  });
}

class ImageInfoBottomSheet extends StatefulWidget {
  final SearchResult searchResult;

  const ImageInfoBottomSheet({super.key, required this.searchResult});

  @override
  State<ImageInfoBottomSheet> createState() => _ImageInfoBottomSheetState();
}

class _ImageInfoBottomSheetState extends State<ImageInfoBottomSheet> {
  ImageDetail? _imageDetail;
  bool _isLoadingDetail = false;
  late String _unknownLabel;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _unknownLabel = context.l10n.unknown;
    if (_imageDetail == null && !_isLoadingDetail) {
      _loadImageDetail();
    }
  }

  Future<void> _loadImageDetail() async {
    if (_isLoadingDetail) return;
    setState(() => _isLoadingDetail = true);

    try {
      final file = File(widget.searchResult.filePath);
      String dimensions = _unknownLabel;
      String fileSize = _unknownLabel;
      String modifyTime = _unknownLabel;

      if (await file.exists()) {
        final stat = await file.stat();
        fileSize = _formatFileSize(stat.size);
        modifyTime = _formatDateTime(stat.modified);

        try {
          final decodedImage = await decodeImageFromList(
            await file.readAsBytes(),
          );
          dimensions = '${decodedImage.width} x ${decodedImage.height}';
        } catch (_) {
          dimensions = _unknownLabel;
        }
      }

      final extension = widget.searchResult.filePath
          .split('.')
          .last
          .toUpperCase();

      final detail = ImageDetail(
        filePath: widget.searchResult.filePath,
        fileName: widget.searchResult.fileName,
        format: extension,
        dimensions: dimensions,
        fileSize: fileSize,
        modifyTime: modifyTime,
        similarity: widget.searchResult.similarity,
      );

      if (mounted) {
        setState(() {
          _imageDetail = detail;
          _isLoadingDetail = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isLoadingDetail = false);
      }
    }
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
          if (_isLoadingDetail || _imageDetail == null)
            const Center(child: CircularProgressIndicator())
          else ...[
            _DetailRow(
              label: context.l10n.fileName,
              value: _imageDetail!.fileName,
            ),
            _DetailRow(
              label: context.l10n.filePath,
              value: _imageDetail!.filePath,
            ),
            _DetailRow(label: context.l10n.format, value: _imageDetail!.format),
            _DetailRow(
              label: context.l10n.dimensions,
              value: _imageDetail!.dimensions,
            ),
            _DetailRow(
              label: context.l10n.fileSize,
              value: _imageDetail!.fileSize,
            ),
            _DetailRow(
              label: context.l10n.modified,
              value: _imageDetail!.modifyTime,
            ),
            _DetailRow(
              label: context.l10n.similarity,
              value: '${(_imageDetail!.similarity * 100).toStringAsFixed(1)}%',
            ),
          ],
        ],
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;

  const _DetailRow({required this.label, required this.value});

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
            child: SelectableText(
              value,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
        ],
      ),
    );
  }
}
