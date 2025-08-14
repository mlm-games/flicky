import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'layouts/mobile_layout.dart';
import 'layouts/tablet_layout.dart';
import 'layouts/tv_layout.dart';

class MainScreen extends ConsumerWidget {
  const MainScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        // Clean separation - no mixed logic
        switch (deviceInfo.deviceType) {
          case DeviceType.mobile:
            return MobileLayout(deviceInfo: deviceInfo);
          case DeviceType.tablet:
            return TabletLayout(deviceInfo: deviceInfo);
          case DeviceType.tv:
          case DeviceType.desktop:
            return TVLayout(deviceInfo: deviceInfo);
        }
      },
    );
  }
}
