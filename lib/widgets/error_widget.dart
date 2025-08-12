import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';

class ErrorDisplay extends ConsumerWidget {
  final Object error;
  final StackTrace? stackTrace;
  
  const ErrorDisplay({
    required this.error,
    this.stackTrace,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Center(
      child: Container(
        padding: EdgeInsets.all(20),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.error_outline,
              size: 64,
              color: Colors.red,
            ),
            SizedBox(height: 16),
            Text(
              'Failed to load apps',
              style: TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 8),
            Text(
              error.toString(),
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.grey),
            ),
            SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () {
                // Refresh the apps provider
                ref.invalidate(appsProvider);
              },
              icon: Icon(Icons.refresh),
              label: Text('Retry'),
            ),
          ],
        ),
      ),
    );
  }
}