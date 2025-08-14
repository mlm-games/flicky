import 'package:flicky/core/models/fdroid_app.dart';
import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/widgets/mobile/mobile_app_card.dart';
import 'package:flicky/widgets/tv/tv_app_card.dart';
import 'package:flutter/material.dart';

class AdaptiveAppCard extends StatelessWidget {
  final FDroidApp app;
  final bool autofocus;

  const AdaptiveAppCard({Key? key, required this.app, this.autofocus = false})
    : super(key: key);

  @override
  Widget build(BuildContext context) {
    return ResponsiveBuilder(
      builder: (context, deviceInfo) {
        if (deviceInfo.isTV || !deviceInfo.hasTouch) {
          return TVAppCard(app: app, autofocus: autofocus);
        } else {
          return MobileAppCard(app: app);
        }
      },
    );
  }
}
