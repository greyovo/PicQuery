import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:picquery_app/src/utils/localization.dart';

class PrivacyPolicyPage extends StatefulWidget {
  const PrivacyPolicyPage({super.key});

  @override
  State<PrivacyPolicyPage> createState() => _PrivacyPolicyPageState();
}

class _PrivacyPolicyPageState extends State<PrivacyPolicyPage> {
  late final _policy = rootBundle.loadString('website/docs/privacy-policy.md');

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(context.l10n.privacyPolicy)),
    body: FutureBuilder<String>(
      future: _policy,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return Center(child: Text(context.l10n.privacyPolicyLoadFailed));
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final sections = snapshot.data!.split('\n---\n');
        final chinese = Localizations.localeOf(context).languageCode == 'zh';
        final text = (chinese ? sections.last : sections.first).replaceAll(
          RegExp(r'^#{1,6}\s+', multiLine: true),
          '',
        );
        return SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: SelectableText(text, style: const TextStyle(height: 1.6)),
            ),
          ),
        );
      },
    ),
  );
}
