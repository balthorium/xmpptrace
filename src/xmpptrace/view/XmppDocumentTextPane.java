/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.view;

import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JTextPane;

/**
 * Class to extend the basic JTextPane, and provide minor adjustments to
 * establish desired UI behavior.  One is to ensure that the xmpp text
 * pane is always at least as wide as the space it has been allocated in
 * the window.  Second, we need to have the pane request focus when it is
 * clicked by the mouse, so that users can select/cut/copy segments of
 * text from the xmpp stanzas.
 * 
 * @author adb
 */
public class XmppDocumentTextPane 
		extends JTextPane 
		implements MouseListener
{
	private static final long serialVersionUID = 1L;

	/**
	 * Ctor.
	 */
	public XmppDocumentTextPane()
	{
		addMouseListener(this);
	}
	
	/**
	 * We want the xmpp text to not wrap around, but rather extend as needed,
	 * and for a horizontal scrollbar to appear when needed.  By overriding
	 * this method, we disable the default behavior which has the JTextPane
	 * size forced to fit the viewing area of the scroll pane (causing wrap).
	 */
	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return false;
	}

	/**
	 * We have to override this because the override of 
	 * getScrollableTracksViewportWidth() makes it possible now for the text
	 * pane to actually be smaller than the scroll pane viewport (which looks
	 * bad).  So this will ensure that the size of the XmppDocumentTextPane will
	 * always be at least as wide as the parent scroll pane, even when it
	 * doesn't actually require that much space to fit the text.
	 * @param d The size to assign to this component.
	 */
	@Override
	public void setSize(Dimension d)
	{
		if (d.width < getParent().getSize().width)
			d.width = getParent().getSize().width;

		super.setSize(d);
	}

	/**
	 * Required by the MouseListener interface. No-op.
	 * @param e The received event.
	 */
	@Override
	public void mouseClicked(MouseEvent e) {}

	/**
	 * Required by the MouseListener interface. No-op.
	 * @param e The received event.
	 */
	@Override
	public void mouseEntered(MouseEvent e) {}

	/**
	 * Required by the MouseListener interface. No-op.
	 * @param e The received event.
	 */
	@Override
	public void mouseExited(MouseEvent e) {}

	/**
	 * Required by the MouseListener interface. No-op.
	 * @param e The received event.
	 */
	@Override
	public void mousePressed(MouseEvent e) {}

	/**
	 * Required by the MouseListener interface.  Implemented to 
	 * request focus when the mouse button is released.
	 * @param e The received event.
	 */
	@Override
	public void mouseReleased(MouseEvent e) 
	{
		requestFocusInWindow();
	}
}