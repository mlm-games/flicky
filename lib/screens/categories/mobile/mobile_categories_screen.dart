import 'package:flicky/providers/app_providers.dart';
import 'package:flicky/widgets/adaptive/adaptive_app_card.dart';
import 'package:flicky/widgets/mobile/category_chip_list.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class MobileCategoriesScreen extends ConsumerStatefulWidget {
  const MobileCategoriesScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<MobileCategoriesScreen> createState() =>
      _MobileCategoriesScreenState();
}

class _MobileCategoriesScreenState
    extends ConsumerState<MobileCategoriesScreen> {
  String selectedCategory = 'All';

  @override
  Widget build(BuildContext context) {
    final categories = ref.watch(categoriesProvider);
    final apps = ref.watch(appsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Categories')),
      body: Column(
        children: [
          CategoryChipList(
            categories: ['All', ...categories],
            selectedCategory: selectedCategory,
            onCategorySelected: (category) {
              setState(() => selectedCategory = category);
            },
          ),
          Expanded(
            child: apps.when(
              data: (appList) {
                final filteredApps = selectedCategory == 'All'
                    ? appList
                    : appList
                          .where((app) => app.category == selectedCategory)
                          .toList();

                return GridView.builder(
                  padding: const EdgeInsets.all(16),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    childAspectRatio: 0.75,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                  ),
                  itemCount: filteredApps.length,
                  itemBuilder: (context, index) {
                    return AdaptiveAppCard(app: filteredApps[index]);
                  },
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (error, stack) => Center(child: Text('Error: $error')),
            ),
          ),
        ],
      ),
    );
  }
}
