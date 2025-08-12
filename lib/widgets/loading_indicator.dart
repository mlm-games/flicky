import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class LoadingIndicator extends StatelessWidget {
  final String? message;
  
  const LoadingIndicator({this.message});
  
  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(
            valueColor: AlwaysStoppedAnimation<Color>(AppTheme.primaryGreen),
          ),
          if (message != null) ...[
            SizedBox(height: 16),
            Text(
              message!,
              style: TextStyle(color: Colors.grey),
            ),
          ],
        ],
      ),
    );
  }
}