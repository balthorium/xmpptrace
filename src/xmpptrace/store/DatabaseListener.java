/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.store;

public interface DatabaseListener
{
    /**
     * Event fired when the database has been updated.
     */
    public void onDatabaseUpdate();    
}
