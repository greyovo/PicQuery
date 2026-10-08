import 'package:flutter/material.dart';
import 'package:picquery_app/src/stores/settings_store.dart';
import 'package:picquery_app/src/utils/localization.dart';
import 'package:picquery_app/src/utils/error_reporting.dart';
import 'package:picquery_app/src/pages/privacy_policy_page.dart';

class PrivacyAgreementGate extends StatefulWidget {
  const PrivacyAgreementGate({required this.child, super.key});

  final Widget child;

  @override
  State<PrivacyAgreementGate> createState() => _PrivacyAgreementGateState();
}

class _PrivacyAgreementGateState extends State<PrivacyAgreementGate> {
  late bool _accepted;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _accepted = SettingsStore.getPrivacyAgreementAccepted();
  }

  Future<void> _accept(bool reportingEnabled) async {
    if (_saving) return;
    setState(() => _saving = true);
    await SettingsStore.acceptPrivacyAgreement();
    await ErrorReporting.setAutomaticReporting(reportingEnabled);
    if (mounted) setState(() => _accepted = true);
  }

  @override
  Widget build(BuildContext context) {
    if (_accepted) return widget.child;

    return PopScope(
      canPop: false,
      child: Scaffold(
        body: Center(
          child: AlertDialog(
            title: Text(context.l10n.privacyAgreementTitle),
            content: SingleChildScrollView(
              child: Text(context.l10n.privacyAgreementMessage),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const PrivacyPolicyPage(),
                  ),
                ),
                child: Text(context.l10n.privacyPolicy),
              ),
              TextButton(
                onPressed: _saving ? null : () => _accept(false),
                child: Text(context.l10n.privacyAgreementDecline),
              ),
              FilledButton(
                onPressed: _saving ? null : () => _accept(true),
                child: Text(context.l10n.privacyAgreementAgree),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
