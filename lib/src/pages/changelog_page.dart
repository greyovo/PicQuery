import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:picquery_app/src/utils/localization.dart';

class ChangelogPage extends StatefulWidget {
  const ChangelogPage({super.key});

  @override
  State<ChangelogPage> createState() => _ChangelogPageState();
}

class _ChangelogPageState extends State<ChangelogPage> {
  Future<String>? _changelog;
  String? _assetPath;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final language = Localizations.localeOf(context).languageCode == 'zh'
        ? 'zh'
        : 'en';
    final path = 'assets/CHANGELOG_$language.md';
    if (_assetPath != path) {
      _assetPath = path;
      _changelog = rootBundle.loadString(path);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(context.l10n.changelog)),
    body: FutureBuilder<String>(
      key: ValueKey(_assetPath),
      future: _changelog,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return Center(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(context.l10n.changelogLoadFailed),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => setState(() {
                    _changelog = rootBundle.loadString(_assetPath!);
                  }),
                  child: Text(context.l10n.appUpdateRetry),
                ),
              ],
            ),
          );
        }
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        return SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: MarkdownBody(data: snapshot.data!, selectable: true),
            ),
          ),
        );
      },
    ),
  );
}
