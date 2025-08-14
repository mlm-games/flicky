import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/providers/navigation_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TabletLayout extends ConsumerStatefulWidget {
  final DeviceInfo deviceInfo;

  const TabletLayout({Key? key, required this.deviceInfo}) : super(key: key);

  @override
  ConsumerState<TabletLayout> createState() => _TabletLayoutState();
}

class _TabletLayoutState extends ConsumerState<TabletLayout> {
  late PageController _pageController;

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final currentIndex = ref.watch(navigationIndexProvider);
    final screens = ref.watch(navigationScreensProvider);

    return Scaffold(
      body: Row(
        children: [
          NavigationRail(
            extended: widget.deviceInfo.size.width > 900,
            selectedIndex: currentIndex,
            onDestinationSelected: (index) {
              ref.read(navigationIndexProvider.notifier).setIndex(index);
              _pageController.jumpToPage(index);
            },
            labelType: widget.deviceInfo.size.width > 900
                ? NavigationRailLabelType.none
                : NavigationRailLabelType.selected,
            destinations: const [
              NavigationRailDestination(
                icon: Icon(Icons.explore_outlined),
                selectedIcon: Icon(Icons.explore),
                label: Text('Browse'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.category_outlined),
                selectedIcon: Icon(Icons.category),
                label: Text('Categories'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.update_outlined),
                selectedIcon: Icon(Icons.update),
                label: Text('Updates'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.settings_outlined),
                selectedIcon: Icon(Icons.settings),
                label: Text('Settings'),
              ),
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(
            child: PageView(
              controller: _pageController,
              physics: const NeverScrollableScrollPhysics(),
              children: screens,
            ),
          ),
        ],
      ),
    );
  }
}
