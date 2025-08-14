import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';

import 'mobile/mobile_browse_screen.dart';
import 'tv/tv_browse_screen.dart';

class BrowseScreen extends StatelessWidget {
  const BrowseScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.deviceType == DeviceType.mobile ||
            deviceInfo.deviceType == DeviceType.tablet) {
          return MobileBrowseScreen(deviceInfo: deviceInfo);
        } else {
          return TVBrowseScreen(deviceInfo: deviceInfo);
        }
      },
    );
  }
}
