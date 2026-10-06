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
the **General** section of the plugin's RuneLite configuration. A confirmation explains what is saved before
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
RuneScape names and small message boxes. Incoming messages use charcoal boxes
on the left; outgoing messages use muted blue boxes on the right. Sender labels
are omitted, while timestamps remain. Centred date labels separate messages by
local calendar day, including the first visible day. New messages in the selected
conversation always scroll the transcript to the latest entry.
An orange **New** pill at the right of a conversation row indicates an
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

Enable **Message widget > Enable widget** in RuneLite's plugin configuration to
show a movable panel inside the game window. Hold the overlay drag hotkey (Alt by
default) to move it, or drag an edge or corner to resize either layout. RuneLite
remembers position and size. Alt + right-click resets both. In the regular layout,
width stays within 180-360 pixels and height is at least 60 pixels. Each chat
keeps its own grey box with transparent gaps; there is no title or outer background.

Configuration starts with enabling the widget and choosing its layout, followed
by visible chat count (1-10), recent previews
per chat (0-3), avatars, friend status, unread indicators, and whether clicking a
chat opens its sidebar view. Defaults are three chats, one preview, and a
240-pixel regular width. Size is controlled through Alt-dragging rather than a
width setting. Switching layouts resets the dragged size. All other settings
remain in RuneLite configuration.

Enable **Low footprint** for a transparent, resizable grid showing only circular
avatars and unread indicators. Chats fill left to right, then wrap onto the next
row as you narrow the widget. Widen it for a horizontal strip or narrow it for a
vertical stack. Names, message previews, status orbs, chat backgrounds and footer
text are hidden. Avatars are always shown, even if **Show avatars** is disabled.
**Visible chats**, **Show New indicator**, and **Click opens sidebar** still apply.
An empty grid is hidden. Compact height can shrink to 40 pixels.

Choose **Unread indicator style > New pill** (the default) or **Glowing avatar**.
Glow uses a soft, steady gold halo around unread avatars instead of a label.
It applies in both layouts and clears when the sidebar conversation is read.
When unread indicators are enabled, choosing Glow also shows avatars in the
regular layout even if **Show avatars** is disabled. **Show New indicator** turns
off either indicator without changing unread state.

Glow cells need less width than pill cells. For four chats, these dragged sizes
fit the complete grid (width x height in pixels):

| Indicator | Two columns, two rows | Four columns, one row |
| --- | --- | --- |
| New pill | 148 x 80 | 292 x 44 |
| Glowing avatar | 76 x 80 | 148 x 44 |

The latest incoming message's sender always takes the first widget slot. With
**Visible chats** set to one, a message from another player replaces the current
row or compact avatar. Remaining slots show recent conversations. Reading the
conversation or replying does not displace the latest sender; the next incoming
message selects the next sender. Restored history does not count as a new incoming
message, and logout or clearing history resets this priority.

Regular previews show incoming messages in the standard text colour and outgoing
messages in muted blue, without sender prefixes. Each message occupies at most
two lines, with long text shortened. Viewing either widget layout never marks
messages read; opening the sidebar conversation does. Widget rows and compact
avatars are passive when **Click opens sidebar** is off.

If the widget exceeds its resized height or the game window height, only complete
chats are shown and hidden cells cannot be clicked. The regular layout displays
**More chats in sidebar** when there is spare room beneath visible chats. This
footer never hides a chat that fits. The widget hides on logout and mouse actions
are removed when the plugin stops. Drawing uses cached pixels with no disk access
or history scanning. Resizing rebuilds the compact grid and hit areas on the UI
thread. Glow is cached too, and compact mode skips message-preview snapshots.
