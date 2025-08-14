import 'package:flicky/utils/platform_utils.dart';
import 'package:flutter/material.dart';

enum DeviceType { mobile, tablet, tv, desktop }

enum ScreenSize { compact, medium, expanded, large }

class DeviceInfo {
  final DeviceType deviceType;
  final ScreenSize screenSize;
  final Orientation orientation;
  final Size size;
  final bool isTV;
  final bool hasTouch;

  DeviceInfo({
    required this.deviceType,
    required this.screenSize,
    required this.orientation,
    required this.size,
    required this.isTV,
    required this.hasTouch,
  });

  factory DeviceInfo.fromContext(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final orientation = MediaQuery.of(context).orientation;
    final width = size.width;

    // Check if it's actually a TV first
    final isTV = PlatformUtils.isTV;
    final hasTouch = PlatformUtils.hasTouch;

    // Determine device type
    DeviceType deviceType;
    ScreenSize screenSize;

    if (isTV) {
      deviceType = DeviceType.tv;
      screenSize = ScreenSize.large;
    } else if (width < 600) {
      deviceType = DeviceType.mobile;
      screenSize = ScreenSize.compact;
    } else if (width < 840) {
      deviceType = DeviceType.tablet;
      screenSize = ScreenSize.medium;
    } else if (width < 1200) {
      deviceType = DeviceType.desktop;
      screenSize = ScreenSize.expanded;
    } else {
      deviceType = DeviceType.desktop;
      screenSize = ScreenSize.large;
    }

    return DeviceInfo(
      deviceType: deviceType,
      screenSize: screenSize,
      orientation: orientation,
      size: size,
      isTV: isTV,
      hasTouch: hasTouch,
    );
  }

  bool get isMobilePortrait =>
      deviceType == DeviceType.mobile && orientation == Orientation.portrait;

  bool get isMobileLandscape =>
      deviceType == DeviceType.mobile && orientation == Orientation.landscape;

  bool get isCompact => screenSize == ScreenSize.compact;
  bool get needsBottomNav => isMobilePortrait;
  bool get needsSidebar => !needsBottomNav;
  bool get needsTVOptimization => isTV || !hasTouch;
}

class ResponsiveBuilder extends StatelessWidget {
  final Widget Function(BuildContext, DeviceInfo) builder;

  const ResponsiveBuilder({Key? key, required this.builder}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final deviceInfo = DeviceInfo.fromContext(context);
        return builder(context, deviceInfo);
      },
    );
  }
}

class ResponsiveValue<T> {
  final T mobile;
  final T? tablet;
  final T? desktop;
  final T? tv;

  const ResponsiveValue({
    required this.mobile,
    this.tablet,
    this.desktop,
    this.tv,
  });

  T get(DeviceInfo info) {
    switch (info.deviceType) {
      case DeviceType.mobile:
        return mobile;
      case DeviceType.tablet:
        return tablet ?? mobile;
      case DeviceType.desktop:
        return desktop ?? tablet ?? mobile;
      case DeviceType.tv:
        return tv ?? desktop ?? tablet ?? mobile;
    }
  }
}
