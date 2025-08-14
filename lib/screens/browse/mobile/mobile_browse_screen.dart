import 'package:flicky/core/responsive/responsive_builder.dart';
import 'package:flicky/widgets/adaptive/adaptive_app_grid.dart';
import 'package:flicky/widgets/mobile/mobile_search_bar.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class MobileBrowseScreen extends ConsumerWidget {
  final DeviceInfo deviceInfo;

  const MobileBrowseScreen({Key? key, required this.deviceInfo})
    : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            floating: true,
            snap: true,
            title: const Text('Browse Apps'),
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(60),
              child: Padding(
                padding: const EdgeInsets.all(8.0),
                child: MobileSearchBar(),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.all(16),
            sliver: AdaptiveAppGrid(deviceInfo: deviceInfo),
          ),
        ],
      ),
    );
  }
}
