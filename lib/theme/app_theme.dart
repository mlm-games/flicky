import 'package:flutter/material.dart';

class AppTheme {
  static const primaryGreen = Color(0xFF00D26A);
  static const surfaceLight = Color(0xFFF8F9FA);
  static const surfaceDark = Color(0xFF1A1D21);
  static const cardLight = Colors.white;
  static const cardDark = Color(0xFF2A2D31);
  
  static ThemeData lightTheme = ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    scaffoldBackgroundColor: surfaceLight,
    colorScheme: ColorScheme.light(
      primary: primaryGreen,
      surface: cardLight,
      background: surfaceLight,
    ),
    cardTheme: CardTheme(
      elevation: 0,
      color: cardLight,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Colors.grey.shade200),
      ),
    ),
  );
  
  static ThemeData darkTheme = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: surfaceDark,
    colorScheme: ColorScheme.dark(
      primary: primaryGreen,
      surface: cardDark,
      background: surfaceDark,
    ),
    cardTheme: CardTheme(
      elevation: 0,
      color: cardDark,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Colors.grey.shade800),
      ),
    ),
  );
}