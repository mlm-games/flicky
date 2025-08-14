import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../widgets/navigation_sidebar.dart';
import '../utils/device_utils.dart';
import 'browse_screen.dart';
import 'categories_screen.dart';
import 'updates_screen.dart';
import 'settings_screen.dart';

class MainScreen extends ConsumerStatefulWidget {
  @override
  _MainScreenState createState() => _MainScreenState();
}

class _MainScreenState extends ConsumerState<MainScreen> {
  int selectedIndex = 0;
  final PageController pageController = PageController();

  @override
  Widget build(BuildContext context) {
    final isMobile = DeviceUtils.isMobile(context);
    final isPortrait =
        MediaQuery.of(context).orientation == Orientation.portrait;
    final showBottomNav = isMobile && isPortrait;

    final pages = [
      BrowseScreen(),
      CategoriesScreen(),
      UpdatesScreen(),
      SettingsScreen(),
    ];

    if (showBottomNav) {
      // Mobile portrait - use bottom navigation
      return Scaffold(
        body: PageView(
          controller: pageController,
          physics: NeverScrollableScrollPhysics(),
          children: pages,
        ),
        bottomNavigationBar: NavigationBar(
          selectedIndex: selectedIndex,
          onDestinationSelected: (index) {
            setState(() => selectedIndex = index);
            pageController.animateToPage(
              index,
              duration: Duration(milliseconds: 300),
              curve: Curves.easeOutCubic,
            );
          },
          destinations: [
            NavigationDestination(icon: Icon(Icons.explore), label: 'Browse'),
            NavigationDestination(
              icon: Icon(Icons.category),
              label: 'Categories',
            ),
            NavigationDestination(icon: Icon(Icons.update), label: 'Updates'),
            NavigationDestination(
              icon: Icon(Icons.settings),
              label: 'Settings',
            ),
          ],
        ),
      );
    } else {
      // TV, desktop, or mobile landscape - use sidebar
      return Scaffold(
        body: Row(
          children: [
            // Sidebar
            NavigationSidebar(
              selectedIndex: selectedIndex,
              onIndexChanged: (index) {
                setState(() => selectedIndex = index);
                pageController.animateToPage(
                  index,
                  duration: Duration(milliseconds: 300),
                  curve: Curves.easeOutCubic,
                );
              },
            ),

            // Main content
            Expanded(
              child: PageView(
                controller: pageController,
                physics: NeverScrollableScrollPhysics(),
                children: pages,
              ),
            ),
          ],
        ),
      );
    }
  }
}
