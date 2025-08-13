import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class TVFocusWrapper extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;
  final bool autofocus;
  final BorderRadius? borderRadius;
  final EdgeInsets? padding;
  final bool showFocusBorder;

  const TVFocusWrapper({
    super.key,
    required this.child,
    this.onTap,
    this.autofocus = false,
    this.borderRadius,
    this.padding,
    this.showFocusBorder = true,
  });

  @override
  State<TVFocusWrapper> createState() => _TVFocusWrapperState();
}

class _TVFocusWrapperState extends State<TVFocusWrapper> {
  late FocusNode _focusNode;
  bool _isFocused = false;

  @override
  void initState() {
    super.initState();
    _focusNode = FocusNode();
    _focusNode.addListener(() {
      if (mounted) {
        setState(() {
          _isFocused = _focusNode.hasFocus;
        });
      }
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final borderRadius = widget.borderRadius ?? BorderRadius.circular(12);

    return InkWell(
      focusNode: _focusNode,
      autofocus: widget.autofocus,
      onTap: widget.onTap,
      borderRadius: borderRadius,
      child: AnimatedContainer(
        duration: Duration(milliseconds: 200),
        padding: widget.padding,
        decoration: widget.showFocusBorder
            ? BoxDecoration(
                borderRadius: borderRadius,
                border: Border.all(
                  color: _isFocused
                      ? AppTheme.primaryGreen
                      : Colors.transparent,
                  width: _isFocused ? 3 : 0,
                ),
                boxShadow: _isFocused
                    ? [
                        BoxShadow(
                          color: AppTheme.primaryGreen..withValues(alpha: 0.2),
                          blurRadius: 8,
                          spreadRadius: 0,
                        ),
                      ]
                    : [],
              )
            : null,
        child: widget.child,
      ),
    );
  }
}
