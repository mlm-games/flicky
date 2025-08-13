import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import '../providers/app_providers.dart';
import '../theme/app_theme.dart';

class VoiceSearchButton extends ConsumerStatefulWidget {
  @override
  _VoiceSearchButtonState createState() => _VoiceSearchButtonState();
}

class _VoiceSearchButtonState extends ConsumerState<VoiceSearchButton> {
  bool isListening = false;
  late stt.SpeechToText _speech;
  bool _speechEnabled = false;
  
  @override
  void initState() {
    super.initState();
    _speech = stt.SpeechToText();
    _initSpeech();
  }
  
  void _initSpeech() async {
    _speechEnabled = await _speech.initialize();
    setState(() {});
  }
  
  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(
        isListening ? Icons.mic : Icons.mic_none,
        color: isListening ? AppTheme.primaryGreen : (_speechEnabled ? null : Colors.grey),
      ),
      onPressed: _speechEnabled ? () {
        if (isListening) {
          _stopListening();
        } else {
          _startListening();
        }
      } : null,
    );
  }
  
  void _startListening() async {
    await _speech.listen(
      onResult: (result) {
        ref.read(searchQueryProvider.notifier).state = result.recognizedWords;
      },
    );
    setState(() => isListening = true);
  }
  
  void _stopListening() async {
    await _speech.stop();
    setState(() => isListening = false);
  }
}