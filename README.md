# Enhanced Messaging
Private message conversations in the RuneLite sidebar.

Enable **Enhanced Messaging**, open its chat bubble sidebar tab, and send or
receive private messages through the normal game chat. Select a player to view
their incoming and outgoing messages with timestamps. Conversations with recent
activity appear first.

By default, history is kept in memory while you are logged in and the plugin is
enabled. World hops retain it; logout, plugin disable, and restart clear the
display. History begins when the plugin is enabled.

To keep conversations between sessions, enable **Retain message history** in
the sidebar or plugin settings. A confirmation explains what is saved before
you enable it. The current session is included. Saved history is restored when
you log in to the same game character with retention enabled.

Files are stored locally as compressed UTF-8 JSON in
`.runelite/plugin-data/enhanced-messaging/<character-key>/conversations.json.gz`.
The folder key is derived from RuneLite's character profile, so character name
changes do not mix histories. Files contain player names, message text, direction,
timestamps, and message IDs. They are **not encrypted**; anyone who can access
the files can read them. No messages are uploaded or stored in RuneLite's synced
configuration.

The latest 500 messages per player are retained for up to 100 players. Older
messages and the least recently active conversations are removed at those limits.
Saves run in the background and are batched over two seconds; a crash can lose
the newest few seconds. Logout and plugin disable queue a final save, and normal
client exit uses RuneLite's asynchronous shutdown hook to finish pending writes.

Turning retention off stops new saves and restores but keeps existing files.
**Delete saved history** removes the current character's file and clears the
display after confirmation. Other characters' files remain. If retention stays
enabled, new messages begin a new saved history. An unreadable history file is
preserved and saving is paused for that character until it can be loaded or is
explicitly deleted. Storage errors are shown in the sidebar.

Build with `javm exec --jdk temurin@11 ./gradlew.bat build` and launch the
development client with `javm exec --jdk temurin@11 ./gradlew.bat run`.
