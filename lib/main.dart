import 'package:flicky/utils/device_utils.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'theme/app_theme.dart';
import 'providers/app_providers.dart';
import 'screens/main_screen.dart';
import 'services/cache_service.dart';
import 'services/package_info_service.dart';
import 'services/repository_sync_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  await CacheService.init();
  await PackageInfoService.init();
  await RepositorySyncService.init();

  final isTV = await DeviceUtils.isTV();
  if (isTV) {
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  } else {
    // Mobile/Tablet - all orientations
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  }

  runApp(ProviderScope(child: FDroidTV()));
}

class FDroidTV extends ConsumerWidget {
  const FDroidTV({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);

    return Shortcuts(
      shortcuts: <LogicalKeySet, Intent>{
        //TODO?: Need the ok button shortcut
        LogicalKeySet(LogicalKeyboardKey.select): ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.enter): ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.space): ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.gameButtonA): ActivateIntent(),
      },
      child: MaterialApp(
        title: 'Flicky',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.lightTheme,
        darkTheme: AppTheme.darkTheme,
        themeMode: themeMode,
        home: DefaultTextEditingShortcuts(child: MainScreen()),
      ),
    );
  }
}
