import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/providers/navigation_provider.dart';
import 'package:flicky/widgets/tv/tv_navigation_sidebar.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TVLayout extends ConsumerStatefulWidget {
  final DeviceInfo deviceInfo;

  const TVLayout({Key? key, required this.deviceInfo}) : super(key: key);

  @override
  ConsumerState<TVLayout> createState() => _TVLayoutState();
}

class _TVLayoutState extends ConsumerState<TVLayout> {
  late PageController _pageController;
  final FocusNode _sidebarFocus = FocusNode();

  @override
  void initState() {
    super.initState();
    _pageController = PageController();
    // Request focus for TV navigation
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _sidebarFocus.requestFocus();
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    _sidebarFocus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final currentIndex = ref.watch(navigationIndexProvider);
    final screens = ref.watch(navigationScreensProvider);

    return Shortcuts(
      shortcuts: <LogicalKeySet, Intent>{
        LogicalKeySet(LogicalKeyboardKey.select): const ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.enter): const ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.space): const ActivateIntent(),
        LogicalKeySet(LogicalKeyboardKey.gameButtonA): const ActivateIntent(),
      },
      child: Scaffold(
        body: Row(
          children: [
            // TV-optimized sidebar
            Focus(
              focusNode: _sidebarFocus,
              child: TVNavigationSidebar(
                selectedIndex: currentIndex,
                onIndexChanged: (index) {
                  ref.read(navigationIndexProvider.notifier).setIndex(index);
                  _pageController.jumpToPage(index);
                },
              ),
            ),

            // Main content area
            Expanded(
              child: PageView(
                controller: _pageController,
                physics: const NeverScrollableScrollPhysics(),
                children: screens,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
