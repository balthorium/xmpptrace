package xmpptrace.view;


import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import xmpptrace.model.PacketTableModel;

/**
 * The EventDisplayMaster is a helper class for the SequenceEventPanel.  This
 * class assists in the rendering of the Event arrows and labels in the 
 * event panel, and the vertical lines that extend down from the actors.
 * 
 * @author adb
 *
 */
class EventDisplayMaster 
{
	// the actor display model that goes with this event display model
	private ActorDisplayMaster mAdm;
	
	// the packet (event) table which is displayed by this event display model
	private PacketTableModel mPacketTable;
	
	// static display parameters
	static public Color sEventArrowColor = Pallette.EVENT;
	static public Color sEventTextColor = Pallette.EVENT_TEXT;
	static public Color sEventSelectedColor = Pallette.SELECTED_EVENT;
	static public int sEventHeight = Pallette.FONT_EVENT_TEXT.getSize() * 4 / 3;
	static public int sMinPanelHeight = 500;
	
	/**
	 * Ctor.
	 * @param adm The ActorDisplayMaster that goes with this EventDisplayMaster.
	 * @param packetTable The PacketTableModel to be display.
	 */
	public EventDisplayMaster(
			ActorDisplayMaster adm,
			PacketTableModel packetTable)
	{
		mAdm = adm;
		mPacketTable = packetTable;
	}
		
	/**
	 * Used to determine which event from the packet table has been selected
	 * by the mouse. 
	 * @param y The y coordinate of the mouse pointer in event panel coords.
	 * @return The index of the selected event among all visible events.
	 */
	int getEventAt(int y)
	{
		int idx = (int)(y - mAdm.getMinHeight()) / sEventHeight;
		if (idx < 0 || idx >= mPacketTable.getRowCount()) idx = -1;
		return idx;
	}
	
	/**
	 * Returns the number of events arrows which can be displayed in the given
	 * image buffer.  Figures this based on the image height of the buffer,
	 * and the amount of space required by the actor header sub panel at the
	 * top.
	 * @param image The image buffer to which events are displayed.
	 * @return Number of events which can be displayed at once on the panel.
	 */
	int getVisibleEvents(BufferedImage image)
	{
		return ((image.getHeight() - mAdm.getMinHeight()) / sEventHeight) - 1;
	}
	
	/**
	 * Draws events to the given image.
	 * @param image The buffered image to draw on.
	 * @param firstEventIndex The index of the first event in the packet model.
	 * @param selectedEventIndex The index of the currently selected event.
	 */
	void drawEvents(
			BufferedImage image, 
			int firstEventIndex,
			int selectedEventIndex) 
	{		
		// clear the event portion of the image for redrawing
		clearEvents(image);
		
		// draw the vertical actor lines
		for (ActorDisplayMaster.DisplayedActor actor : 
				mAdm.getActorsByPrecedence())
		{
			drawActorLine(image, actor);
		}
		
		// draw the event arrows and corresponding text for events
		int eventY = mAdm.getMinHeight() + sEventHeight;
		int numEvents = getVisibleEvents(image);
		int lastEventIndex = firstEventIndex + numEvents;
		if (lastEventIndex > mPacketTable.getRowCount())
		{
			lastEventIndex = mPacketTable.getRowCount();
		}
		for (int i = firstEventIndex; i < lastEventIndex; ++i)
		{
			drawEvent(image, i, eventY, i == selectedEventIndex);
			eventY += sEventHeight;
		}
	}

	/**
	 * Cleans up the given image so that events can be re-rendered. This
	 * only erases the region of the image where events are displayed, the
	 * actor headers are left intact.
	 * @param image The image for which the event display region is cleared.
	 */
	private void clearEvents(
			BufferedImage image)
	{
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setColor(Pallette.BG_EVENT_PANEL);
		g.fillRect(0, mAdm.getMinHeight(), image.getWidth(), 
					image.getHeight() - mAdm.getMinHeight());
	}
	
	/**
	 * Draws a full vertical actor line for a given actor.  The 
	 * ActorDisplayMaster only renders a stub line from underneath the actor 
	 * header boxes.  This extends those lines for the full height of the event 
	 * panel.
	 * @param image The image to which the actor line should be drawn.
	 * @param actor The actor for which a line should be drawn.
	 */
	private void drawActorLine(
			BufferedImage image, 
			ActorDisplayMaster.DisplayedActor actor) 
	{
		Graphics2D g = (Graphics2D) image.getGraphics();
		
		g.setColor(ActorDisplayMaster.sActorColor);
		g.drawLine(actor.x - 1, mAdm.getMinHeight(), 
					actor.x - 1, image.getHeight());
		
		g.setColor(ActorDisplayMaster.sActorColor.brighter());
		g.drawLine(actor.x, mAdm.getMinHeight(), 
					actor.x, image.getHeight());
		
		g.setColor(ActorDisplayMaster.sActorColor);
		g.drawLine(actor.x + 1, mAdm.getMinHeight(), 
					actor.x + 1, image.getHeight());
	}

	/**
	 * Draws a single event arrow, with text.
	 * @param image The image buffer to draw to.
	 * @param eventIndex The index of the packet to be rendered.
	 * @param eventY The y coordinate, in image coords, for the line.
	 * @param isSelected True if this event is "selected".
	 */
	private void drawEvent(
			BufferedImage image, 
			int eventIndex,
			int eventY,
			boolean isSelected) 
	{
		final Graphics2D g = (Graphics2D)image.getGraphics();
		
		// figure out the "to" and "from" actors for this packet
		String fromActorName = (String)mPacketTable.getValueAt(
					eventIndex, PacketTableModel.SENDER_ALIAS);
		
		String toActorName = (String)mPacketTable.getValueAt(
					eventIndex, PacketTableModel.RECIPIENT_ALIAS);
		
		ActorDisplayMaster.DisplayedActor fromActor = 
					mAdm.getActor(fromActorName);
		
		ActorDisplayMaster.DisplayedActor toActor = 
					mAdm.getActor(toActorName);
				
		// select appropriate color for the event line (highlight if selected)
		Color color = sEventArrowColor;
		if (isSelected)
		{
			color = sEventSelectedColor;
		}
		g.setColor(color);
		
		// draw event line and arrowhead
		g.drawLine(fromActor.x, eventY, toActor.x, eventY);
		drawArrowHead(image, toActor.x, eventY, 
				toActor.x > fromActor.x, color);	

		// get text to be displayed 
		String tcpData = (String) mPacketTable.getValueAt(
					eventIndex, PacketTableModel.TCPDATA);
		if (tcpData.length() == 0)
		{
			tcpData = (String) mPacketTable.getValueAt(
					eventIndex, PacketTableModel.TCP_FLAGS);
		}

		// abbreviate arrow text (allow 75% of distance between actor lines)
		g.setFont(Pallette.FONT_EVENT_TEXT);
		if (isSelected)
		{
		    g.setFont(Pallette.FONT_SELECTED_EVENT_TEXT);
		}
		final FontMetrics fm = g.getFontMetrics();
		final int maxWidth = (int) 
				((float)0.75 * Math.abs(toActor.x - fromActor.x));
		tcpData = new Object()
		{
			// returns an abbreviation of the given string
			public String abbreviate(String str)
			{
				int idx = abbvIdx(str, str.length(), str.length() / 2); 
				if (idx < str.length())
				{
					return str.substring(0, idx) + "...";
				}
				else return str;
			}	
			
			// recursive binary search, looking for a good abbv length
			public int abbvIdx(String str, int index, int delta) 
			{ 
				if (delta == 0) return index;
				if (index > str.length()) return str.length();
				if (fm.getStringBounds(
						str.substring(0, index), g).getWidth() > maxWidth)
				{
					return abbvIdx(str, index - delta, delta / 2);
				}
				else
				{
					return abbvIdx(str, index + delta, delta / 2);
				}
			}
		}.abbreviate(tcpData);

		// select appropriate color for the event text (highlight if selected)
		color = sEventTextColor;
		if (isSelected)
		{
			color = sEventSelectedColor;
		}
		g.setColor(color);
		
		// turn on antialiasing just for rendering the text
		g.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		
		// draw event text, centered between vertical actor lines		
		g.drawString(
				tcpData, 
				Math.min(toActor.x, fromActor.x) + 
				(int)(Math.abs(toActor.x - fromActor.x)/2) - 
				(int)(fm.getStringBounds(tcpData, g).getWidth() / 2), 
				eventY - 1);
	}    
	
	/**
	 * Draw an arrowhead to the given image.
	 * @param image Image to which arrowhead should be drawn.
	 * @param aX The x coordinate of the arrowhead tip.
	 * @param aY The y coordinate of the arrowhead tip.
	 * @param pointRight True if arrow points right, false if left.
	 * @param color Color to use when drawing arrowhead.
	 */
	void drawArrowHead(
			BufferedImage image, 
			int aX, 
			int aY, 
			boolean pointRight, 
			Color color)
    {   		
        int x[] = new int[9];
        int y[] = new int[7];
            
        if (pointRight)
        {
        	for (int i = 0; i < 9; ++i) x[i] = aX - 8 + i;
        	for (int i = 0; i < 7; ++i) y[i] = aY - 3 + i;
        }
        else
        {
        	for (int i = 0; i < 9; ++i) x[i] = aX + 8 - i;
        	for (int i = 0; i < 7; ++i) y[i] = aY + 3 - i;
        }
                
        image.setRGB(x[0], y[0], color.getRGB()); 
        image.setRGB(x[1], y[0], color.getRGB()); 
        image.setRGB(x[2], y[0], color.getRGB());
        
        image.setRGB(x[1], y[1], color.getRGB()); 
        image.setRGB(x[2], y[1], color.getRGB()); 
        image.setRGB(x[3], y[1], color.getRGB());

        image.setRGB(x[2], y[2], color.getRGB()); 
        image.setRGB(x[3], y[2], color.getRGB()); 
        image.setRGB(x[4], y[2], color.getRGB());
        image.setRGB(x[5], y[2], color.getRGB());
 
        image.setRGB(x[3], y[3], color.getRGB()); 
        image.setRGB(x[4], y[3], color.getRGB()); 
        image.setRGB(x[5], y[3], color.getRGB());
        image.setRGB(x[6], y[3], color.getRGB());
        image.setRGB(x[7], y[3], color.getRGB());
        image.setRGB(x[8], y[3], color.getRGB());
        
        image.setRGB(x[2], y[4], color.getRGB()); 
        image.setRGB(x[3], y[4], color.getRGB()); 
        image.setRGB(x[4], y[4], color.getRGB());
        image.setRGB(x[5], y[4], color.getRGB());
        
        image.setRGB(x[1], y[5], color.getRGB()); 
        image.setRGB(x[2], y[5], color.getRGB()); 
        image.setRGB(x[3], y[5], color.getRGB());

        image.setRGB(x[0], y[6], color.getRGB()); 
        image.setRGB(x[1], y[6], color.getRGB()); 
        image.setRGB(x[2], y[6], color.getRGB());
    }
}
