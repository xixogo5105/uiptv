import '../api/uiptv_api_client.dart';

class DesktopChannelQueue {
  static List<UiptvChannelSummary> filter({
    required Iterable<UiptvChannelSummary> channels,
    String query = '',
    bool playableOnly = false,
  }) {
    final normalizedQuery = query.trim().toLowerCase();
    return channels
        .where((channel) {
          if (playableOnly && !channel.playable) return false;
          if (normalizedQuery.isEmpty) return true;
          return channel.name.toLowerCase().contains(normalizedQuery) ||
              channel.description.toLowerCase().contains(normalizedQuery);
        })
        .toList(growable: false);
  }

  static List<UiptvChannelSummary> appendFiltered({
    required Iterable<UiptvChannelSummary> current,
    required Iterable<UiptvChannelSummary> nextPage,
    required String query,
  }) {
    final queue = current.toList();
    final knownIds = queue.map((channel) => channel.dbId).toSet();
    for (final channel in filter(
      channels: nextPage,
      query: query,
      playableOnly: true,
    )) {
      if (knownIds.add(channel.dbId)) queue.add(channel);
    }
    return List.unmodifiable(queue);
  }
}
