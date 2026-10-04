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
the plugin's RuneLite configuration. A confirmation explains what is saved before
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

The sidebar uses the standard RuneLite background and message font, with yellow
RuneScape names and small message boxes. An orange **New** pill at the right of a conversation row indicates an
incoming message in a conversation you are not viewing. Opening that conversation
clears the pill. Messages received while its sidebar view is hidden remain unread;
restored history is not marked new. Unread state lasts for the current session.

A small orb at the bottom-right of a chat avatar mirrors your friend list:
green means online, grey means offline. Players outside your friend list or with
unavailable status have no orb. The **New** pill stays separate from the avatar.
Status updates on game ticks and is kept only in memory; logout, account changes,
and connection interruptions clear it. Hidden online status cannot be detected.

Right-click a player in the list or the conversation heading to choose a stock
avatar, import a PNG/JPEG, or reset their avatar. Imports are local to your client,
and assignments are separate for each logged-in game character. They remain when
message history is deleted. Images must be at most 2 MiB and 2048 × 2048 pixels;
the plugin center-crops them to 48 × 48 and keeps a PNG copy under
`.runelite/plugin-data/enhanced-messaging/avatars/`. Moving the original file does
not break the avatar. Reset removes the saved assignment and restores the default.
Files use hashed character/player keys and contain only the small avatar image.

Stock artwork is bundled in `src/main/resources/com/enhancedmessaging/avatars/`
as `default.png`, `knight.png`, `mage.png`, `ranger.png`, `zuk.png`, and `jad.png`. Each asset is an
optimized 48 × 48 PNG, displayed in a 24-pixel circle. All image reads, imports,
and writes run in the background; displayed avatars are cached.
