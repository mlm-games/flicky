import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import 'voice_search.dart';

class SearchBar extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sortOption = ref.watch(sortOptionProvider);
    
    return Row(
      children: [
        Expanded(
          child: Container(
            height: 56,
            decoration: BoxDecoration(
              color: Theme.of(context).cardColor,
              borderRadius: BorderRadius.circular(28),
              border: Border.all(color: Colors.grey.shade300),
            ),
            child: TextField(
              onChanged: (value) {
                ref.read(searchQueryProvider.notifier).state = value;
              },
              decoration: InputDecoration(
                hintText: 'Search apps...',
                prefixIcon: Icon(Icons.search),
                suffixIcon: VoiceSearchButton(),
                border: InputBorder.none,
                contentPadding: EdgeInsets.symmetric(horizontal: 20, vertical: 16),
              ),
            ),
          ),
        ),
        SizedBox(width: 12),
        // Sort dropdown
        Container(
          padding: EdgeInsets.symmetric(horizontal: 12),
          decoration: BoxDecoration(
            color: Theme.of(context).cardColor,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.grey.shade300),
          ),
          child: DropdownButton<SortOption>(
            value: sortOption,
            underline: SizedBox(),
            icon: Icon(Icons.sort),
            onChanged: (value) {
              if (value != null) {
                ref.read(sortOptionProvider.notifier).state = value;
              }
            },
            items: [
              DropdownMenuItem(
                value: SortOption.updated,
                child: Text('Recently Updated'),
              ),
              DropdownMenuItem(
                value: SortOption.name,
                child: Text('Name'),
              ),
              DropdownMenuItem(
                value: SortOption.added,
                child: Text('Recently Added'),
              ),
              DropdownMenuItem(
                value: SortOption.size,
                child: Text('Size'),
              ),
            ],
          ),
        ),
      ],
    );
  }
}