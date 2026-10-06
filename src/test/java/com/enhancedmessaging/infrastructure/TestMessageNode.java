package com.enhancedmessaging.infrastructure;

import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;
import net.runelite.api.Node;

// Plain test data; mutations are never dispatched to a game client.
public class TestMessageNode implements MessageNode
{
	private int id;
	private ChatMessageType type;
	private String name = "Alice";
	private String sender = "";
	private String value;
	private String runeLiteFormatMessage;
	private int timestamp = 100;

	public TestMessageNode(int id, ChatMessageType type, String value)
	{
		this.id = id;
		this.type = type;
		this.value = value;
	}

	@Override public int getId() { return id; }
	public void setId(int id) { this.id = id; }
	@Override public ChatMessageType getType() { return type; }
	@Override public String getName() { return name; }
	@Override public void setName(String name) { this.name = name; }
	@Override public String getSender() { return sender; }
	@Override public void setSender(String sender) { this.sender = sender; }
	@Override public String getValue() { return value; }
	@Override public void setValue(String value) { this.value = value; }
	@Override public String getRuneLiteFormatMessage() { return runeLiteFormatMessage; }
	@Override public void setRuneLiteFormatMessage(String text) { runeLiteFormatMessage = text; }
	@Override public int getTimestamp() { return timestamp; }
	@Override public void setTimestamp(int timestamp) { this.timestamp = timestamp; }
	@Override public Node getNext() { return null; }
	@Override public Node getPrevious() { return null; }
	@Override public long getHash() { return id; }
}
