package xmpptrace.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;

import javax.swing.ProgressMonitorInputStream;
import javax.swing.SwingUtilities;

import org.h2.constant.ErrorCode;
import org.h2.jdbcx.JdbcConnectionPool;
import org.w3c.dom.Document;

import xmpptrace.action.StreamParser;
import xmpptrace.action.XmppAugur;
import xmpptrace.action.TcpDumpStreamParser;
import xmpptrace.action.XmppDumpStreamParser;
import xmpptrace.model.Address;
import xmpptrace.model.TcpPacket;

import static xmpptrace.store.DatabaseQuery.*;

/**
 * XmppDumpDatabase supplies an interface to the embedded database used
 * for storing xmppdump traffic read into the application (e.g. via file
 * or via the XmppDompCollectService).  All db-aware code is centralized
 * in this one class, to simplify transition to, or support for, other
 * databases (current implementation uses H2).  XmppDumpDatabase is 
 * itself a singleton, from which users may acquire
 * @author adb
 *
 */
public class Database
{
    public static final String SETTINGS_TRUE = "true";
    public static final String SETTINGS_FALSE = "false";
    public static final String SETTINGS_XMPP_ONLY = "visible.xmpp-only";
    private static Database sInstance;
    
    private JdbcConnectionPool mCxnPool;
    private String mDbFileName;
    private boolean mDbFileIsTemp;
    private Object mPacnoLock;
    private ArrayList<DatabaseListener> mListeners;
    private boolean mDisableUpdateEvents;

    /**
     * Definition of a callback interface, used by the iterateOverPackets()
     * method.  Users of that function must supply their own object which
     * implements this interface.
     * @author adb
     *
     */
    public static interface XmppPacketFetchCallback
    {
        void processPacket(TcpPacket packet);
    }
    
    /**
     * The database object will be a singleton.
     * @return The XmppDumpDatabase singleton instance.
     */
    static public synchronized Database getInstance()
    {
        if (sInstance == null)
        {
            sInstance = new Database();
        }
        return sInstance;
    }

    /**
     * Ctor is private, get access to singleton using getInstance().
     * @throws ClassNotFoundException 
     * @throws SQLException 
     */
    private Database()
    {
        mCxnPool = null;
        mDbFileName = null;
        mDbFileIsTemp = false;
        mPacnoLock = new Object();
        mListeners = new ArrayList<DatabaseListener>();
        mDisableUpdateEvents = false;
        try
        {
            open(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Opens (or re-opens) the database, using the given db name or
     * db file name  If aDbName is null, this will create a new 
     * database with a unique name. 
     * If the database is already open, it will close the existing db.
     * 
     * @param aDbName The name of the db, or db file, to open.
     *      
     * @throws SQLException On failure to operate on the database.
     */
    public void open(String aDbName) throws SQLException
    {   
        try
        {
            // close db, if open
            close();
            
            // temp automatic to hold name until method succeeds
            String dbFileName = null;
    
            // if no name provided, make one up, and ensure its not locked
            if (aDbName == null)
            {
                int i = 0;
                String tempName = "xmpptrace.temp.";
                String curDir = System.getProperty("user.dir");
                String dbFileLock = curDir + File.separator + 
                        tempName + i + ".lock.db";
                while (new File(dbFileLock).exists())
                {
                    dbFileLock = curDir + File.separator + 
                            tempName + ++i + ".lock.db";
                }
                dbFileName = curDir + File.separator + 
                        tempName + i + ".h2.db";                
                new File(dbFileName).delete();
                mDbFileIsTemp = true;
            }
            else
            {
                // if given db name has no extension, add one
                if (aDbName.indexOf(".h2.db") == -1)
                {
                    aDbName = aDbName + ".h2.db";
                }
                dbFileName = aDbName;
                mDbFileIsTemp = false;
            }
            
            // determine if this is a new database file
            boolean isNewFile = !new File(dbFileName).exists();
    
            // create connection pool, and db file if it doesn't already exists
            try
            {
                Class.forName("org.h2.Driver");
            }
            catch (ClassNotFoundException e)
            {
                e.printStackTrace();
                System.exit(-1);
            }
            String dbName = dbFileName.substring(0, 
                    dbFileName.lastIndexOf(".h2.db"));
            mCxnPool = JdbcConnectionPool.create(
                   "jdbc:h2:file:" + dbName, "jabber", "jabber");
    
            // create and initialize new db, or verify existing db
            if (isNewFile)
            {
                createSchema();
                updateSetting(SETTINGS_XMPP_ONLY, SETTINGS_FALSE);  
            }
            else
            {
                verifySchema();
            }
            
            mDbFileName = dbFileName;
            fireDatabaseUpdateEvent();
        }
        catch (SQLException e)
        {
            // if failed to open named db, creating an empty one
            if (aDbName != null)
            {
                open(null);
                throw e;
            }
        }
    }
    
    /**
     * Saves the current database file under the new name,
     * and reopens the db with that name.  If the current
     * db exists under a temporary name, this is just a file
     * move.
     * 
     * @param aDbName The name under which to save the database.
     * @throws Exception On failure to save the current database.
     */
    public void saveAs(String aDbName) throws Exception
    {
        if (aDbName.indexOf(".h2.db") == -1)
        {
            aDbName = aDbName + ".h2.db";
        }
        boolean dbFileIsTemp = mDbFileIsTemp;
        mDbFileIsTemp = false;
        File oldFile = new File(mDbFileName);
        File newFile = new File(aDbName);
        close();
        if (dbFileIsTemp)
        {
            oldFile.renameTo(newFile);
        }
        else
        {
            copyFile(oldFile, newFile);
        }
        open(newFile.getAbsolutePath());
    }
    
    /**
     * Generic file copy.  Staggering that Java standard library doesn't have
     * a method for this until Java 7 (which is too new to assume folks have).
     * 
     * @param src Source file.
     * @param dst Destination file.
     * @throws Exception On failure to copy the given file.
     */
    private void copyFile(File src, File dst) throws Exception 
    {
        FileInputStream fis  = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        try 
        {
            byte[] buf = new byte[1024];
            int i = 0;
            while ((i = fis.read(buf)) != -1) 
            {
                fos.write(buf, 0, i);
            }
        } 
        finally 
        {
            if (fis != null) fis.close();
            if (fos != null) fos.close();
        }
    }
    
    /**
     * Closes the db, if open (no-op otherwise).  If the database
     * is flagged as "default", it will also delete the underlying
     * database file.
     * 
     * @throws SQLException On failure to close the database.
     */
    public void close() throws SQLException
    {
        if (mCxnPool != null)
        {
            mCxnPool.dispose();
        }
        if (mDbFileIsTemp && mDbFileName != null)
        {
            new File(mDbFileName).delete();
        }
        mCxnPool = null;
        mDbFileName = null;
        mDbFileIsTemp = false;
    }

    /**
     * Returns the file name of the currently open database.
     * @return The file name of the currently open database.
     */
    public String getDbFileName()
    {
        if (mDbFileIsTemp)
        {
            return null;
        }
        return mDbFileName;
    }
    
    /**
     * Add a listener for events on this XmppDumpDatabase object.
     * @param l Listener to be added.
     */
    public void addListener(DatabaseListener l)
    {
        mListeners.add(l);
    }

    /**
     * Creates the xmpptrace schema in a new database.
     */
    private void createSchema() throws SQLException
    {
        java.sql.Connection cxn = null;
        cxn = mCxnPool.getConnection();
        cxn.setAutoCommit(true);
        cxn.createStatement().execute(CREATE_SCHEMA);
        cxn.close();
    }

    /**
     * Shrinks the database by deleting packets which are not
     * currently selected as visible.
     * @throws SQLException On failure to reduce the database size.
     */
    public void reduce() throws SQLException
    {
        java.sql.Connection cxn = null;
        cxn = mCxnPool.getConnection();
        cxn.setAutoCommit(true);
        cxn.createStatement().execute(REDUCE_PACKETS);
        cxn.close();

        fireDatabaseUpdateEvent();
    }

    /**
     * Creates the xmpptrace schema in a new database.
     */
    private void verifySchema() throws SQLException
    {
        java.sql.Connection cxn = null;
        cxn = mCxnPool.getConnection();
        cxn.setAutoCommit(true);
        ResultSet rs = cxn.createStatement().executeQuery(VERIFY_SCHEMA);
        if (!rs.next()) throw new SQLException("Db schema not recognized.");
        cxn.close();
    }
    
    /**
     * Update the given address in the addresses table, to have the given
     * actor name.
     * @param ip Address to be updated.
     * @param actor New actor name.
     */
    public void setAddressActor(String ip, String actor)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);
            PreparedStatement ps = cxn.prepareStatement(
                    UPDATE_ADDRESS_ACTOR);
            ps.setString(1, actor);
            ps.setString(2, ip);
            ps.executeUpdate();
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        updateActorTable();
    }
    
    /**
     * Fetch the actor name corresponding to the given address from the
     * address table.
     * @param ip Address to be updated.
     * @return Actor name.
     */
    public String getAddressActor(String ip)
    {
        String actor = null;
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);
            PreparedStatement ps = cxn.prepareStatement(
                    FETCH_ADDRESS_ACTOR);
            ps.setString(1, ip);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
            {
                actor = rs.getString(1);
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        return actor;
    }
    
    /**
     * Update the given address in the addresses table, to have the given
     * visibility flag value.
     * @param ip Address to be updated.
     * @param flag New flag value for visibility.
     */
    public void setAddressVisible(String ip, Boolean flag)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);
            PreparedStatement ps1 = cxn.prepareStatement(
                    UPDATE_ADDRESS_VISIBLE);
            ps1.setBoolean(1, flag);
            ps1.setString(2, ip);
            ps1.executeUpdate();
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        updateActorTable();
    }
    
    /**
     * Overwrites the current contents of the actors table with the contents
     * of the given actorList.  Note that this will update the actors table
     * immediately following the insertion, such that the data constraints
     * between the actors and addresses is maintained.
     * 
     * @param actorList List of actors to insert to actors table.
     */
    public void setActorsList(ArrayList<String> actorList)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);

            // delete current contents of actors table
            Statement sDel = cxn.createStatement();
            sDel.executeUpdate(DELETE_ACTORS);
            
            // insert new actor list to actors table
            PreparedStatement psIns = cxn.prepareStatement(INSERT_ACTOR);         
            int i = 0;
            for (String actor: actorList)
            {                
                psIns.setInt(1, i++);
                psIns.setString(2, actor);
                psIns.executeUpdate();                
            }
            cxn.close();
        }
        catch (Exception e) 
        {
            e.printStackTrace();
        }

        updateActorTable();
    }

    /**
     * Inserts or updates a (name, value) pair in the settings table.
     * @param name Setting name.
     * @param value New setting value.
     */
    public void updateSetting(String name, String value)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);

            PreparedStatement ps = null;
            ps = cxn.prepareStatement(FETCH_SETTING);      
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
            {
                ps = cxn.prepareStatement(UPDATE_SETTING);      
                ps.setString(1, value);
                ps.setString(2, name);
                ps.executeUpdate();
            }
            else
            {
                ps = cxn.prepareStatement(INSERT_SETTING);      
                ps.setString(1, name);
                ps.setString(2, value);
                ps.executeUpdate();
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        fireDatabaseUpdateEvent();
    }    
    
    /**
     * Fetches a (name, value) pair from the settings table.
     * @param name Setting name.
     * @return The value of the given setting.
     */
    public String fetchSetting(String name)
    {
        String value = null;
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);

            PreparedStatement ps = null;
            ps = cxn.prepareStatement(FETCH_SETTING);      
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
            {
                value = rs.getString(1);
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        return value;
    }    
    
    /**
     * As addresses are modified (visibility, actor name, etc) the actors table
     * can become out-of-sync.  This function will audit the contents of the 
     * actors table, remove any entries that should not be present, and add new
     * entries when needed.  It will not, however, alter the precedence of the
     * entries. 
     */
    private void updateActorTable()
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);

            PreparedStatement ps1 = cxn.prepareStatement(SELECT_ACTORS_TO_ADD);
            PreparedStatement ps2 = cxn.prepareStatement(INSERT_ACTOR);
            ResultSet rs1 = ps1.executeQuery();
            while (rs1.next())
            {
                ps2.setInt(1, Integer.MAX_VALUE);
                ps2.setString(2, rs1.getString(1));
                ps2.executeUpdate();
            }
    
            PreparedStatement ps3 = cxn.prepareStatement(SELECT_ACTORS_TO_REMOVE);
            PreparedStatement ps4 = cxn.prepareStatement(DELETE_ACTOR);
            ResultSet rs3 = ps3.executeQuery();
            while (rs3.next())
            {
                ps4.setString(1, rs3.getString(1));
                ps4.executeUpdate();
            }   
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }

        fireDatabaseUpdateEvent();
    }    
    
    /**
     * Fetch all entries from actors table, and use these to
     * populate the given ArrayList.
     * @param actorList List to which actors shall be inserted.
     */
    public void fetchActors(ArrayList<String> actorList)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            Statement s = cxn.createStatement();
            ResultSet rs = s.executeQuery(FETCH_ACTORS);
            while (rs.next())
            {
                actorList.add(rs.getString(1));
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Fetch all entries from addresses table, and use these to
     * populate the given ArrayList.
     * @param addressList List to which addresses shall be inserted.
     */
    public void fetchAddresses(ArrayList<Address> addressList)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            Statement s = cxn.createStatement();
            ResultSet rs = s.executeQuery(FETCH_ADDRESSES);
            while (rs.next())
            {
                Address a = new Address();
                a.ip = rs.getString(1);
                a.actor = rs.getString(2);
                a.visible = rs.getBoolean(3);
                addressList.add(a);
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Reads xmppdump formatted data from the given file, and loads it into the
     * embedded database.  Does not clear any pre-existing contents.
     * @param f File to be loaded.
     */
    public void loadFromFile(final File f)
    {
        try
        {
            // funnel file reads through a progress monitor
            final ProgressMonitorInputStream pmis = new ProgressMonitorInputStream(
                    xmpptrace.view.XmppTraceFrame.getInstance(), 
                    "Reading " + f.getName() + "...",
                    new FileInputStream(f));     
            pmis.getProgressMonitor().setMillisToPopup(0);

            // do the file loading on background thread so progmon will show
            new Thread() 
            {
                public void run()
                {
                    // create a parser on an input stream from the file
                    if (f.getName().endsWith(".pcap"))
                    {
                        Database.this.readPacketsFromStream(
                        		new TcpDumpStreamParser(pmis));
                    }
                    else
                    {
                        Database.this.readPacketsFromStream(
                        		new XmppDumpStreamParser(pmis));
                    }
                }
            }.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Private helper function to do the work of reading packets from a stream
     * and inserting to the database.  This is written with the intent that
     * this be executed on a worker thread, not on the Swing eventing thread.
     * @param parser The parse from which to read packets.
     */
    private void readPacketsFromStream(StreamParser parser)
    {
       try
       {
            // acquire connection to embedded database
            java.sql.Connection cxn = mCxnPool.getConnection();
            cxn.setAutoCommit(true);
            PreparedStatement psAddress = cxn.prepareStatement(INSERT_ADDRESS);         
            PreparedStatement psPacket = cxn.prepareStatement(INSERT_PACKET);  
            
            // remember addresses we've already added
            ArrayList<String> addressCache = new ArrayList<String>();
            
            // iterate over all packets in file, parse and insert to db
            TcpPacket p = parser.getNextPacket();
            while (p != null)
            {
                // insert source address
                if (!addressCache.contains(p.src))
                {
                    psAddress.setString(1, p.src);
                    psAddress.setString(2, p.src);
                    psAddress.setBoolean(3, false);
                    try
                    {
                        psAddress.executeUpdate();
                    }
                    catch (SQLException e)
                    {
                        // duplicate keys are expected
                        if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY_1)
                        {
                            e.printStackTrace();
                        }
                    }
                    addressCache.add(p.src);
                }
                
                // insert destination address
                if (!addressCache.contains(p.dst))
                {
                    psAddress.setString(1, p.dst);
                    psAddress.setString(2, p.dst);
                    psAddress.setBoolean(3, false);
                    try
                    {
                        psAddress.executeUpdate();
                    }
                    catch (SQLException e)
                    {
                        // duplicate keys are expected
                        if (e.getErrorCode() != ErrorCode.DUPLICATE_KEY_1)
                        {
                            e.printStackTrace();
                        }
                    }
                    addressCache.add(p.dst);
                }
                
                // insert packet (leave pacno null, that is set at the end)
                psPacket.setTimestamp(1, p.time);
                psPacket.setString(2, p.src);
                psPacket.setString(3, p.dst);
                psPacket.setLong(4, p.tcpflags);
                psPacket.setLong(5, p.seqno);
                psPacket.setLong(6, p.ackno);
                psPacket.setInt(7, p.pktlen);
                psPacket.setBoolean(8, p.readable);
                
                // store the original packet text string
                if (p.data != null && p.data.length() > 0)
                {
                    psPacket.setString(9, p.data);
                }
                else
                {
                    psPacket.setNull(9, Types.VARCHAR);
                }
                
                // serialize the array of stanza dom documents
                if (p.stanzas != null && p.stanzas.size() > 0)
                {
                    ByteArrayOutputStream aos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(aos);
                    oos.writeObject(p.stanzas);
                    psPacket.setBytes(10, aos.toByteArray());
                }
                else
                {
                    psPacket.setNull(10, Types.BINARY);
                }
                
                // execute the insertion
                psPacket.executeUpdate();
                
                // parse next packet from the stream
                p = parser.getNextPacket();
            }
            cxn.close();
        
            // consult the augur, and wait quietly.
            mDisableUpdateEvents = true;
            new XmppAugur().takeAuspices();
            mDisableUpdateEvents = false;
        
            // reset the pacno fields of all packets in the database
            reSequence();

            fireDatabaseUpdateEvent();                    
       }
       catch (InterruptedIOException e)
       {
           // ok, user simply decided to cancel the load
       }
       catch (Exception e)
       {
           e.printStackTrace();
       }
    }
        
    /**
     * Once a stream of new packets have been loaded to the database, we
     * need to re-sequence the pacno field, as that is used to represent
     * the chronological sequencing of all packets in the table.  This
     * is done by iterating over the set of all packets, ordered by
     * time (then by uid), and updating the pacno field of each row to
     * be one greater than the previous.
     */
    private void reSequence()
    {
        // renumbering of pacno must be done in a critical section
        synchronized (mPacnoLock)
        {
            try
            {
                // acquire connection to embedded database
                java.sql.Connection cxn = mCxnPool.getConnection();
                cxn.setAutoCommit(true);
                
                // we want the result set to be scrollable and updatable 
                Statement s = cxn.createStatement(
                        ResultSet.TYPE_SCROLL_INSENSITIVE, 
                        ResultSet.CONCUR_UPDATABLE);

                // we want pacno to increase with time, and then by uid
                ResultSet rs = s.executeQuery(SELECT_ALL_UID_AND_PACNO);
                
                // iterate over result set, and update the pacno field of each row
                int i = 0;
                while (rs.next())
                {
                    rs.updateInt(2, i++);
                    rs.updateRow();
                }
                cxn.close();
            }
            catch (SQLException e)
            {
                e.printStackTrace();
            }
        }   
    }
    
    /**
     * Sequentially and synchronously invokes onDatabaseUpdate() on listeners.
     */
    private void fireDatabaseUpdateEvent()
    {
        if (mDisableUpdateEvents) return; 
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                for (DatabaseListener l: mListeners)
                {
                    l.onDatabaseUpdate();
                }                
            }
        });
    }

    /**
     * Fetches the full set of pacno values, in ascending order, of packets
     * which are currently visible.  Inserts these to the given list.
     * @param list The list to which pacno values should be inserted.
     */
    public void getVisiblePackets(ArrayList<Integer> list)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            Statement s = cxn.createStatement();
            ResultSet rs = s.executeQuery(SELECT_VISIBLE_PACKETS);
            while (rs.next())
            {
                list.add(rs.getInt(1));
            }
            cxn.close();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }        
    }

    /**
     * Retrieves a single packet, with the given pacno, from the database.
     * @param pacno The pacno to select on when retrieving from the db.
     * @return The packet with matching pacno, or null if not found.
     */
    public TcpPacket getPacket(int pacno)
    {
        TcpPacket p = new TcpPacket();
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            PreparedStatement ps = cxn.prepareStatement(FETCH_PACKET_BY_PACNO);         
            ps.setInt(1, pacno);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                p = packetFromResultSet(rs);
            }
            cxn.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }       
        
        return p;
    }

    /**
     * Returns a count of all packets in the database.
     * @return Total number of packets in the packets table.
     */
    public int getPacketCount()
    {
        int retval = 0;
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            PreparedStatement ps = cxn.prepareStatement(GET_PACKET_COUNT);         
            ResultSet rs = ps.executeQuery();
            if (rs.next())
            {
                retval = rs.getInt(1);
            }
            cxn.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }       
        
        return retval;
    }
    
    /**
     * Retrieves and iterates over all packet from the database. For
     * each packet fetched, this will invoke the given callback 
     * object's processPacket() method.
     * @param iter The iterator pointing to the current packet in the resuts.
     */
    public void iterateOverPackets(XmppPacketFetchCallback iter)
    {
        try
        {
            java.sql.Connection cxn = mCxnPool.getConnection();
            PreparedStatement ps = cxn.prepareStatement(FETCH_ALL_PACKETS,
                    ResultSet.TYPE_SCROLL_SENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);         
            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                iter.processPacket(packetFromResultSet(rs));
            }
            cxn.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    /**
     * Helper function to extract fields from a ResultSet row
     * and use them to construct an TcpPacket object.
     * @param rs A result set positioned at the row of interest.
     * @return A new TcpPacket object instantiated from the row data.
     * @throws SQLException On failure to operate on the database.
     * @throws IOException On failure to deserialize the packet.
     * @throws ClassNotFoundException On failure to deserialize the packet.
     */
    @SuppressWarnings("unchecked")
    private TcpPacket packetFromResultSet(ResultSet rs) 
            throws SQLException, IOException, ClassNotFoundException
    {
        TcpPacket p = new TcpPacket();

        p.pacno = rs.getInt(1);
        p.time = rs.getTimestamp(2);
        p.src = rs.getString(3);
        p.dst = rs.getString(4);
        p.tcpflags = rs.getByte(5);
        p.seqno = rs.getLong(6);
        p.ackno = rs.getLong(7);
        p.pktlen = rs.getInt(8);
        p.readable = rs.getBoolean(9);
        p.data = rs.getString(10);
        if (p.data == null)
        {
            p.data = new String();
        }
        
        byte[] ba = rs.getBytes(11);
        if (ba != null)
        {
            ByteArrayInputStream ais = new ByteArrayInputStream(ba);    
            ObjectInputStream ois = new ObjectInputStream(ais);
            Object obj = ois.readObject();
            if (obj instanceof ArrayList<?>)
            {
                p.stanzas = (ArrayList<Document>)obj;
            }
        }
        
        return p;
    }
}
