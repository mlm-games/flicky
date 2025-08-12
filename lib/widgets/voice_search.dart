import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';

class VoiceSearchButton extends ConsumerStatefulWidget {
  @override
  _VoiceSearchButtonState createState() => _VoiceSearchButtonState();
}

class _VoiceSearchButtonState extends ConsumerState<VoiceSearchButton> {
  bool isListening = false;
  
  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(
        isListening ? Icons.mic : Icons.mic_none,
        color: isListening ? AppTheme.primaryGreen : null,
      ),
      onPressed: () {
        setState(() => isListening = !isListening);
        if (isListening) {
          _startListening();
        }
      },
    );
  }
  
  void _startListening() {
    // TODO: Implement voice search
    // For now, simulates voice input
    Future.delayed(Duration(seconds: 2), () {
      if (mounted) {
        setState(() => isListening = false);
        // Simulate setting search query
        ref.read(searchQueryProvider.notifier).state = 'termux';
      }
    });
  }
}