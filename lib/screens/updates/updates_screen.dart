import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';

import 'mobile/mobile_updates_screen.dart';
import 'tv/tv_updates_screen.dart';

class UpdatesScreen extends StatelessWidget {
  const UpdatesScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.deviceType == DeviceType.mobile ||
            deviceInfo.deviceType == DeviceType.tablet) {
          return const MobileUpdatesScreen();
        } else {
          return const TVUpdatesScreen();
        }
      },
    );
  }
}
