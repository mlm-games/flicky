import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';

import 'mobile/mobile_app_detail.dart';
import 'tv/tv_app_detail.dart';

class AppDetailScreen extends StatelessWidget {
  final FDroidApp app;

  const AppDetailScreen({Key? key, required this.app}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.deviceType == DeviceType.mobile ||
            deviceInfo.deviceType == DeviceType.tablet) {
          return MobileAppDetail(app: app);
        } else {
          return TVAppDetail(app: app);
        }
      },
    );
  }
}
