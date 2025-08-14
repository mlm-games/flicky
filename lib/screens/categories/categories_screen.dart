import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flutter/material.dart';

import 'mobile/mobile_categories_screen.dart';
import 'tv/tv_categories_screen.dart';

class CategoriesScreen extends StatelessWidget {
  const CategoriesScreen({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.deviceType == DeviceType.mobile ||
            deviceInfo.deviceType == DeviceType.tablet) {
          return const MobileCategoriesScreen();
        } else {
          return const TVCategoriesScreen();
        }
      },
    );
  }
}
