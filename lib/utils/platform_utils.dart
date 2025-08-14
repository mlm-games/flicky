import 'package:flutter/material.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'dart:io';

class PlatformUtils {
  static bool? _isTV;
  static bool? _hasTouch;
  static DeviceInfoPlugin? _deviceInfo;

  static Future<void> init() async {
    _deviceInfo = DeviceInfoPlugin();
    _isTV = await checkIsTV();
    _hasTouch = await checkHasTouch();
  }

  static Future<bool> checkIsTV() async {
    if (!Platform.isAndroid) return false;

    try {
      final androidInfo = await _deviceInfo!.androidInfo;

      return androidInfo.systemFeatures.contains('android.software.leanback') ||
          androidInfo.systemFeatures.contains(
            'android.hardware.type.television',
          ) ||
          androidInfo.model.toLowerCase().contains('tv') ||
          androidInfo.manufacturer.toLowerCase().contains('tv') ||
          androidInfo.model.toLowerCase().contains('firestick') ||
          androidInfo.model.toLowerCase().contains('shield');
    } catch (e) {
      return false;
    }
  }

  static Future<bool> checkHasTouch() async {
    if (!Platform.isAndroid) return true;

    try {
      final androidInfo = await _deviceInfo!.androidInfo;
      return androidInfo.systemFeatures.contains(
        'android.hardware.touchscreen',
      );
    } catch (e) {
      return true;
    }
  }

  static bool get isTV => _isTV ?? false;
  static bool get hasTouch => _hasTouch ?? true;

  static bool isMobileDevice(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return size.shortestSide < 600;
  }

  static bool isTablet(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return size.shortestSide >= 600 && size.shortestSide < 900;
  }

  static bool isDesktop(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return size.shortestSide >= 900;
  }
}
