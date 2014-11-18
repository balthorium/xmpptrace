package xmpptrace.store;

public interface DatabaseListener
{
    /**
     * Event fired when the database has been updated.
     */
    public void onDatabaseUpdate();    
}
