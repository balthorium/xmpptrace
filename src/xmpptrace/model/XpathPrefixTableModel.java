package xmpptrace.model;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.table.AbstractTableModel;

/**
 * This is an adapter class, intended to supply a TableModel wrapper around
 * the XmppNamespaceContext class.  This allows the namespace/prefix mappings
 * to be displayed in the ui as an editable table.
 * 
 * @author adb
 */
public class XpathPrefixTableModel 
		extends AbstractTableModel
{
	private static final long serialVersionUID = 1L;
	static public final int NS = 0;
	static public final int PREFIX = 1;
	static public final int NUMCOLS = 2;
	
	private ArrayList<TableEntry> mTable;

	private class TableEntry
	{
		public TableEntry(String aPrefix, String aNs)
		{
			prefix = aPrefix;
			ns = aNs;
		}
		String ns;
		String prefix;
	}	
		
	/**
	 * Ctor.
	 */
	public XpathPrefixTableModel()
	{
		rebuildTable();
	}
	
	/**
	 * Builds/rebuilds table from the current contents of 
	 * the XmppNameSpaceContext singleton instance.
	 */
	private void rebuildTable()
	{
		mTable = new ArrayList<TableEntry>();
		
		// first entry will always be blank, and editable
		mTable.add(new TableEntry("", ""));
		
		TreeMap<String,TreeSet<String>> map = 
				XmppNamespaceContext.getInstance().getPrefixMap();
		
		for (String ns : map.keySet())
		{
			for (String prefix : map.get(ns))
			{
				 mTable.add(new TableEntry(prefix, ns));
			}
		}
		fireTableDataChanged();
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
	 * @return The number of rows in the table.
	 */
	@Override
	public int getRowCount() 
	{
		return mTable.size();
	}

	/**
	 * Required by the TableModel interface. 
	 * @param columnIndex Column whose name is requested.
	 * @return The name of the column.
	 */
	@Override
	public String getColumnName(int columnIndex) 
	{
		switch(columnIndex)
		{
		case NS: return "Namespace";
		case PREFIX: return "Mapped Prefix";		
		}
		return null;
	}

	/**
	 * Required by the TableModel interface.
	 * @param rowIndex The row of the cell whose value is requested.
	 * @param columnIndex The column of the cell whose value is requested.
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		switch(columnIndex)
		{
		case NS: return mTable.get(rowIndex).ns;
		case PREFIX: return mTable.get(rowIndex).prefix;
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
		return rowIndex == 0;
	}
	
	/**
	 * Required by the TableModel interface.
	 * @param aValue The value to assign to the selected cell.
	 * @param rowIndex The row of the selected cell.
	 * @param columnIndex the column of the selected cell.
	 */
	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) 
	{
		TableEntry te = mTable.get(rowIndex);
		switch(columnIndex)
		{
		case NS:
			te.ns = (String)aValue;
			break;
			
		case PREFIX: 
			te.prefix = (String)aValue;
		}
		if (te.ns.length() > 0 && te.prefix.length() > 0)
		{
			XmppNamespaceContext.getInstance()
					.addPrefixNsPair(te.prefix, te.ns);
			rebuildTable();
		}
	}
}
