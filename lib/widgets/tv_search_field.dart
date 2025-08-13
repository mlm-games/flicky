import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class TVSearchField extends ConsumerStatefulWidget {
  @override
  _TVSearchFieldState createState() => _TVSearchFieldState();
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

  void _handleKeyEvent(KeyEvent event) {
    if (event is KeyDownEvent) {
      if (event.logicalKey == LogicalKeyboardKey.arrowDown ||
          event.logicalKey == LogicalKeyboardKey.arrowUp ||
          event.logicalKey == LogicalKeyboardKey.arrowLeft ||
          event.logicalKey == LogicalKeyboardKey.arrowRight) {
        // if (!_controller.text.isNotEmpty) {
        //   // If search is empty, allow navigation
        //   _focusNode.unfocus();
        // }
      } else if (event.logicalKey == LogicalKeyboardKey.escape) {
        // Clear search and unfocus
        _controller.clear();
        ref.read(searchQueryProvider.notifier).state = '';
        _focusNode.unfocus();
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return KeyboardListener(
      focusNode: FocusNode(),
      onKeyEvent: _handleKeyEvent,
      child: AnimatedContainer(
        duration: Duration(milliseconds: 200),
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
            // Unfocus after search
            _focusNode.unfocus();
          },
          decoration: InputDecoration(
            hintText: 'Search apps...',
            prefixIcon: Icon(Icons.search),
            suffixIcon: _controller.text.isNotEmpty
                ? IconButton(
                    icon: Icon(Icons.clear),
                    onPressed: () {
                      _controller.clear();
                      ref.read(searchQueryProvider.notifier).state = '';
                      _focusNode.unfocus();
                    },
                  )
                : null,
            border: InputBorder.none,
            contentPadding: EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          ),
        ),
      ),
    );
  }
}