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

1. **Download the software** from the official repository: [UIPTV GitHub](https://github.com/xixogo5105/uiptv).
2. **Install Dependencies**: Ensure all required dependencies are installed by following the instructions in the developer's guide.
3. **Run UIPTV**: Start the application after installation.

---

## 3. Configuration

The **Configuration** panel allows you to customize your experience with UIPTV. Here's a breakdown of the settings:

### Player Paths
You can specify up to three different media player paths for playing IPTV channels:

- **Player 1**: Use the **Browse...** button to select the path for your primary media player.
- **Player 2**: Select the path for your second media player.
- **Player 3**: Configure a third media player if needed.

On the left of each player's path field, you'll see **radio buttons**. These buttons allow you to select which player will be used by default when you **double-click a channel** to start playback. Only one player can be selected at a time.

### Filtering Options:
- **Category Filter**: Enter a comma-separated list of categories. Channels from these categories will be excluded from the listing.
- **Channel Filter**: Enter a comma-separated list of specific channel names you want to exclude from the view.

### Font Settings:
- Customize the **font family**, **size**, and **weight** to adjust the look of the UI.

### Additional Options:
- **Use Dark Theme**: Toggle dark mode for better visibility in low-light environments.
- **Pause Filtering**: Temporarily stop applying the filters you’ve set.
- **Pause Caching**: Pause the caching of streamed content.
- **Clear Cache**: Manually clear cached data.

Once you’ve configured these options, click **Save** to apply your changes.

---

## 4. Managing Accounts

UIPTV allows users to configure five types of accounts. Here's how to manage each account type:

1. **Stalker Portal Account**:
   - A portal-based service for IPTV.
   - Users need to enter the portal URL, username, and password in the account configuration.

2. **Xtream Codes API Account**:
   - For Xtream-based services.
   - Users will need to input the API URL, username, password, and optional settings like the stream type.

3. **M3U8 Remote URL Account**:
   - Users can configure an M3U8 playlist from a remote URL.
   - Simply paste the URL in the account section, and the channels will be loaded from that online source.

4. **M3U8 Local File Account**:
   - For users who have an M3U8 playlist saved locally on their computer.
   - Browse and select the local file to add this playlist to UIPTV.

5. **RSS Feed Account**:
   - Users can add RSS feeds, including YouTube channels as RSS feeds.
   - Enter the RSS feed URL in the account section to add it to UIPTV.

Each account type can be added, managed, and removed under the **Manage Account** section. You can switch between accounts, enable or disable them, and parse the account details by clicking **Parse Accounts**.

---

## 5. Favorites

The **Favorites** section allows you to manage and quickly access your preferred channels:

- **Adding Favorites**: Right-click a channel and select "Add to Favorites" to mark it for quick access.
- **Managing Favorites**: Use this tab to remove or reorder channels you've added to your favorites list.

---

## 6. Starting and Stopping the Server

UIPTV includes an **experimental web server** feature that allows users to run the entire application through a web-based interface. This transforms UIPTV from a desktop-based app into a web solution that can be accessed via a web browser.

### How it Works:

- **Starting the Server**:
  - You can start the server by clicking the **Start Server** button in the configuration panel.
  - Optionally, specify a port (e.g., 8080) in the provided text field before starting. By default, it uses a standard port number.

- **Accessing the Web Interface**:
  - Once the server is running, you can access UIPTV by opening a web browser and navigating to `http://localhost:<port>` (replace `<port>` with the number you specified or the default port).
  - This opens a **fully interactive web-based version** of the app, where you can:
    - **Select Accounts**: Manage and choose from the configured IPTV accounts.
    - **Filter Categories**: Narrow down available channels based on categories.
    - **Browse Channels**: Select and play channels directly from the web interface.
    - **Player Controls**: You can play content directly through the web pages by selecting channels, categories, and accounts, just like in the desktop version.

### Purpose of the Web Interface:
This web server functionality was designed to provide users with **flexibility** in how they access and manage their IPTV content. By running a local web server, you can expose a **web service** version of UIPTV that can be accessed on other devices or remotely (with appropriate network setup).

### Important Disclaimer:
- **Experimental Feature**: Please note that the web server feature is **experimental** and not intended to function as a commercial-grade or flawless web service. While it offers great flexibility, it may have limitations, and users might experience issues when running the web version.

- **Customizable Web Pages**: The hosted pages can be **modified by the end user**, meaning the appearance and functionality of the web interface can be customized if desired.

The primary purpose of this feature is to **expose the web service** and offer users an alternative method to access their IPTV content, but it should not be viewed as a fully commercial web-based IPTV service.

- **Stopping the Server**:
  - To stop the server, simply click the **Stop Server** button. This will terminate the local web service, and the app will revert to desktop-only use.

---

## 7. Search Functionality

UIPTV offers a **Search** feature that allows you to filter and narrow down results for accounts, categories, and channels.

- **Search Box**: At the top of the screen, there is a search input box that can be used to quickly filter the account list, categories, or channels.
- **Auto-clear on Click**: When you click inside the search box, any existing text will automatically be cleared, allowing you to start a new search easily.
- **Real-time Filtering**: As you start typing, the account list or channel list will be filtered in real-time, showing only the matching results. The search is dynamic and updates instantly as you type.

This feature is useful when managing large lists of accounts or channels, providing a quick way to find exactly what you’re looking for.

---

## 8. Advanced Features

### Player Selection for Channels
When you double-click a channel to play, the **selected player** (as configured in the **Player Paths** section) will be used for playback. You can switch between players by choosing one of the radio buttons next to each player’s path.

### Caching and Filtering:
You can pause both caching and filtering options temporarily by enabling the relevant checkboxes in the configuration panel.

### Font and Theme Customization:
Adjust the UI appearance through font family, size, and theme settings.

### RSS Feed Support:
You can add RSS feeds to the player. This includes support for YouTube channels as RSS feeds. Here is an example of how to use YouTube channels as RSS feeds:

1. Find the channel ID of the YouTube channel you want to add.
2. Use the following URL format to add the RSS feed: `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`
3. Add this URL to the UIPTV configuration.

---

## 9. Troubleshooting and FAQs

- **Issue: Channels not appearing**
  **Solution**: Check your account configuration under **Manage Accounts** and ensure the correct account type is set up. Also, make sure you’re not filtering out channels unintentionally via the category or channel filters.

- **Issue: The server won’t start**
  **Solution**: Ensure that the port number specified (e.g., 8080) is not in use by another application, and check if your firewall settings allow UIPTV to run the server.

- **Issue: Double-clicking a channel doesn’t start playback**
  **Solution**: Verify that you’ve selected the correct media player using the radio buttons next to the player paths. Ensure the player paths are correctly configured, and the player is installed.

- **Issue: Slow performance or delays**
  **Solution**: Clear the cache periodically by using the **Clear Cache** button in the configuration panel to improve performance.