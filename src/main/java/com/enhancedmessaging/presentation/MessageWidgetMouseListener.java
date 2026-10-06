package com.enhancedmessaging.presentation;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import net.runelite.client.input.MouseAdapter;

public class MessageWidgetMouseListener extends MouseAdapter
{
	private final MessageWidgetOverlay overlay;
	private final Consumer<MessageWidgetOverlay.Action> action;
	private MessageWidgetOverlay.Action pressed;
	private boolean suppressClick;

	public MessageWidgetMouseListener(MessageWidgetOverlay overlay, Consumer<MessageWidgetOverlay.Action> action)
	{
		this.overlay = overlay;
		this.action = action;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		pressed = null;
		suppressClick = false;
		int modifiers = InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK;
		if (!event.isConsumed() && event.getButton() == MouseEvent.BUTTON1 && (event.getModifiersEx() & modifiers) == 0)
		{
			pressed = overlay.actionAt(event.getPoint());
			if (pressed != null)
			{
				event.consume();
				suppressClick = true;
			}
		}
		return event;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent event)
	{
		if (pressed != null && event.getButton() == MouseEvent.BUTTON1)
		{
			MessageWidgetOverlay.Action target = pressed;
			pressed = null;
			MessageWidgetOverlay.Action released = overlay.actionAt(event.getPoint());
			boolean accepted = !event.isConsumed() && target.getBounds().contains(event.getPoint())
				&& released != null && target.getPlayer().equals(released.getPlayer())
				&& target.getContextToken() == released.getContextToken();
			event.consume();
			if (accepted) { action.accept(target); }
		}
		return event;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent event)
	{
		if (suppressClick && event.getButton() == MouseEvent.BUTTON1)
		{
			event.consume();
			suppressClick = false;
		}
		return event;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent event)
	{
		if (pressed != null) { event.consume(); }
		return event;
	}
}
