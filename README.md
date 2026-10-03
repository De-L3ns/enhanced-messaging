# Enhanced Messaging
Private message conversations in the RuneLite sidebar.

Enable **Enhanced Messaging**, open its chat bubble sidebar tab, and send or
receive private messages through the normal game chat. Select a player to view
their incoming and outgoing messages with timestamps. Conversations with recent
activity appear first.

History is kept in memory while you are logged in and the plugin is enabled.
World hops retain history; logging out, disabling the plugin, or restarting
RuneLite clears it. The latest 500 messages per player are retained for up to
100 players; the least recently active conversation is removed when that limit
is reached. History begins when the plugin is enabled.

Build with `javm exec --jdk temurin@11 ./gradlew.bat build` and launch the
development client with `javm exec --jdk temurin@11 ./gradlew.bat run`.
