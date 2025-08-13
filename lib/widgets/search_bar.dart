import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import 'voice_search.dart';
import 'tv_search_field.dart';

class SearchBar extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final searchQuery = ref.watch(searchQueryProvider);
    
    return Row(
      children: [
        Expanded(
          child: Stack(
            alignment: Alignment.centerRight,
            children: [
              TVSearchField(),
              Positioned(
                right: 8,
                child: VoiceSearchButton(),
              ),
            ],
          ),
        ),
        SizedBox(width: 12),
        IconButton(
          icon: Icon(Icons.filter_list),
          onPressed: () {
            // TODO: Show sort/filter options
          },
        ),
      ],
    );
  }
}