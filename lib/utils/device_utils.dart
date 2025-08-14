import 'package:flutter/material.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'dart:io';

class DeviceUtils {
  static bool? _isTV;

  static Future<bool> isTV() async {
    if (_isTV != null) return _isTV!;

    if (!Platform.isAndroid) {
      _isTV = false;
      return false;
    }

    try {
      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;

      _isTV =
          androidInfo.systemFeatures.contains('android.software.leanback') ||
          androidInfo.systemFeatures.contains(
            'android.hardware.type.television',
          ) ||
          androidInfo.model.toLowerCase().contains('tv') ||
          androidInfo.manufacturer.toLowerCase().contains('tv');

      return _isTV!;
    } catch (e) {
      _isTV = false;
      return false;
    }
  }

  static bool isMobile(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return size.shortestSide < 600;
  }
}
