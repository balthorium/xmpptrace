/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.model;

import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import xmpptrace.store.Database;
import xmpptrace.store.DatabaseListener;

/**
 * Class to represent an ip-to-actor mapping.  Implements the 
 * AbstractTableModel interface such that it is readily displayed using a 
 * JTable.  Each table entry represents a unique socket, and has three fields:
 * 
 *     ip - A string rep of ip and port, unique (r/o)
 *     actor - Actor's name, multiple entries may have the same actor (r/w)
 *     visible - Flag indicating if this actor entry should be displayed (r/w)
 *     
 * The underlying data is pulled from the XmppDumpDatabase singleton.  As this
 * is expected to be a relatively small table, the persistence model is to 
 * perform pass-through writes directly to the database, and to perform full
 * rebuilds of the table model when the resulting update event is received
 * from the database.  Not real efficient, but not worth optimizing now.
 * 
 * @author adb
 */
public class AddressTableModel 
		extends AbstractTableModel
		implements DatabaseListener
{
	private static final long serialVersionUID = 1L;

	// cached address table
	private ArrayList<Address> mAddressArray;

	// index of cached address table, keyed on ip fields
    private HashMap<String,Address> mAddressMap;
    	
	// table column indices
    static public final int VISIBLE = 0;
    static public final int ACTOR = 1;
	static public final int ADDRESS = 2;
	static public final int NUMCOLS = 3;

	/**
	 * Ctor.
	 */
	public AddressTableModel() 
	{
	    mAddressArray = new ArrayList<Address>();
	    mAddressMap = new HashMap<String, Address>();
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
		case ADDRESS: return "Address";
		case ACTOR: return "Actor";
		case VISIBLE: return "Visible";
		}
		return null;
	}

	/**
	 * Required by the TablemModel interface.
	 * @param columnIndex Column whose type is requested.
	 * @return The class type of the column.
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) 
	{
		switch (columnIndex)
		{
		case ADDRESS: return String.class;
		case ACTOR: return String.class;
		case VISIBLE: return Boolean.class;
		}
		return null;
	}

	/**
	 * Required by the TableModel interface.
	 * @return The number of rows in the table.
	 */
	@Override
	public int getRowCount() 
	{
		return mAddressArray.size();
	}

	/**
	 * Required by the TableModel interface.
	 * @param rowIndex The row of the cell whose value is requested.
	 * @param columnIndex The column of the cell whose value is requested.
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) 
	{
		Address a = mAddressArray.get(rowIndex);
		switch (columnIndex)
		{
		case ADDRESS: return a.ip;
		case ACTOR: return a.actor;
		case VISIBLE: return a.visible;
		}
		return null;
	}

	/**
	 * Required by the TableModel interface.
	 * @param rowIndex The row if the cell whose status is requested.
	 * @param columnIndex The column of the cell whose value is requested.
	 * @return True if the cell is editable, false otherwise.
	 */
	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) 
	{
		switch (columnIndex)
		{
		case ADDRESS: return false;
		case ACTOR: return true;
		case VISIBLE: return true;
		}
		return false;
	}

	/**
	 * Required by the TableModel interface.  This is implemented as
	 * a pass-through write directly to the database.  When the db is
	 * updated, it will fire an update event back to this table model,
	 * which will result in an update to the cached data being displayed.
	 * 
	 * @param value The value to assign to the selected cell.
	 * @param rowIndex The row of the selected cell.
	 * @param columnIndex the column of the selected cell.
	 */
	@Override
	public void setValueAt(Object value, int rowIndex, int columnIndex) 
	{
	    Database db = Database.getInstance();
	    
		Address a = mAddressArray.get(rowIndex);
		switch (columnIndex)
		{
		case ACTOR: 
			String actor = (String)value;
			if (actor.length() > 0)
			{
			    db.setAddressActor(a.ip, actor);
			}
			break;
		case VISIBLE: 
		    Boolean visible = (Boolean)value;
		    db.setAddressVisible(a.ip, visible);
			break;
		}
	}
	
	/**
	 * Returns the full set of Addresses in the table, in an array
	 * ordered by display precedence.
	 * @return An array of Address objects.
	 */
	public ArrayList<Address> getAddressArray()
	{
		return mAddressArray;
	}	
	
	/**
	 * Returns a hashmap of all addresses in the table, keyed on ip:port. 
	 * @return A hastable of addresses, keyed on ip:port.
	 */
	public HashMap<String,Address> getAddressMap()
	{
		return mAddressMap;
	}
	
	/**
	 * Assigns an actor name to the specified address.  This method
	 * is used largely by the XmppAugur, in its efforts to identify
	 * and assign useful actor names to each address in the model.
	 * 
	 * @param address The address to modify.
	 * @param actor The actor to assign to that address.
	 */
	public void setActor(String address, String actor)
	{
        // hack: remove unsightly guid from component id
		if ((actor.length() > 32) && (actor.indexOf('.') != -1))
		{
			actor = actor.substring(0, actor.indexOf('.'));
		}
		
		Address a = mAddressMap.get(address);
		if (a.actor.compareTo(actor) != 0)
		{
	       Database db = Database.getInstance();
           db.setAddressActor(a.ip, actor);
		}
	}

	/**
	 * Get the actor assigned to the given address.
	 * If the given address is not found, it will be added.
	 * @param address The address whose actor is requested. 
	 * @return The actor name assigned to the address.
	 */
	public String getActor(String address)
	{
		Address a = mAddressMap.get(address);
		return a.actor;
	}
	
	/**
	 * Get the visibility status of the given address.
	 * If the given address is not found, it will be added.
	 * @param address The address whose visibility is requested. 
	 * @return The visibility status of the address.
	 */
	public boolean getVisible(String address)
	{
		Address a = mAddressMap.get(address);
		return a.visible;
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
     * and replacing it with a new address set from the database.
     */
    private void rebuildTableModel()
    {
        mAddressArray.clear();
        mAddressMap.clear();

        Database db = Database.getInstance();
        
        db.fetchAddresses(mAddressArray);
        for (Address a: mAddressArray)
        {
            mAddressMap.put(a.ip, a);
        }
        
        fireTableChanged(new TableModelEvent(this));
    }
}
