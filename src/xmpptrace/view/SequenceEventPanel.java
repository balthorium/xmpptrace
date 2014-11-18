package xmpptrace.view;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import xmpptrace.model.ActorTableModel;
import xmpptrace.model.AddressTableModel;
import xmpptrace.model.PacketTableModel;
import xmpptrace.model.XmppNamespaceContext;
import xmpptrace.model.TcpPacket;

/**
 * Class to display the contents of a PacketTableModel in the form of a
 * sequence diagram.  This class extends the basic Swing JPanel, and does
 * all of its own rendering of actor headers, actor lines, event lines, etc.
 * To do so, it makes use of the ActorDisplayMaster and EventDisplayMaster
 * classes to render the top portion of the panel, and lower portion of the
 * panel, respectively.
 * 
 * @author adb
 *
 */
public class SequenceEventPanel 
	extends JPanel
	implements KeyEventDispatcher, // listen for event from keyboard arrows
			   AdjustmentListener, // listen for scrollbar events
			   TableModelListener  // listen to the PacketTableModel
{
	private static final long serialVersionUID = 1L;
	
	// data models underlying the panel dispaly
	private PacketTableModel mPacketTable;
	private ActorTableModel mActorTable;
	private int mSelectedEvent;
	
	// list of listeners for selection events panel (eg. stanza text pane)
	private ArrayList<ListSelectionListener> mListeners;
	
	// display models to aid in drawing actors and events
	private ActorDisplayMaster mAdm;
	private EventDisplayMaster mEdm;
	
	// scroll bars which control the display
	private JScrollBar mHorizScrollBar;
	private JScrollBar mVertScrollBar;
	
	// full rendered image
	private BufferedImage mFullImage;
	
	// portion of the full image which is currently viewable in the gui
	private BufferedImage mVisibleImage;
	
	// types of search supported by search() method
	enum SearchType 
	{
		REGEX, XPATH
	}
	
	// type representing search result status
	enum SearchResult
	{
		FOUND, NOT_FOUND, BAD_SYNTAX
	}
	
	/**
	 * Ctor.
	 * @param addressTable The address table to be associated with the display
	 * @param packetTable The packet table to be associated with the display
     * @param actorTable The actor table to be associated with the display
	 */
	public SequenceEventPanel(
			AddressTableModel addressTable,
			PacketTableModel packetTable,
			ActorTableModel actorTable)
	{
		super();

		// keep references to the underlying table models
		mPacketTable = packetTable;
		mActorTable = actorTable;
		
		// -1 means no events are currently selected
		mSelectedEvent = -1;
		
		// list of selection listings for this panel
		mListeners = new ArrayList<ListSelectionListener>();
		
		mHorizScrollBar = null;
		mVertScrollBar = null;
		
		// listen to the packet table and actor table
		mPacketTable.addTableModelListener(this);
		
		// need to start with blank image due to how redraw works
		mFullImage = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);
		
		// listen to resize events on this panel, do full redraw on resize
		addComponentListener(new ComponentAdapter() 
		{
			public void componentResized(ComponentEvent e) 
			{
				redrawFullImage();
			}
		});
		
		// listen for mouse events for protocol event selection and focus
		addMouseListener(new MouseAdapter() 
		{
			public void mouseClicked(MouseEvent e) 
			{
				if (mEdm == null || mAdm == null) return;
				
				// TODO: hook this in as a means for selecting actors
				//ActorDisplayMaster.DisplayedActor a = 
				//		mAdm.getActorAt(e.getX(), e.getY());

				int idx = mEdm.getEventAt(e.getY());
				if (idx >= 0)
				{
					int firstVisible = mVertScrollBar.getValue();
					setSelectedEvent(idx + firstVisible);
	    			redrawEventsOnly();
				}
			}
			public void mouseReleased(MouseEvent e) 
			{
				requestFocusInWindow();
			}
		});
		
		// have mouse wheel event scroll the vertical scrollbar
		addMouseWheelListener(new MouseWheelListener() 
		{
			public void mouseWheelMoved(MouseWheelEvent e) 
			{
				mVertScrollBar.setValue(
						mVertScrollBar.getValue() + e.getWheelRotation());
			}
		});
	}
	
	/**
	 * Entangles this event panel with its controlling scrollbars.
	 * @param aHorizScrollBar Controlling horizontal scroll bar
	 * @param aVertScrollBar Controlling vertical scroll bar
	 */
	public void setScrollBars(
			JScrollBar aHorizScrollBar,
			JScrollBar aVertScrollBar) 
	{
		mHorizScrollBar = aHorizScrollBar;
		mHorizScrollBar.addAdjustmentListener(this);
		
		mVertScrollBar = aVertScrollBar;
		mVertScrollBar.addAdjustmentListener(this);
	}

	/**
	 * Adds listener to selections on this event panel.
	 * @param listener New listener to be added.
	 */
	public void addListSelectionListener(ListSelectionListener listener) 
	{
		mListeners.add(listener);
	}
	
	/**
	 * Fires selection list event to all listeners.
	 * @param e Selection event to be fired.
	 */
	private void fireValueChangedEvent(final ListSelectionEvent e) 
	{
		SwingUtilities.invokeLater(new Runnable() 
		{
			public void run() 
			{
				for (ListSelectionListener l: mListeners) 
				{
					l.valueChanged(e);
				}				
			}
		});
	}

	/**
	 * Changes the currently selected event to be that of the event at the 
	 * given index.  Also checks to see if the newly selected event is in
	 * the set of currently displayed events, and if not, will automatically
	 * scroll the display until the newly selected event is visible.
	 * @param idx Index to assign as the newly selected event.
	 */
	private void setSelectedEvent(int idx)
	{
		if ((idx == mSelectedEvent) || (idx < 0) || 
					(idx >= mPacketTable.getRowCount()))
		{
			return;
		}
		
		mSelectedEvent = idx;
		
		// if new selection is close to old selection, just inc/dec to it 
		if (Math.abs(mSelectedEvent - mVertScrollBar.getValue()) 
		        < mEdm.getVisibleEvents(this.mFullImage) + 1)
		{
            while (mSelectedEvent < mVertScrollBar.getValue())
            {   
                mVertScrollBar.setValue(mVertScrollBar.getValue() - 1); 
            }   
                
            while (mSelectedEvent >= mVertScrollBar.getValue() + 
                        mEdm.getVisibleEvents(mFullImage))
            {   
                mVertScrollBar.setValue(mVertScrollBar.getValue() + 1); 
            }   
		}
		// if new selection is fa fa away, just jump to it
        else
        {
            mVertScrollBar.setValue(mSelectedEvent);
        }
        
		redrawEventsOnly();
		
		// xmpp doc listens to selection events on this panel
		fireValueChangedEvent(new ListSelectionEvent(this, idx, idx, false));
	}
	
	/**
	 * Return the index of the currently selected event.
	 * @return Index of the currently selected event.
	 */
	public int getSelectedEvent()
	{
		return mSelectedEvent;
	}
	
	/**
	 * Listen for arrow keypresses, increment and decrement the selected event
	 * accordingly (and readjust the scroll as needed to keep the selected
	 * events visible).
	 * @param e KeyEvent received from ui.
	 * @return true If this listener has consumed the event, false otherwise.
	 */
    public boolean dispatchKeyEvent(KeyEvent e) 
    {
    	if ((e.getSource() == this) && 
    			(e.getID() == KeyEvent.KEY_PRESSED))
    	{
    		if (e.getKeyCode() == 38)
    		{
    			setSelectedEvent(mSelectedEvent - 1);
    			redrawEventsOnly();
    		}
    		else if (e.getKeyCode() == 40)
    		{
    			setSelectedEvent(mSelectedEvent + 1);
    			redrawEventsOnly();
    		}
			return true;
    	}

    	return false;
    }
    
	/**
	 * Listens to change events on the PacketTableModel and ActorTableModel, 
	 * redraws full image.
	 * @param e Table event received.
	 */
	public void tableChanged(TableModelEvent e) 
	{
		redrawFullImage();
		fireValueChangedEvent(new ListSelectionEvent(
				this, mSelectedEvent, mSelectedEvent, false));
	}
		
	/**
	 * Re-draws the full event panel.  This is typically necessary when the
	 * panel is resized, or either the address or packet tables are modified.
	 */
	private void redrawFullImage()
	{
		// create actor and event display models to assist in drawing actors
		Graphics2D g2 = (Graphics2D)mFullImage.getGraphics();
		mAdm = new ActorDisplayMaster(g2, mActorTable);
		mEdm = new EventDisplayMaster(mAdm, mPacketTable);
		
		// figure out what the current visible panel size is
		Dimension visibleDim = getSize();

		// initialize a new full image buffer
		mFullImage = new BufferedImage(
				Math.max(mAdm.getMinWidth(), visibleDim.width),
				Math.max(mAdm.getMinHeight(), visibleDim.height),
				BufferedImage.TYPE_3BYTE_BGR);

		// draw actors to the new image
		mAdm.drawActors(mFullImage);
				
		// draw events to the new image (remember previous scroll location)
		int vertSbValue = mVertScrollBar.getValue();
		if (vertSbValue < 0 || vertSbValue >= mPacketTable.getRowCount())
		{
			vertSbValue = 0;
		}		
		mEdm.drawEvents(mFullImage, vertSbValue, mSelectedEvent);

		// set the vertical scroll bar values based on size of event table.
		// the vertical scroll bar range is equal to the number of events in
		// the packet table, and the value is the index of the topmost
		// displayed event in the panel.
		mVertScrollBar.setValues(vertSbValue, 100, 0, 
					mPacketTable.getRowCount() + 100);

		// set horizontal scroll bar values and range.  the horizontal scroll
		// bar range is equal to the raster width of the full image, minus the
		// width of the visible region of the panel.  the value is the x 
		// coordinate of the leftmost displayed pixels as represented in the
		// full image coordinates.
		int horizSbValue = mHorizScrollBar.getValue();
		if (horizSbValue < 0 || 
					horizSbValue >= mFullImage.getWidth() - visibleDim.width)
		{
			horizSbValue = 0;
		}
		if (visibleDim.width < mFullImage.getWidth())
		{
			mHorizScrollBar.setVisible(true);			
			mHorizScrollBar.setValues(horizSbValue, visibleDim.width, 
					0, mFullImage.getWidth());
			redrawVisibleImage();
		}
		else
		{
			this.mHorizScrollBar.setVisible(false);			
			mVisibleImage = mFullImage;
			repaint();
		}
	}
		
	/**
	 * Redraws only the lower portion of the panel, which displayes event
	 * arrows and text.  Does not update the Actor header blocks.  This is
	 * useful for handling vertical scrollbar events, and when the user is
	 * keying through events.
	 */
	private void redrawEventsOnly()
	{
		if (mEdm != null)
		{
			mEdm.drawEvents(
						mFullImage, mVertScrollBar.getValue(), mSelectedEvent);
		}
		redrawVisibleImage();
	}

	/**
	 * List for scrollbar events.  This is triggered by both vertical and 
	 * horizontal scrollbar events.  For vertical scrolling, we only want
	 * to redraw the events portion of the display, and leave the actor
	 * blocks at the top alone.  For horizontal scrolling, we just need
	 * to change the visibile image (that which paint actuall paints) to
	 * be a new subimage of the full image.
	 * @param e Adjustment event triggered by a scrollbar.
	 */
	public void adjustmentValueChanged(AdjustmentEvent e) 
	{
		if (e.getSource() == mVertScrollBar)
		{
			redrawEventsOnly();
		}
		else
		{
			redrawVisibleImage();
		}
	}
	
	/**
	 * Crops a new subimage from the full image and sets that as the visible
	 * image from which the paint method renders.  Subimage will always be
	 * of size at least 1x1, will have a height equal to the full image,
	 * and a width equal to the current viewable region of the panel.
	 */
	private void redrawVisibleImage()
	{	
		Dimension visibleDim = getSize();
		int horizSbValue = mHorizScrollBar.getValue();
		
		// take some care to ensure we have sane values
		if (visibleDim.width < 1) visibleDim.width = 1;
		if (visibleDim.height < 1) visibleDim.height = 1;
		if (horizSbValue > mFullImage.getWidth() - visibleDim.width) 
			horizSbValue = mFullImage.getWidth() - visibleDim.width;
		if (horizSbValue < 0) horizSbValue = 0;
						
		mVisibleImage = mFullImage.getSubimage(
				horizSbValue, 
				0, 
				visibleDim.width, 
				visibleDim.height);
		
		repaint();
	}	
		
	/**
	 * Standard Swing paint, renders the panel contents to the given graphics
	 * context.
	 * @param g Graphics context on which to render the panel contents.
	 */
	public void paint(Graphics g) 
	{
		if (mVisibleImage != null)
		{
			g.drawImage(mVisibleImage, 0, 0, null);
		}
	}
	
	/**
	 * Performs a search on contents of the packet table.  Searches always
	 * begin at the event immediately following the currently selected event 
	 * (or 0 if none are selected) and continues until a match, or all events 
	 * have failed to produce a match.  Automatically wraps around to the 
	 * beginning if it reaches the last event in the table without a match.  
	 * 
	 * Note - its best not execute this directly on the swing event thread.
     *
	 * @param type The type of search, current options are REGEX, and XPATH.
	 * @param searchString The search query (should be valid based on type).
	 * @return Returns FOUND, NOT_FOUND, or BAD_SYNTAX.  A result of FOUND
	 *         implies that the selected event has been updated to be the
	 *         index of the packet which produced the next match.
	 */
	public SearchResult search(SearchType type, String searchString)
	{
        SearchResult result = SearchResult.NOT_FOUND;
        int matchingRow = -1;
        
        // pop up a progress monitor
        int progress = 0;
        int numPackets = mPacketTable.getRowCount();
        ProgressMonitor pm = new ProgressMonitor(
                xmpptrace.view.XmppTraceFrame.getInstance(),
                "Searching...", null, progress, numPackets);

		try
		{
			Searcher s = null;
			switch(type)
			{
			case REGEX: 
				s = new RegexSearcher(searchString);
				break;
				
			case XPATH:
				s = new XpathSearcher(searchString); 
				break;
			}
			int rowCount = mPacketTable.getRowCount();
			int mStart = mSelectedEvent + 1;
			for (int i = mStart; i < rowCount; ++i)
			{
				if (s.search(i))
				{
					result = SearchResult.FOUND;
					matchingRow = i;
					break;
				}
                pm.setProgress(++progress);
                if (pm.isCanceled()) break;
			}
			if (result == SearchResult.NOT_FOUND && mStart != 0)
			{
				for (int i = 0; i < mStart; ++i)
				{
					if (s.search(i))
					{
						result = SearchResult.FOUND;
						matchingRow = i;
						break;
					}
		            pm.setProgress(++progress);
	                if (pm.isCanceled()) break;
				}
			}
		}
		catch (Exception e) 
		{
			return SearchResult.BAD_SYNTAX;
		}
		
		// set the selected row to be the one that matched
		final int selectRow = matchingRow;
		if (result == SearchResult.FOUND)
		{
		    SwingUtilities.invokeLater(new Runnable()
		    {
		        public void run()
		        {
		            SequenceEventPanel.this.setSelectedEvent(selectRow);
                }
            });
		}
		
		pm.setProgress(numPackets);
		return result;
	}

	/**
	 * Interface to allow for polymorphic treatment of different kinds
	 * of searches by the search() method.
	 */
	private interface Searcher
	{
		public boolean search(int row) throws Exception;
	}
	
	/**
	 * An implementation of Searcher which supplies regex searching.
	 */
	private class RegexSearcher implements Searcher
	{
		private Pattern p;

		public RegexSearcher(String searchString)
		{
			p = Pattern.compile(searchString);
		}
		
		public boolean search(int row) throws Exception
		{
			String tcpData = (String)mPacketTable.getValueAt(
                    row, PacketTableModel.TCPDATA);
			if (tcpData.length() == 0) return false;
			Matcher m = p.matcher(tcpData);
			return m.find();
		}
	}

	/**
	 * An implementation of Searcher which supplies XPath searching.
	 */
	private class XpathSearcher implements Searcher
	{
		private XPathExpression xpe;
		
		public XpathSearcher(String searchString) 
                throws XPathExpressionException
		{
			XPathFactory xpf = XPathFactory.newInstance();
			XPath xp = xpf.newXPath();
			xp.setNamespaceContext(XmppNamespaceContext.getInstance());
			xpe = xp.compile(searchString);
		}

		public boolean search(int row) throws Exception
		{
			boolean found = false;
			TcpPacket packet = mPacketTable.getValueAt(row);
			if (packet.stanzas == null)
			{
				return false;
			}
			for (Document stanza : packet.stanzas)
			{
				NodeList nodes = null;
				nodes = (NodeList)xpe.evaluate(stanza, XPathConstants.NODESET);
				if ((nodes != null) && (nodes.getLength() > 0))
				{
					found = true;
					break;
				}
			}
			return found;
		}
	}	
}
