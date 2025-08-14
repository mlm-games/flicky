import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'adaptive_app_card.dart';

class AdaptiveAppGrid extends ConsumerWidget {
  final DeviceInfo deviceInfo;

  const AdaptiveAppGrid({Key? key, required this.deviceInfo}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(sortedAppsProvider);

    return apps.when(
      data: (appList) {
        if (appList.isEmpty) {
          return SliverToBoxAdapter(
            child: Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: const [
                  Icon(Icons.search_off, size: 64, color: Colors.grey),
                  SizedBox(height: 16),
                  Text(
                    'No apps found',
                    style: TextStyle(fontSize: 18, color: Colors.grey),
                  ),
                ],
              ),
            ),
          );
        }

        return SliverGrid(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: _getGridCrossAxisCount(deviceInfo),
            childAspectRatio: _getAspectRatio(deviceInfo),
            crossAxisSpacing: deviceInfo.deviceType == DeviceType.mobile
                ? 12
                : 16,
            mainAxisSpacing: deviceInfo.deviceType == DeviceType.mobile
                ? 12
                : 16,
          ),
          delegate: SliverChildBuilderDelegate(
            (context, index) => AdaptiveAppCard(
              app: appList[index],
              autofocus: index == 0 && deviceInfo.isTV,
            ),
            childCount: appList.length,
          ),
        );
      },
      loading: () => const SliverToBoxAdapter(
        child: Center(child: CircularProgressIndicator()),
      ),
      error: (error, stack) =>
          SliverToBoxAdapter(child: Center(child: Text('Error: $error'))),
    );
  }

  int _getGridCrossAxisCount(DeviceInfo deviceInfo) {
    final width = deviceInfo.size.width;

    if (deviceInfo.deviceType == DeviceType.mobile) {
      if (deviceInfo.orientation == Orientation.portrait) {
        return width > 600 ? 3 : 2;
      } else {
        return width > 900 ? 4 : 3;
      }
    }

    // TV/Desktop
    if (width > 1400) return 6;
    if (width > 1200) return 5;
    if (width > 900) return 4;
    if (width > 600) return 3;
    return 2;
  }

  double _getAspectRatio(DeviceInfo deviceInfo) {
    if (deviceInfo.deviceType == DeviceType.mobile) {
      return 0.75; // Taller cards for mobile
    }
    return 0.8; // Slightly wider for TV
  }
}
