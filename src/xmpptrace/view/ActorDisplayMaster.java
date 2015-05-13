/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.view;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.TreeMap;

import xmpptrace.model.ActorTableModel;

/**
 * The ActorDisplayMaster is a helper class for the SequenceEventPanel.  This
 * class assists in the rendering of the Actor boxes at the top of the 
 * event panel, and the vertical lines that extend downward from them.
 * This class also supplies authoritative values for the minumim width and
 * height required for this Actor display portion of the panel.
 * 
 * @author adb
 *
 */
class ActorDisplayMaster 
{
	// DisplayedActors, indexed by name
	private TreeMap<String, DisplayedActor> mActorsByName;
	
	// DisplayedActors, ordered by display precedence
	private ArrayList<DisplayedActor> mActorsByPrecedence;
	
	// minimum width and height required by the actor portsion of the display
	private int mWidth;
	private int mHeight;

	// static display parameters
	static public Color sActorColor = Pallette.ACTOR;
	static public Color sActorSelectedColor = Pallette.SELECTED_ACTOR;
	static public int sActorBoxPadX = 20;
	static public int sMinPanelWidth = 1170;
	static public int sMinActorSpace = 5;
	static public int sMinActorWidth = 200;
	static public int sBoxCenterLineY = 30;
	static public int sActorBoxHeight = 30;
	static public int sActorBoxHalfHeight;

	// shading gradient vectors for actor box
	private static float[] sVertLineShade;
	private static float[][] sEndCapShade;
	
	// precalculate shading gradients for actor boxes
	static
	{
		// used alot, so keep it in a static
		sActorBoxHalfHeight = sActorBoxHeight / 2;

		// generate gradient values for shading an actor box vertical line
		sVertLineShade = new float[sActorBoxHeight];
		for (int i = 0; i < sActorBoxHeight; ++i)
		{
			sVertLineShade[i] = 1 - (float)Math.sin(i * Math.PI / sActorBoxHeight);
		}

		// generate gradient values for shading an actor box end cap
		int hh = sActorBoxHalfHeight;
		sEndCapShade = new float[hh][sActorBoxHeight];
		for (int i = 0; i < sActorBoxHeight; ++i)
		{
			for (int j = 0; j < hh; ++j)
			{
				float d = (float)Math.sqrt(
                        Math.pow(j-hh, 2) + Math.pow(i-hh, 2));
				if (d <= hh)  
				{
					sEndCapShade[j][i] = 1 - (float)Math.cos(d * Math.PI / (2*hh));
				}
				else if (d <= (hh + 0.5))
				{
				    sEndCapShade[j][i] = (float)Math.pow(1 + 2 * (hh - d), 2);
				}
				else
				{
					sEndCapShade[j][i] = 0;
				}
			}
		}
	}

	// Private class to represent each actor being displayed.
	public static class DisplayedActor 
	{
		// display name of the actor
		public String name;
		
		// the x coordinate defining the centerline of the actor
		public int x;
		
		// flag to indicate if this actor has been marked as selected
		public boolean selected;

		public DisplayedActor(String aName) 
		{
			name = aName;
			x = 0;
			selected = false;
		}
	}

	/**
	 * Ctor.
	 * @param g A graphics object which can be used for computing 
	 * 		  displayed text widths
	 * @param actorTableModel  The actor table model associated with the
	 *        actors to be display.
	 */
	ActorDisplayMaster(
			Graphics2D g, 
			ActorTableModel actorTableModel) 
	{
		mActorsByName = new TreeMap<String, DisplayedActor>();
		mActorsByPrecedence = new ArrayList<DisplayedActor>();
		for (String actor: actorTableModel.getActorArray())
		{
			DisplayedActor seqActor = new DisplayedActor(actor);
			mActorsByName.put(actor, seqActor);
			mActorsByPrecedence.add(seqActor);
		}

		// figure out the actor header total width
		g.setFont(Pallette.FONT_ACTOR_NAME);
		FontMetrics fontmetrics = g.getFontMetrics();
		int actorTextWidthMax = 0;
		for (String alias : mActorsByName.keySet()) 
		{
			Rectangle2D actorTextRect = fontmetrics.getStringBounds(alias, g);
			if (actorTextWidthMax < actorTextRect.getWidth()) 
			{
				actorTextWidthMax = (int)actorTextRect.getWidth();
			}
		}
		int maxActorWidth = 
                actorTextWidthMax + 2 * sActorBoxPadX + sMinActorSpace;
		if (maxActorWidth < sMinActorWidth)
		{
			maxActorWidth = sMinActorWidth;
		}
		mWidth = maxActorWidth * mActorsByName.size();
		if (mWidth < sMinPanelWidth) 
		{
			mWidth = sMinPanelWidth;
		}
		
		// figure out actor header height
		mHeight = sBoxCenterLineY + sActorBoxHeight;

		// compute location of actor vertical lines
		int numActors = mActorsByPrecedence.size();
		if (numActors > 0)
		{
			int i = 0;
			int trueActorWidth = mWidth / numActors;
			for (DisplayedActor actor : mActorsByPrecedence)
			{
				actor.x = (int) ((i + 0.5) * trueActorWidth);
				++i;
			}
		}
	}

	/**
	 * Returns the DisplayedActor object reference matching the given name.
	 * @param name Actor name.
	 * @return The matching DisplayedActor object, or null if no match.
	 */
	DisplayedActor getActor(String name)
	{
		return mActorsByName.get(name);
	}
	
	/**
	 * Returns the set of all actors, ordered by display precedence.
	 * @return Array of all actors, ordered by display precedence.
	 */
	ArrayList<DisplayedActor> getActorsByPrecedence()
	{
		return mActorsByPrecedence;
	}
	
	/**
	 * Returns the minimum display panel width needed to display all actors.
	 * @return Minimum display panel width needed to display all actors.
	 */
	int getMinWidth()
	{
		return mWidth;
	}
	
	/**
	 * Returns the minimum display panel height needed to display all actors.
	 * @return Minimum display panel height needed to display all actors.
	 */
	int getMinHeight()
	{
		return mHeight;
	}
	
	/**
	 * Draws the actor header to a given image buffer.  The given image must
	 * have dimensions that meet the minimum required, as given by getMinWidth
	 * and getMinHeight.
	 * @param image The image to which the actor header will be drawn.
	 */
	void drawActors(BufferedImage image) 
	{
		clearActors(image);

		if (image.getWidth() < mWidth || image.getHeight() < mHeight)
		{
			System.err.println("ActorDisplayMaster.drawActors: image too small");
			return;
		}
		
		for (DisplayedActor actor : mActorsByPrecedence)
		{
			drawActor(image, actor);
		}
	}
	
	/**
	 * Cleans up the given image so that actors can be re-rendered. This
	 * only erases the region of the image where actors are displayed, the
	 * events section is left intact.
	 * @param image The image for which the actor header region is cleared.
	 */
	private void clearActors(
			BufferedImage image)
	{
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setColor(Pallette.BG_EVENT_PANEL);
		g.fillRect(0, 0, image.getWidth(), getMinHeight());
	}
	
	/**
	 * Draw a single actor to the given image buffer.  This includes the 
	 * actor box, display name text, and vertical actor line down as far
	 * as the minimum height of the actor header.
	 * @param image Buffered image to which actor will be drawn.
	 * @param a The DisplayActor record for the actor that is to be drawn.
	 */
	private void drawActor(BufferedImage image, DisplayedActor a)
	{
		// compute size and positioning for actor name text
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setFont(Pallette.FONT_ACTOR_NAME);
		Rectangle2D textRect = g.getFontMetrics().getStringBounds(a.name, g);
		int textX = a.x - (int) (textRect.getWidth() / 2);
		int textTopY = sBoxCenterLineY + (int) (textRect.getHeight() / 2);

		// select appropriate color for the actor
		Color c = sActorColor;
		if (a.selected)
		{
			c = sActorSelectedColor;
		}
		
		// draw actor box
		g.setColor(c);
		int boxWidth = (int) (textRect.getWidth() + 2 * sActorBoxPadX);
		drawActorBox(image, 
				a.x - (int) (boxWidth / 2),
				sBoxCenterLineY - sActorBoxHalfHeight,
				boxWidth);

		// draw actor line
		g.setColor(c);
		g.drawLine(a.x - 1, sBoxCenterLineY + sActorBoxHalfHeight, 
				   a.x - 1, sBoxCenterLineY + sActorBoxHeight);
		
		g.setColor(c.brighter());
		g.drawLine(a.x, sBoxCenterLineY + sActorBoxHalfHeight, 
				   a.x, sBoxCenterLineY + sActorBoxHeight);
		
		g.setColor(c);
		g.drawLine(a.x + 1, sBoxCenterLineY + sActorBoxHalfHeight, 
				   a.x + 1, sBoxCenterLineY + sActorBoxHeight);
	
		// draw actor text
		g.setColor(Pallette.ACTOR_TEXT);
		g.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.drawString(a.name, textX, textTopY - 2); 
	}
		
	/**
	 * Draw an actor box to the given image, with the given dimensions.
	 * @param image Image to which the box should be drawn.
	 * @param x Horizontal coordinate of the left edge of the box.
	 * @param y Vertical coordinate of the top edge of the box.
	 * @param w Width of the box.
	 */
	private void drawActorBox(BufferedImage image, int x, int y, int w)
	{
		// iterate over actor box drawing area, and fill in pixels
		float shade = (float) 0.0;
		int hh = sActorBoxHeight / 2;
		for (int i = 0; i < sActorBoxHeight; ++i)
		{
			for (int j = 0; j < w; ++j)
			{
				// left end cap
				if (j < hh)
				{
					shade = sEndCapShade[j][i];
				}
				// right end cap
				else if (j >= w - hh)
				{
					shade = sEndCapShade[w - j - 1][i];
				}
				// center portion
				else
				{
					shade = sVertLineShade[i];
				}
				// paint pixel
				int red = (int) (Pallette.ACTOR.getRed() * shade + 
                        Pallette.BG_EVENT_PANEL.getRed() * (1 - shade));
				int green = (int) (Pallette.ACTOR.getGreen() * shade + 
                        Pallette.BG_EVENT_PANEL.getGreen() * (1 - shade));
				int blue = (int) (Pallette.ACTOR.getBlue() * shade + 
                        Pallette.BG_EVENT_PANEL.getBlue() * (1 - shade));
				image.setRGB(x + j, y + i, (red << 16) + (green << 8) + blue);
			}
		}
	}	
	
	/**
	 * For the given mouse coordinates, returns the actor which is closest
	 * to that coordinate.
	 * @param x The x coordinate of the mouse.
	 * @param y The y coordinate of the mouse.
	 * @return The actor which is closest to (x,y), or null if no actors.
	 */
	DisplayedActor getActorAt(int x, int y)
	{
		if (y > mHeight) return null;

		int closestDist = Integer.MAX_VALUE;
		DisplayedActor closest = null;
		for (DisplayedActor actor: mActorsByPrecedence)
		{
			int dist = Math.abs(actor.x - x); 
			if (dist < closestDist)
			{
				closest = actor;
				closestDist = dist;
			}
		}
		return closest;
	}
}
