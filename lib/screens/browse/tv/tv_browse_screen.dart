import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/widgets/error_widget.dart';
import 'package:flicky/widgets/loading_indicator.dart';
import 'package:flicky/widgets/sync_progress_indicator.dart';
import 'package:flicky/widgets/tv/tv_app_card.dart';
import 'package:flicky/widgets/tv_search_field.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class TVBrowseScreen extends ConsumerWidget {
  final DeviceInfo deviceInfo;

  const TVBrowseScreen({Key? key, required this.deviceInfo}) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(sortedAppsProvider);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          // Search and filter section
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  Row(
                    children: [
                      const Expanded(child: TVSearchField()),
                      const SizedBox(width: 12),
                      _buildFilterButton(context, ref),
                    ],
                  ),
                  const SizedBox(height: 10),
                  SyncProgressIndicator(),
                  // Status text
                  apps.when(
                    data: (appList) => Text(
                      '${appList.length} apps available',
                      style: const TextStyle(color: Colors.grey),
                    ),
                    loading: () => const Text(
                      'Loading F-Droid repository...',
                      style: TextStyle(color: Colors.grey),
                    ),
                    error: (_, __) => const Text(
                      'Using offline data',
                      style: TextStyle(color: Colors.orange),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Apps grid
          SliverPadding(
            padding: const EdgeInsets.all(20),
            sliver: apps.when(
              data: (appList) {
                if (appList.isEmpty) {
                  return SliverToBoxAdapter(
                    child: Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: const [
                          Icon(Icons.search_off, size: 64, color: Colors.grey),
                          SizedBox(height: 16),
                          Text(
                            'No apps found',
                            style: TextStyle(fontSize: 18, color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                  );
                }

                return SliverGrid(
                  gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: _getGridCrossAxisCount(deviceInfo),
                    childAspectRatio: 0.8,
                    crossAxisSpacing: 16,
                    mainAxisSpacing: 16,
                  ),
                  delegate: SliverChildBuilderDelegate(
                    (context, index) =>
                        TVAppCard(app: appList[index], autofocus: index == 0),
                    childCount: appList.length,
                  ),
                );
              },
              loading: () => SliverToBoxAdapter(
                child: SizedBox(height: 400, child: const LoadingIndicator()),
              ),
              error: (error, stack) => SliverToBoxAdapter(
                child: ErrorDisplay(error: error, stackTrace: stack),
              ),
            ),
          ),
        ],
      ),
    );
  }

  int _getGridCrossAxisCount(DeviceInfo deviceInfo) {
    final width = deviceInfo.size.width;
    if (width > 1400) return 6;
    if (width > 1200) return 5;
    if (width > 900) return 4;
    if (width > 600) return 3;
    return 2;
  }

  Widget _buildFilterButton(BuildContext context, WidgetRef ref) {
    return TVFilterButton(onTap: () => _showFilterDialog(context, ref));
  }

  void _showFilterDialog(BuildContext context, WidgetRef ref) {
    final sortOption = ref.read(sortOptionProvider);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Sort & Filter'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Sort by:'),
            const SizedBox(height: 8),
            ...SortOption.values.map(
              (option) => RadioListTile<SortOption>(
                title: Text(_getSortOptionLabel(option)),
                value: option,
                groupValue: sortOption,
                onChanged: (value) {
                  if (value != null) {
                    ref.read(sortOptionProvider.notifier).state = value;
                    Navigator.pop(context);
                  }
                },
                autofocus: option == sortOption,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  String _getSortOptionLabel(SortOption option) {
    switch (option) {
      case SortOption.name:
        return 'Name';
      case SortOption.updated:
        return 'Recently Updated';
      case SortOption.size:
        return 'Size';
      case SortOption.added:
        return 'Recently Added';
    }
  }
}

class TVFilterButton extends StatefulWidget {
  final VoidCallback onTap;

  const TVFilterButton({Key? key, required this.onTap}) : super(key: key);

  @override
  State<TVFilterButton> createState() => _TVFilterButtonState();
}

class _TVFilterButtonState extends State<TVFilterButton> {
  late FocusNode _focusNode;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InkWell(
      focusNode: _focusNode,
      onTap: widget.onTap,
      borderRadius: BorderRadius.circular(24),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: _isFocused
              ? Theme.of(context).primaryColor.withOpacity(0.1)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(24),
          border: Border.all(
            color: _isFocused
                ? Theme.of(context).primaryColor
                : Colors.grey.withOpacity(0.3),
            width: _isFocused ? 2 : 1,
          ),
        ),
        child: Icon(
          Icons.filter_list,
          color: _isFocused ? Theme.of(context).primaryColor : null,
        ),
      ),
    );
  }
}
