package xmpptrace.model;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import xmpptrace.store.Database;
import xmpptrace.store.DatabaseListener;


/**
 * Class to represent the full set of visible packets from the
 * xmppdump database.  It implements the TableModel by extending
 * the AbstractTableModel, such that it can be displayed using
 * a JTable (although not actually doing that currently).  It
 * also implements the TableModelListener, such that it can 
 * be alerted to changes in its associated ActorTableModel.
 * 
 * @author adb
 */
public class PacketTableModel 
		extends AbstractTableModel 
		implements DatabaseListener
{
	private static final long serialVersionUID = 1L;

	private static final long MAX_CACHED_PACKETS = 50;
	
	// list of "pacno" values of visible packets, in sequence
	private ArrayList<Integer> mPacnoList;
	
	// mapping from database "pacno" to cached TcpPacket
	private TreeMap<Integer, TcpPacket> mPacketCache;
	
	// lru queue for aging out cached packets
	private ArrayDeque<TcpPacket> mPacketCacheLru;
	
	// table of all addresses to and from which this table's packets flow
	private AddressTableModel mAddressTable;
	
	// display format for packet dates
	private SimpleDateFormat mDateFormat;
	
	// table column indices
	static public final int TIME = 0;
	static public final int SENDER_ALIAS = 1;
	static public final int SENDER_ADDRESS = 2;
	static public final int RECIPIENT_ALIAS = 3;
	static public final int RECIPIENT_ADDRESS = 4;
	static public final int TCP_FLAGS = 5;
	static public final int SEQ_NO = 6;
	static public final int ACK_NO = 7;
	static public final int LENGTH = 8;
	static public final int TCPDATA = 9;
	static public final int NUMSTANZAS = 10;
	static public final int NUMCOLS = 11;
	
	/**
	 * Ctor.  
	 * @param addressTable The address table corresponding to the packets.
	 */
	public PacketTableModel(AddressTableModel addressTable) 
	{
	    mPacnoList = new ArrayList<Integer>();
	    mPacketCache = new TreeMap<Integer, TcpPacket>();
	    mPacketCacheLru = new ArrayDeque<TcpPacket>();
	    
        mAddressTable = addressTable;
		mDateFormat = new SimpleDateFormat("(MM/dd) HH:mm:ss.SSSZ");
		rebuildTableModel();
	}
	
	/**
	 * Required by the TableModel interface.
	 * @return int The number of columns represented by this TableModel.
	 */
	@Override
	public int getColumnCount() 
	{
		return NUMCOLS;
	}

	/**
	 * Required by the TableModel interface. 
	 * @param columnIndex Column whose name is requested.
	 * @return The name of the column.
	 */
	@Override
	public String getColumnName(int columnIndex) 
	{
		switch (columnIndex)
		{
		case TIME: return "Time";
		case SENDER_ALIAS: return "Sender Alias";
		case SENDER_ADDRESS: return "Sender Address";
		case RECIPIENT_ALIAS: return "Recipient Alias";
		case RECIPIENT_ADDRESS: return "Recipient Address";
		case TCP_FLAGS: return "TCP Flags";
		case SEQ_NO: return "Seq No.";
		case ACK_NO: return "Ack No.";
		case LENGTH: return "Length";		
		case TCPDATA: return "TCP Data";
		case NUMSTANZAS: return "Number of Stanzas";
		}
		return null;
	}

	/**
	 * Required by the TableModel interface.
	 * @return The number of visible rows in the table.
	 */
	@Override
	public int getRowCount()
	{
		return mPacnoList.size();
	}

	/**
	 * Required by the TableModel interface.
	 * @param rowIndex The visible row of the cell whose value is requested.
	 * @param columnIndex The column of the cell whose value is requested.
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) 
	{
	    TcpPacket p = this.getValueAt(rowIndex);
	    if (p == null)
	    {
	        return null;
	    }
		switch (columnIndex)
		{
		case TIME: return mDateFormat.format(p.time);
		case SENDER_ALIAS: return mAddressTable.getActor(p.src);
		case SENDER_ADDRESS: return p.src;
		case RECIPIENT_ALIAS: return mAddressTable.getActor(p.dst);
		case RECIPIENT_ADDRESS: return p.dst;
		case TCP_FLAGS: return p.getTcpFlags();
		case SEQ_NO: return p.seqno;
		case ACK_NO: return p.ackno;
		case LENGTH: return p.pktlen;
		case TCPDATA: return p.data;
		case NUMSTANZAS: return (p.stanzas != null) ? 
				String.valueOf(p.stanzas.size()) : "0";
		}
		return null;
	}

	/**
	 * Required by the TableModel interface.
	 * @param row The visible row if the cell whose status is requested.
	 * @param col The column of the cell whose value is requested.
	 * @return True if the cell is editable, false otherwise.
	 */
	@Override
	public boolean isCellEditable(int row, int col) 
	{
		return false;
	}

	/**
	 * Returns the TcpPacket from the given visible row.  Implementation
	 * is based on lazy and limited caching, where packets are aged out
	 * base on least recent usage.
	 * 
	 * This is synchronized, due to two facts:
	 * (1) this method does lazy-fetch cacheing, and LRU age-out.
	 * (2) it is invoked both from the swing thread, and search bg thread
	 * 
	 * @param rowIndex The visible row index of the requested packet.
	 * @return The packet at the given visible row index.
	 */
    synchronized public TcpPacket getValueAt(int rowIndex)
    {
        if (rowIndex < 0 || rowIndex >= mPacnoList.size())
        {
            return null;
        }

        int pacno = mPacnoList.get(rowIndex);
        TcpPacket p = mPacketCache.get(pacno);
        if (p == null)
        {
            // fetch the packet from database, and cache
            Database db = Database.getInstance();
            p = db.getPacket(pacno);
            if (p != null)
            {
                mPacketCache.put(pacno, p);
                mPacketCacheLru.addLast(p);
                
                // if we're at the max cache size, age our lru
                if (mPacketCacheLru.size() > MAX_CACHED_PACKETS)
                {
                    mPacketCacheLru.removeFirst();
                    mPacketCache.remove(pacno);
                }
            }
        }
        else
        {
            // move selected packet to end of lru queue
            if (mPacketCacheLru.remove(p))
            {
                mPacketCacheLru.addLast(p);
            }
        }

        // sanity check the cache
        if (mPacketCache.size() != mPacketCacheLru.size() ||
                mPacketCache.size() > MAX_CACHED_PACKETS)
        {
            System.err.println("Packet cache inconsistency detected.");
            System.err.println("Max cache size: " + MAX_CACHED_PACKETS);
            System.err.println("Cache size: " + mPacketCache.size());
            System.err.println("LRU size: " + mPacketCacheLru.size());
        }
        
        return p;
    }
	
	/**
	 * Provides access to this packet table's corresponding address table.
	 * @return The address table for this packet table.
	 */
	public AddressTableModel getAddressTableModel()
	{
		return mAddressTable;
	}
	
	/**
     * Invoked when the underlying database has been updated.  This
     * will respond to the event by rebuilding the table model.
     */
    @Override
    public void onDatabaseUpdate()
    {
        rebuildTableModel();
    }
    
    /**
     * Rebuilds the table model by dropping the currently cached data
     * and retrieving a new pacno list of visible packets from the db.
     */
    private void rebuildTableModel()
    {
        mPacnoList.clear();
        mPacketCache.clear();
        mPacketCacheLru.clear();

        Database db = Database.getInstance();
        db.getVisiblePackets(mPacnoList);

        fireTableChanged(new TableModelEvent(this));
    }
}
