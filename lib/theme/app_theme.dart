import 'package:flutter/material.dart';

class AppTheme {
  // Colors
  static const primaryGreen = Color(0xFF00D26A);
  static const primaryGreenDark = Color(0xFF00A653);
  static const accentBlue = Color(0xFF2196F3);
  static const surfaceLight = Color(0xFFF5F7FA);
  static const surfaceDark = Color(0xFF1A1D21);
  static const cardLight = Color(0xFFFFFFFF);
  static const cardDark = Color(0xFF2A2D31);

  static ThemeData lightTheme = ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    scaffoldBackgroundColor: surfaceLight,
    primaryColor: primaryGreen,
    colorScheme: ColorScheme.light(
      primary: primaryGreen,
      secondary: accentBlue,
      surface: cardLight,
      error: Color(0xFFE74C3C),
      onPrimary: Colors.white,
      onSecondary: Colors.white,
      onSurface: Color(0xFF2C3E50),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: cardLight,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      shadowColor: Colors.black.withValues(alpha: 0.05),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: primaryGreen,
        foregroundColor: Colors.white,
        elevation: 0,
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: primaryGreen,
        side: BorderSide(color: primaryGreen),
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: primaryGreen),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      centerTitle: false,
      iconTheme: IconThemeData(color: Color(0xFF2C3E50)),
      titleTextStyle: TextStyle(
        color: Color(0xFF2C3E50),
        fontSize: 20,
        fontWeight: FontWeight.w600,
      ),
    ),
    dividerTheme: DividerThemeData(color: Color(0xFFE0E6ED), thickness: 1),
  );

  static ThemeData darkTheme = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    scaffoldBackgroundColor: surfaceDark,
    primaryColor: primaryGreen,
    colorScheme: ColorScheme.dark(
      primary: primaryGreen,
      secondary: accentBlue,
      surface: cardDark,
      error: Color(0xFFE74C3C),
      onPrimary: Colors.white,
      onSecondary: Colors.white,
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: cardDark,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: primaryGreen,
        foregroundColor: Colors.white,
        elevation: 0,
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: primaryGreen,
        side: BorderSide(color: primaryGreen),
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(foregroundColor: primaryGreen),
    ),
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      centerTitle: false,
    ),
    dividerTheme: DividerThemeData(color: Colors.grey.shade800, thickness: 1),
  );
}
