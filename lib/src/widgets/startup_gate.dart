import 'package:flutter/material.dart';
import 'package:logging/logging.dart';
import 'package:picquery_app/src/utils/app_startup.dart';
import 'package:picquery_app/src/utils/localization.dart';

final _log = Logger('startup');

/// Paint a useful first frame before preparing resources. The app shell is only
/// mounted once the database and both models are ready.
class StartupGate extends StatefulWidget {
  const StartupGate({required this.initialize, required this.child, super.key});

  final AppInitializer initialize;
  final Widget child;

  @override
  State<StartupGate> createState() => _StartupGateState();
}

class _StartupGateState extends State<StartupGate> {
  StartupProgress _progress = const StartupProgress(StartupStage.database);
  bool _ready = false;
  bool _running = false;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _prepare();
  }

  Future<void> _prepare() async {
    if (_running) return;
    _running = true;
    setState(() => _failed = false);
    // Finish the first frame before starting expensive resource preparation.
    await WidgetsBinding.instance.endOfFrame;
    if (!mounted) return;
    try {
      await widget.initialize((progress) {
        if (mounted) setState(() => _progress = progress);
      });
      if (mounted) setState(() => _ready = true);
    } catch (error, stackTrace) {
      _log.severe('Application preparation failed.', error, stackTrace);
      if (mounted) setState(() => _failed = true);
    } finally {
      _running = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_ready) return widget.child;
    final l10n = context.l10n;
    final status = switch (_progress.stage) {
      StartupStage.database => l10n.startupDatabase,
      StartupStage.clipAssets => l10n.startupClipAssets,
      StartupStage.clipLoading => l10n.startupClipLoading,
      StartupStage.translationAssets => l10n.startupTranslationAssets,
      StartupStage.translationLoading => l10n.startupTranslationLoading,
    };
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 360),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    Icons.photo_library_outlined,
                    size: 64,
                    color: Theme.of(context).colorScheme.primary,
                  ),
                  const SizedBox(height: 20),
                  Text(
                    'PicQuery',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    _failed ? l10n.startupFailed : l10n.startupTitle,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    _failed ? l10n.startupFailedMessage : l10n.startupMessage,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  if (_failed)
                    FilledButton.icon(
                      onPressed: _prepare,
                      icon: const Icon(Icons.refresh),
                      label: Text(l10n.startupRetry),
                    )
                  else ...[
                    const LinearProgressIndicator(),
                    const SizedBox(height: 12),
                    Semantics(
                      liveRegion: true,
                      child: Text(status, textAlign: TextAlign.center),
                    ),
                    if (_progress.total > 0)
                      Text('${_progress.completed} / ${_progress.total}'),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
