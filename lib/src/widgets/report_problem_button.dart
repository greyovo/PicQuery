import 'package:flutter/material.dart';
import 'package:picquery_app/src/utils/error_reporting.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/utils/toast_helper.dart';

class ReportProblemButton extends StatefulWidget {
  const ReportProblemButton({
    super.key,
    required this.source,
    this.error,
    this.diagnostics = const {},
  });

  final String source;
  final Object? error;
  final Map<String, dynamic> diagnostics;

  @override
  State<ReportProblemButton> createState() => _ReportProblemButtonState();
}

class _ReportProblemButtonState extends State<ReportProblemButton> {
  bool _sending = false;
  bool _submitted = false;

  Future<void> _report() async {
    setState(() => _sending = true);
    final result = await ErrorReporting.reportProblem(
      source: widget.source,
      error: widget.error,
      diagnostics: widget.diagnostics,
    );
    if (!mounted) return;
    setState(() {
      _sending = false;
      _submitted = result == ReportResult.submitted;
    });
    Toast.showMessage(switch (result) {
      ReportResult.submitted => context.l10n.errorReportSubmitted,
      ReportResult.unavailable => context.l10n.errorReportingUnavailable,
      ReportResult.failed => context.l10n.errorReportFailed,
    });
  }

  @override
  Widget build(BuildContext context) => TextButton.icon(
    onPressed: _sending || _submitted ? null : _report,
    icon: _sending
        ? const SizedBox.square(
            dimension: 16,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : Icon(_submitted ? Icons.check : Icons.bug_report_outlined),
    label: Text(
      _submitted
          ? context.l10n.errorReportSubmitted
          : context.l10n.reportProblemPrompt,
    ),
  );
}
