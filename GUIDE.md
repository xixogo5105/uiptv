# UIPTV User Guide

## Table of Contents
1. [Introduction](#1-introduction)
2. [Installation](#2-installation)
3. [Configuration](#3-configuration)
4. [Managing Accounts](#4-managing-accounts)
5. [Favorites](#5-favorites)
6. [Starting and Stopping the Server](#6-starting-and-stopping-the-server)
7. [Search Functionality](#7-search-functionality)
8. [Advanced Features](#8-advanced-features)
9. [Troubleshooting and FAQs](#9-troubleshooting-and-faqs)

---

## 1. Introduction
UIPTV is a tool designed to manage and stream IPTV content efficiently. This guide walks you through the installation, configuration, and usage of UIPTV, enabling you to manage accounts, parse data, and customize your viewing experience.

---

## 2. Installation
Follow these steps to install UIPTV:

1. **Download the software** from the official repository: [UIPTV Releases](https://github.com/xixogo5105/uiptv/releases/latest).
2. **Install the Application**: Run the downloaded installer (`.msi`, `.deb`, `.dmg`, etc.) and follow the on-screen instructions.
3. **Dependencies for Enhanced Features**:
   - For the best video playback experience, install **VLC Media Player** and ensure it is accessible from your system's PATH.
   - To play YouTube videos via RSS feeds, install **yt-dlp** and ensure it is accessible from your system's PATH.
4. **Run UIPTV**: Start the application after installation.

---

## 3. Configuration

The **Configuration** tab allows you to customize your experience with UIPTV. Here's a breakdown of the settings:

### Player Options
UIPTV includes two built-in players and also supports external players.

- **Embedded VLC Player**: The default and recommended player. It provides a full-featured experience but requires VLC to be installed on your system.
- **Embedded Lite Player**: A lightweight player with limited features that is used as a fallback if VLC is not detected.
- **External Player Paths**: You can specify up to three different external media players (e.g., MPV, SMPlayer) for playing IPTV channels. Use the **Browse...** button to select the executable for each player.

On the left of each player's path field are **radio buttons**. These allow you to select which player will be used by default when you **double-click a channel**.

### Filtering Options:
- **Category Filter**: Enter a comma-separated list of categories to exclude from the channel listing.
- **Channel Filter**: Enter a comma-separated list of specific channel names to exclude.

### Font Settings:
- Customize the **font family**, **size**, and **weight** to adjust the look of the UI.

### Additional Options:
- **Use Dark Theme**: Toggle dark mode for better visibility in low-light environments.
- **Pause Filtering**: Temporarily stop applying the category and channel filters.
- **Pause Caching**: Pause the caching of streamed content.
- **Clear Cache**: Manually clear all cached data to refresh content and resolve potential issues.

Once you’ve configured these options, click **Save** to apply your changes.

---

## 4. Managing Accounts

UIPTV allows users to configure five types of accounts from the **Manage Account** tab.

1. **Stalker Portal Account**:
   - A portal-based service for IPTV.
   - Enter the portal URL and MAC address.

2. **Xtream Codes API Account**:
   - For Xtreme-based services.
   - Input the API URL, username, and password.

3. **M3U Remote URL Account**:
   - Configure an M3U playlist from a remote URL.
   - Paste the URL to load channels from the online source.

4. **M3U Local File Account**:
   - For users who have an M3U playlist saved locally.
   - Browse and select the local file to add the playlist.

5. **RSS Feed Account**:
   - Add RSS feeds, including YouTube channels.
   - Enter the RSS feed URL to add it to UIPTV.

Each account can be added, managed, and removed. You can switch between accounts, enable or disable them, and parse the account details by clicking **Parse Accounts**.

---

## 5. Favorites

The **Favorites** tab allows you to manage and quickly access your preferred channels:

- **Adding Favorites**: Right-click a channel and select "Add to Favorites" to bookmark it.
- **Managing Favorites**: Use this tab to remove or reorder channels in your favorites list.

---

## 6. Starting and Stopping the Server

UIPTV includes an **experimental web server** that allows you to access your content through a web browser.

### How it Works:

- **Starting the Server**:
  - Navigate to the **Configuration** tab and click the **Start Server** button.
  - You can specify a port (e.g., 8080) before starting.

- **Accessing the Web Interface**:
  - Once the server is running, open a web browser and navigate to `http://localhost:<port>`.
  - The web interface allows you to select accounts, filter categories, and play channels directly in the browser (HLS streams only).

### Important Disclaimer:
- **Experimental Feature**: This feature is experimental and may have limitations.
- **Customizable**: The web pages are user-modifiable, allowing for customization of the appearance and functionality.

- **Stopping the Server**:
  - To stop the server, click the **Stop Server** button in the **Configuration** tab.

---

## 7. Search Functionality

UIPTV offers a **Search** feature to quickly filter accounts, categories, and channels.

- **Search Box**: At the top of the screen, a search box allows you to filter the lists in real-time.
- **Auto-clear**: Clicking inside the search box clears any existing text.
- **Real-time Filtering**: The lists update instantly as you type, showing only matching results.

---

## 8. Advanced Features

### Player Selection
When you double-click a channel, the player selected via the radio button in the **Configuration** tab will be used.

### Caching and Filtering
You can temporarily disable caching and filtering by checking the corresponding boxes in the **Configuration** tab.

### Update Mechanism
The application includes an **About** page that checks for new releases. This feature will be enhanced in the future.

### RSS Feed for YouTube
You can add a YouTube channel as an RSS feed to watch its videos.

1. Find the `channel_id` of the YouTube channel.
2. Use the following URL format: `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`
3. Add this URL as an RSS Feed account in UIPTV.
4. **Note**: **yt-dlp** must be installed and in your system's PATH for playback to work.

---

## 9. Troubleshooting and FAQs

- **Issue: Channels not appearing**
  **Solution**: Check your account configuration in the **Manage Account** tab. Ensure the account is enabled and parsed. Also, check that your filters are not unintentionally hiding channels.

- **Issue: The server won’t start**
  **Solution**: Ensure the specified port is not in use by another application and that your firewall is not blocking UIPTV.

- **Issue: Video playback fails**
  **Solution**:
    - If using the embedded VLC player, ensure **VLC** is installed correctly.
    - If playing a YouTube video, ensure **yt-dlp** is installed and in your system's PATH.
    - Verify that the correct player is selected in the **Configuration** tab.

- **Issue: Slow performance or delays**
  **Solution**: Use the **Clear Cache** button in the **Configuration** tab periodically to refresh data and improve performance.
