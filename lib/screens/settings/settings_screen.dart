import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';

import 'mobile/mobile_settings.dart';
import 'tv/tv_settings.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.deviceType == DeviceType.mobile ||
            deviceInfo.deviceType == DeviceType.tablet) {
          return const MobileSettings();
        } else {
          return const TVSettings();
        }
      },
    );
  }
}
