package xmpptrace.store;

/**
 * A dumping ground for SQL statements used by XmppDumpDatabase.
 * 
 * @author adb
 *
 */
public interface DatabaseQuery
{
    static public final String VERIFY_SCHEMA =
        "SELECT table_name " +
            "FROM information_schema.tables " +
            "WHERE table_name='PACKETS';";
       
    static public final String CREATE_SCHEMA =
            "" +
        "CREATE TABLE IF NOT EXISTS settings (" +
            "name VARCHAR(255) NOT NULL," +
            "value VARCHAR(255) NOT NULL, " +
            "PRIMARY KEY(name));" +
            "" +
        "CREATE TABLE IF NOT EXISTS addresses (" +
            "ip VARCHAR(255)," +
            "actor VARCHAR(255)," +
            "visible BOOLEAN," +
            "PRIMARY KEY (ip));" +
            "" +
        "CREATE TABLE IF NOT EXISTS actors (" +
            "precedence INTEGER," +
            "actor VARCHAR(255));" +
            "" +
        "CREATE TABLE IF NOT EXISTS packets (" +
            "uid INTEGER AUTO_INCREMENT NOT NULL," +
            "pacno INTEGER," +
            "time TIMESTAMP NOT NULL," +
            "src VARCHAR(255) NOT NULL," +
            "dst VARCHAR(255) NOT NULL," +
            "tcpflags TINYINT NOT NULL," +
            "seqno BIGINT NOT NULL," +
            "ackno BIGINT NOT NULL," +
            "pktlen INTEGER NOT NULL," +
            "readable BOOLEAN NOT NULL," +
            "data VARCHAR(65535)," +
            "stanzas BINARY," +
            "FOREIGN KEY (src) REFERENCES addresses (ip)," +
            "FOREIGN KEY (dst) REFERENCES addresses (ip)," +
            "PRIMARY KEY(uid));" +
            "" +
            "CREATE OR REPLACE VIEW packets_visible AS " +
            "SELECT p.pacno FROM packets p, addresses a, addresses b " + 
            "WHERE p.pacno IS NOT NULL " +
            "AND (a.visible = 'true' AND a.ip = p.src) " + 
            "AND (b.visible = 'true' AND b.ip = p.dst) " + 
            "AND (p.data IS NOT NULL OR 'false' IN " + 
            "(SELECT value FROM settings WHERE name='visible.xmpp-only')) " + 
            "ORDER BY pacno; " +
            "" +
        "CREATE INDEX IF NOT EXISTS packets_time_idx ON packets (time);" +
        "CREATE INDEX IF NOT EXISTS packets_pacno_idx ON packets (pacno);" +
        "CREATE INDEX IF NOT EXISTS actors_precedence_idx ON actors (precedence);";

    static public final String UPDATE_SETTING = 
        "UPDATE settings SET value=? WHERE name=?;";

    static public final String INSERT_SETTING = 
        "INSERT INTO settings (name, value) " +
        "VALUES (?, ?);";
    
    static public final String FETCH_SETTING = 
        "SELECT value FROM settings WHERE name=?;";
 
    static public final String DELETE_ACTORS =
        "DELETE FROM actors;";

    static public final String DELETE_ACTOR =
        "DELETE FROM actors WHERE actor=?;";

    static public final String INSERT_ACTOR = 
        "INSERT INTO actors (precedence, actor) " +
        "VALUES (?, ?);";
    
    static public final String FETCH_ACTORS = 
        "SELECT actor FROM actors ORDER BY precedence;";
    
    static public final String SELECT_ACTORS_TO_ADD = 
        "SELECT DISTINCT actor FROM addresses WHERE visible='true' " +
        "AND NOT actor IN (SELECT actor FROM actors);";

    static public final String SELECT_ACTORS_TO_REMOVE = 
        "SELECT actor FROM actors WHERE NOT actor IN " +
        "(SELECT DISTINCT actor FROM addresses WHERE visible='true');";
    
    static public final String FETCH_ADDRESSES = 
        "SELECT ip, actor, visible FROM addresses ORDER BY actor, ip;";

    static public final String INSERT_ADDRESS = 
        "INSERT INTO addresses (ip, actor, visible) " +
        "VALUES (?, ?, ?);";

    static public final String UPDATE_ADDRESS_ACTOR = 
        "UPDATE addresses SET actor=? where ip=?;";
    
    static public final String FETCH_ADDRESS_ACTOR =
        "SELECT actor FROM addresses WHERE ip=?;";

    static public final String UPDATE_ADDRESS_VISIBLE = 
        "UPDATE addresses SET visible=? where ip=?;";
    
    static public final String INSERT_PACKET = 
        "INSERT INTO packets (" +
        "time, src, dst, tcpflags, seqno, " +
        "ackno, pktlen, readable, data, stanzas) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
    
    static public final String SELECT_ALL_UID_AND_PACNO =
        "SELECT uid, pacno FROM packets ORDER BY time, uid;";

    static public final String SELECT_VISIBLE_PACKETS = 
        "select pacno FROM packets_visible";

    static public final String FETCH_PACKET_BY_PACNO =
        "SELECT pacno, time, src, dst, tcpflags, seqno, ackno, pktlen, " +
        "readable, data, stanzas FROM packets WHERE pacno=?;";

    static public final String FETCH_ALL_PACKETS =
        "SELECT pacno, time, src, dst, tcpflags, seqno, ackno, pktlen, " +
        "readable, data, stanzas FROM packets;";
    
    static public final String GET_PACKET_COUNT =
        "SELECT count(*) from packets;";
    
    static public final String REDUCE_PACKETS = 
        "DELETE FROM packets WHERE pacno NOT IN " +
        "(SELECT pacno FROM packets_visible); " +
        "DELETE FROM addresses WHERE ip NOT IN " + 
        "(SELECT distinct src FROM packets); " +
        "DELETE FROM addresses WHERE ip NOT IN " + 
        "(SELECT distinct dst FROM packets); " +
        "DELETE FROM actors WHERE actor NOT IN " + 
        "(SELECT distinct actor FROM addresses);";
}
