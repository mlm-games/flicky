import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import 'voice_search.dart';
import 'tv_search_field.dart';

class SearchBar extends ConsumerStatefulWidget {
  @override
  _SearchBarState createState() => _SearchBarState();
}

class _SearchBarState extends ConsumerState<SearchBar> {
  late FocusNode _filterFocusNode;
  bool _filterFocused = false;

  @override
  void initState() {
    super.initState();
    _filterFocusNode = FocusNode();
    _filterFocusNode.addListener(() {
      setState(() {
        _filterFocused = _filterFocusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _filterFocusNode.dispose();
    super.dispose();
  }

  void _showFilterDialog() {
    final sortOption = ref.read(sortOptionProvider);
    
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Sort & Filter'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('Sort by:'),
            SizedBox(height: 8),
            ...SortOption.values.map((option) => RadioListTile<SortOption>(
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
            )),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Close'),
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

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Stack(
            alignment: Alignment.centerRight,
            children: [
              TVSearchField(),
              Positioned(
                right: 48,
                child: VoiceSearchButton(),
              ),
            ],
          ),
        ),
        SizedBox(width: 12),
        InkWell(
          focusNode: _filterFocusNode,
          onTap: _showFilterDialog,
          borderRadius: BorderRadius.circular(24),
          child: AnimatedContainer(
            duration: Duration(milliseconds: 200),
            padding: EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: _filterFocused ? Theme.of(context).primaryColor.withOpacity(0.1) : Colors.transparent,
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: _filterFocused ? Theme.of(context).primaryColor : Colors.grey.withOpacity(0.3),
                width: _filterFocused ? 2 : 1,
              ),
            ),
            child: Icon(
              Icons.filter_list,
              color: _filterFocused ? Theme.of(context).primaryColor : null,
            ),
          ),
        ),
      ],
    );
  }
}