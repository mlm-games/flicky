import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class TVSearchField extends ConsumerStatefulWidget {
  const TVSearchField({Key? key}) : super(key: key);
  
  @override
  ConsumerState<TVSearchField> createState() => _TVSearchFieldState();
}

class _TVSearchFieldState extends ConsumerState<TVSearchField> {
  late FocusNode _focusNode;
  late TextEditingController _controller;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _controller = TextEditingController();
    
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Focus(
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          // Handle back/escape key
          if (event.logicalKey == LogicalKeyboardKey.escape ||
              event.logicalKey == LogicalKeyboardKey.goBack) {
            if (_controller.text.isNotEmpty) {
              // Clear search first
              _controller.clear();
              ref.read(searchQueryProvider.notifier).state = '';
              return KeyEventResult.handled;
            } else {
              // Unfocus and let the back button work normally
              _focusNode.unfocus();
              return KeyEventResult.ignored;
            }
          }
          
          // Allow navigation when field is focused but empty
          if (_controller.text.isEmpty && _isFocused) {
            if (event.logicalKey == LogicalKeyboardKey.arrowDown ||
                event.logicalKey == LogicalKeyboardKey.arrowUp) {
              _focusNode.unfocus();
              return KeyEventResult.ignored;
            }
          }
        }
        return KeyEventResult.ignored;
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        height: 56,
        decoration: BoxDecoration(
          color: Theme.of(context).cardColor,
          borderRadius: BorderRadius.circular(28),
          border: Border.all(
            color: _isFocused 
                ? Theme.of(context).primaryColor 
                : Colors.grey.shade300,
            width: _isFocused ? 2 : 1,
          ),
        ),
        child: TextField(
          controller: _controller,
          focusNode: _focusNode,
          onChanged: (value) {
            ref.read(searchQueryProvider.notifier).state = value;
          },
          onSubmitted: (value) {
            _focusNode.unfocus();
          },
          decoration: InputDecoration(
            hintText: 'Search apps...',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _controller.text.isNotEmpty
                ? IconButton(
                    icon: const Icon(Icons.clear),
                    onPressed: () {
                      _controller.clear();
                      ref.read(searchQueryProvider.notifier).state = '';
                    },
                  )
                : null,
            border: InputBorder.none,
            contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          ),
        ),
      ),
    );
  }
}