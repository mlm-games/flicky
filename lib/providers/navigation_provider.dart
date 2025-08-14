import 'package:flicky/screens/browse/browse_screen.dart';
import 'package:flicky/screens/categories/categories_screen.dart';
import 'package:flicky/screens/settings/settings_screen.dart';
import 'package:flicky/screens/updates/updates_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final navigationIndexProvider = StateNotifierProvider<NavigationNotifier, int>(
  (ref) => NavigationNotifier(),
);

class NavigationNotifier extends StateNotifier<int> {
  NavigationNotifier() : super(0);

  void setIndex(int index) {
    state = index;
  }

  void goToBrowse() => state = 0;
  void goToCategories() => state = 1;
  void goToUpdates() => state = 2;
  void goToSettings() => state = 3;
}

final navigationScreensProvider = Provider<List<Widget>>((ref) {
  return const [
    BrowseScreen(),
    CategoriesScreen(),
    UpdatesScreen(),
    SettingsScreen(),
  ];
});

// Page controller provider for syncing page views
final pageControllerProvider = Provider<PageController>((ref) {
  return PageController();
});
