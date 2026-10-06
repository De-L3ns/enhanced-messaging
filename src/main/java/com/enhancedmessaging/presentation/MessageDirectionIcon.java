package com.enhancedmessaging.presentation;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

final class MessageDirectionIcon implements Icon
{
	private final boolean outgoing;

	MessageDirectionIcon(boolean outgoing) { this.outgoing = outgoing; }

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.translate(x, y);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(outgoing ? MessageStyle.OUTGOING_ACCENT : MessageStyle.INCOMING_ACCENT);
			g.setStroke(new BasicStroke(1.4f));
			g.drawLine(1, 5, 9, 5);
			int tip = outgoing ? 9 : 1;
			int tail = outgoing ? 6 : 4;
			g.drawLine(tail, 2, tip, 5);
			g.drawLine(tip, 5, tail, 8);
		}
		finally { g.dispose(); }
	}

	@Override public int getIconWidth() { return 11; }
	@Override public int getIconHeight() { return 11; }
}
