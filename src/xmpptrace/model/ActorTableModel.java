/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.model;

import java.util.ArrayList;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;

import xmpptrace.store.Database;
import xmpptrace.store.DatabaseListener;

/**
 * The contents of the ActorTableModel is an ordered set of actor names,
 * corresponding to those marked as "visible" in the address table. The
 * user controls the ordering, as this is used to establish the appropriate
 * left-to-right sequencing of actors in the display.  This class implements 
 * the usual Swing TableModel interface, so that the contents of the model 
 * may be displayed by a JTable, or any other visual component.
 */
public class ActorTableModel 
	extends AbstractTableModel
	implements DatabaseListener
{
	// list of strings representing actor names
	private ArrayList<String> mActorsArray;
	
	// table column indicies
	static public final int ACTOR = 0;

	private static final long serialVersionUID = 1L;
	
	/**
	 * Ctor.
	 */
	public ActorTableModel()
	{
	    mActorsArray = new ArrayList<String>();
	    rebuildTableModel();
	}

	/**
	 * Returns the array underlying this ActorTableModel.
	 * @return The array of actors underlying this ActorTableModel.
	 */
	public ArrayList<String> getActorArray()
	{
		return mActorsArray;
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
		case ACTOR: return "Actor Display Order";
		}
		return null;
	}
	
	/**
	 * Returns the number of columns in this table.
	 * @return Number of columns in this table.
	 */
	@Override
	public int getColumnCount() 
	{
		return 1;
	}

	/**
	 * Returns the number of rows in this table.
	 * @return Number of rows in this table.
	 */
	@Override
	public int getRowCount() 
	{
		return mActorsArray.size();
	}

	/**
	 * Returns an object representing the value contained within the table cell
	 * at the given row and column indexes.
	 * @param rowIndex The requested cell's row index.
	 * @param columnIndex The requested cell's column index.
	 * @return An object representing the value of the cell.
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) 
	{
		if ((rowIndex < 0) || (rowIndex >= mActorsArray.size())) return null;
		return mActorsArray.get(rowIndex);
	}
	
	/**
	 * Decrements the given row's order in the table.
	 * @param rowIndex Current row index.
	 * @return New index of same row.
	 */
	public int moveUp(int rowIndex)
	{
		return moveRow(rowIndex, rowIndex - 1);
	}

	/**
	 * Increments the given row's order in the table.
	 * @param rowIndex Current row index.
	 * @return New index of same row.
	 */
	public int moveDown(int rowIndex)
	{		
		return moveRow(rowIndex, rowIndex + 1);
	}

	/**
	 * Moves the given row to the 0 position in the table.
	 * @param rowIndex Current row index.
	 * @return New index of same row.
	 */
	public int moveTop(int rowIndex)
	{		
		return moveRow(rowIndex, 0);
	}

	/**
	 * Moves the given row to the size-1 position in the table.
	 * @param rowIndex Current row index.
	 * @return New index of same row.
	 */
	public int moveBottom(int rowIndex)
	{		
		return moveRow(rowIndex, mActorsArray.size() - 1);
	}

	/**
	 * Moves a row from one index to another.  If indicies are
	 * not adjacent, this will shift all intermediate rows as
	 * needed.
	 * @param rowIndex Current row index.
	 * @return New index of same row.
	 */
	private int moveRow(int oldRowIndex, int newRowIndex)
	{
		if ((oldRowIndex < 0) || oldRowIndex >= mActorsArray.size())
			return -1;
		
		if (newRowIndex == oldRowIndex ||
				newRowIndex < 0 || 
				newRowIndex >= mActorsArray.size())
			return oldRowIndex;
	
		if (oldRowIndex < newRowIndex)
		{
			for (int i = oldRowIndex; i < newRowIndex; ++i)
			{
				String tmp = mActorsArray.get(i+1);
				mActorsArray.set(i+1, mActorsArray.get(i));
				mActorsArray.set(i, tmp);
			}
		}
		else
		{
			for (int i = oldRowIndex; i > newRowIndex; --i)
			{
				String tmp = mActorsArray.get(i-1);
				mActorsArray.set(i-1, mActorsArray.get(i));
				mActorsArray.set(i, tmp);
			}			
		}
		
		Database db = Database.getInstance();
		db.setActorsList(mActorsArray);
		return newRowIndex;
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
        mActorsArray.clear();
        Database db = Database.getInstance();
        db.fetchActors(mActorsArray);
        fireTableChanged(new TableModelEvent(this));
    }
}
