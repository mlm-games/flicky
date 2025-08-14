import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'theme/app_theme.dart';
import 'screens/main/main_screen.dart';
import 'services/cache_service.dart';
import 'services/package_info_service.dart';
import 'services/repository_sync_service.dart';
import 'utils/platform_utils.dart';
import 'providers/app_providers.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize services
  await Hive.initFlutter();
  await CacheService.init();
  await PackageInfoService.init();
  await RepositorySyncService.init();
  await PlatformUtils.init();

  // Set orientation based on device type
  if (PlatformUtils.isTV) {
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
  }

  runApp(const ProviderScope(child: FlickyApp()));
}

class FlickyApp extends ConsumerWidget {
  const FlickyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);

    return MaterialApp(
      title: 'Flicky',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeMode,
      home: const MainScreen(),
      builder: (context, child) {
        // Add TV-specific shortcuts if needed
        if (PlatformUtils.isTV) {
          return Shortcuts(
            shortcuts: <LogicalKeySet, Intent>{
              LogicalKeySet(LogicalKeyboardKey.select): const ActivateIntent(),
              LogicalKeySet(LogicalKeyboardKey.enter): const ActivateIntent(),
              LogicalKeySet(LogicalKeyboardKey.space): const ActivateIntent(),
              LogicalKeySet(LogicalKeyboardKey.gameButtonA):
                  const ActivateIntent(),
            },
            child: child!,
          );
        }
        return child!;
      },
    );
  }
}
