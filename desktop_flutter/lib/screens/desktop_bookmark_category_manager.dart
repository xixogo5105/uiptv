// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class DesktopBookmarkCategoryManagerDialog extends StatefulWidget {
  const DesktopBookmarkCategoryManagerDialog({
    super.key,
    required this.categories,
    required this.onAdd,
    required this.onRemove,
    required this.categoriesLabel,
    required this.categoryLabel,
    required this.addLabel,
    required this.removeLabel,
    required this.closeLabel,
    required this.title,
    required this.emptyLabel,
  });

  final List<UiptvBookmarkCategory> categories;
  final Future<List<UiptvBookmarkCategory>> Function(String name) onAdd;
  final Future<List<UiptvBookmarkCategory>> Function(String id) onRemove;
  final String categoriesLabel;
  final String categoryLabel;
  final String addLabel;
  final String removeLabel;
  final String closeLabel;
  final String title;
  final String emptyLabel;

  @override
  State<DesktopBookmarkCategoryManagerDialog> createState() =>
      _DesktopBookmarkCategoryManagerDialogState();
}

class _DesktopBookmarkCategoryManagerDialogState
    extends State<DesktopBookmarkCategoryManagerDialog> {
  final TextEditingController _nameController = TextEditingController();
  late List<UiptvBookmarkCategory> _categories;
  String? _selectedCategoryId;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _categories = List.of(widget.categories);
  }

  @override
  void dispose() {
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _addCategory() async {
    final name = _nameController.text.trim();
    if (name.isEmpty || _busy) return;
    setState(() => _busy = true);
    try {
      final categories = await widget.onAdd(name);
      if (!mounted) return;
      setState(() {
        _categories = categories;
        _selectedCategoryId = null;
      });
      _nameController.clear();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _removeCategory() async {
    final id = _selectedCategoryId;
    if (id == null || _busy) return;
    setState(() => _busy = true);
    try {
      final categories = await widget.onRemove(id);
      if (!mounted) return;
      setState(() {
        _categories = categories;
        _selectedCategoryId = null;
      });
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final dark = theme.brightness == Brightness.dark;
    final mediaSize = MediaQuery.sizeOf(context);
    final width = (mediaSize.width - 32).clamp(240.0, 480.0);
    final height = (mediaSize.height - 32).clamp(260.0, 520.0);
    final surface = dark ? const Color(0xFF252525) : Colors.white;
    final surfaceMuted = dark
        ? const Color(0xFF2B2B2B)
        : const Color(0xFFF5F5F5);
    final border = dark ? const Color(0xFF3D3D3D) : const Color(0xFFE5E5E5);
    final text = dark ? const Color(0xFFF2F2F2) : const Color(0xFF111318);
    final secondary = dark ? const Color(0xFFB8B8B8) : const Color(0xFF737373);
    final action = dark ? const Color(0xFFE6E6E6) : const Color(0xFF101827);
    final actionText = dark ? const Color(0xFF171717) : Colors.white;
    final selectedBackground = dark
        ? const Color(0xFF3A3A3A)
        : const Color(0xFF101827);
    final selectedBorder = dark
        ? const Color(0xFF6A6A6A)
        : const Color(0xFF1F2A44);
    final selectedText = dark ? const Color(0xFFF7F7F7) : Colors.white;
    final borderSide = BorderSide(color: border);

    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      clipBehavior: Clip.antiAlias,
      child: SizedBox(
        width: width,
        height: height,
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: dark
                  ? const [Color(0xFF1E1E1E), Color(0xFF191919)]
                  : const [Colors.white, Color(0xFFF6F7F9)],
            ),
          ),
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        widget.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleLarge?.copyWith(
                          color: text,
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    IconButton(
                      key: const ValueKey('bookmark-category-manager-close'),
                      tooltip: widget.closeLabel,
                      onPressed: () => Navigator.of(context).pop(),
                      icon: const Icon(Icons.close),
                      color: secondary,
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Expanded(
                  child: _card(
                    surface,
                    borderSide,
                    child: _categories.isEmpty
                        ? Center(
                            child: Text(
                              widget.emptyLabel,
                              style: TextStyle(
                                color: secondary,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          )
                        : ListView.builder(
                            key: const ValueKey('bookmark-category-list'),
                            padding: EdgeInsets.zero,
                            itemCount: _categories.length,
                            itemBuilder: (context, index) {
                              final category = _categories[index];
                              final selected =
                                  category.id == _selectedCategoryId;
                              return Padding(
                                padding: const EdgeInsets.symmetric(
                                  vertical: 5,
                                  horizontal: 1,
                                ),
                                child: Material(
                                  color: Colors.transparent,
                                  borderRadius: BorderRadius.circular(14),
                                  child: InkWell(
                                    key: ValueKey(
                                      'bookmark-category-${category.id}',
                                    ),
                                    borderRadius: BorderRadius.circular(14),
                                    onTap: _busy
                                        ? null
                                        : () => setState(
                                            () => _selectedCategoryId =
                                                category.id,
                                          ),
                                    child: Container(
                                      constraints: const BoxConstraints(
                                        minHeight: 44,
                                      ),
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 12,
                                        vertical: 8,
                                      ),
                                      decoration: BoxDecoration(
                                        color: selected
                                            ? selectedBackground
                                            : surfaceMuted,
                                        borderRadius: BorderRadius.circular(14),
                                        border: Border.all(
                                          color: selected
                                              ? selectedBorder
                                              : border,
                                        ),
                                      ),
                                      child: Row(
                                        children: [
                                          Expanded(
                                            child: Text(
                                              category.name,
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: TextStyle(
                                                color: selected
                                                    ? selectedText
                                                    : text,
                                                fontWeight: FontWeight.w700,
                                                fontSize: 14,
                                              ),
                                            ),
                                          ),
                                          const SizedBox(width: 8),
                                          Container(
                                            padding: const EdgeInsets.symmetric(
                                              horizontal: 9,
                                              vertical: 3,
                                            ),
                                            decoration: BoxDecoration(
                                              color: action,
                                              borderRadius:
                                                  BorderRadius.circular(999),
                                            ),
                                            child: Text(
                                              widget.categoriesLabel,
                                              style: TextStyle(
                                                color: actionText,
                                                fontSize: 11,
                                                fontWeight: FontWeight.w700,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                  ),
                ),
                const SizedBox(height: 14),
                _card(
                  surface,
                  borderSide,
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          key: const ValueKey('bookmark-category-name'),
                          controller: _nameController,
                          enabled: !_busy,
                          textInputAction: TextInputAction.done,
                          onSubmitted: (_) => _addCategory(),
                          decoration: InputDecoration(
                            hintText: widget.categoryLabel,
                            isDense: true,
                            contentPadding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 9,
                            ),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(14),
                              borderSide: borderSide,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 8),
                      FilledButton(
                        key: const ValueKey('bookmark-category-add'),
                        onPressed: _busy ? null : _addCategory,
                        child: Text(widget.addLabel),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    const Spacer(),
                    FilledButton(
                      key: const ValueKey('bookmark-category-remove'),
                      onPressed: _selectedCategoryId == null || _busy
                          ? null
                          : _removeCategory,
                      style: FilledButton.styleFrom(
                        backgroundColor: const Color(0xFFDC2626),
                        foregroundColor: Colors.white,
                        disabledBackgroundColor: const Color(0xFFDC2626)
                            .withValues(alpha: 0.42),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(13),
                        ),
                      ),
                      child: Text(widget.removeLabel),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _card(Color surface, BorderSide borderSide, {required Widget child}) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: surface,
        borderRadius: BorderRadius.circular(8),
        border: Border.fromBorderSide(borderSide),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.07),
            blurRadius: 14,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: child,
    );
  }
}
