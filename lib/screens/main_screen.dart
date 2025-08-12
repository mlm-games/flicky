import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class MainScreen extends ConsumerStatefulWidget {
  @override
  _MainScreenState createState() => _MainScreenState();
}

class _MainScreenState extends ConsumerState<MainScreen> {
  int selectedIndex = 0;
  final PageController pageController = PageController();
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: [
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
          
          // Main content area
          Expanded(
            child: PageView(
              controller: pageController,
              physics: NeverScrollableScrollPhysics(),
              children: [
                BrowseScreen(),
                CategoriesScreen(),
                UpdatesScreen(),
                SettingsScreen(),
              ],
            ),
          ),
        ],
      ),
    );
  }
}