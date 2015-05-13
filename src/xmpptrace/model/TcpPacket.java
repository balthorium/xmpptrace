/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.model;

import java.util.ArrayList;
import java.sql.Timestamp;

import org.w3c.dom.Document;

/**
 * Class to represent the headers and contents of a TCP packet.
 * Also contains various bits of useful metadata which are collected
 * from the tcpdump/xmppdump stream, such as packet number, time,
 * as well as a list of DOM's matching any parsable XML elements
 * found in the payload.
 * 
 * @author adb
 *
 */
public class TcpPacket 
        implements Comparable<TcpPacket>
{
    // ip fields
    public int pktlen;
	public int srca;
	public int dsta;

    // tcp fields
    public int srcp;
    public int dstp;
    public long seqno;
    public long ackno;
    public byte tcpflags;
	
	// text payload
	public String data;

	// metadata
    public int pacno;
    public Timestamp time;
    public boolean readable;
    public boolean truncated;
    public String src;
    public String dst;
    public ArrayList<Document> stanzas;

    /**
     * Generate a string representation of given address and port.
     * @param a IPv4 address, represented as a big endian integer.
     * @param p TCP port, represented as a big endian short.
     * @return A string representation of the full TCP/IP address.
     */
    public static String stringifyAddress(int a, int p)
    {
        StringBuffer sb = new StringBuffer();
        sb.append(a >>> 24);
        sb.append('.');
        sb.append(a >>> 16 & 0xFF);
        sb.append('.');
        sb.append(a >>> 8 & 0xFF);
        sb.append('.');
        sb.append(a & 0xFF);
        sb.append(":");
        sb.append(p); 
        return sb.toString();
    }    
    
	/**
	 * Expands the abbreviated tcpflags string to a more descriptive
	 * human-readable form.
	 * @return A descriptive human-readable form of the tcpflags.
	 */
	public String getTcpFlags()
	{
		StringBuffer sb = new StringBuffer();
        if ((tcpflags & (byte)0x02) != 0) sb.append("SYN,");
        if ((tcpflags & (byte)0x01) != 0) sb.append("FIN,");
        if ((tcpflags & (byte)0x04) != 0) sb.append("RST,");
        if ((tcpflags & (byte)0x08) != 0) sb.append("PSH,");
        if ((tcpflags & (byte)0x10) != 0) sb.append("ACK,");
        if ((tcpflags & (byte)0x20) != 0) sb.append("URG,");
        if ((tcpflags & (byte)0x40) != 0) sb.append("ECN,");
        if ((tcpflags & (byte)0x80) != 0) sb.append("CWR,");
		if (sb.length() != 0) sb.setLength(sb.length() - 1);
		return sb.toString();
	}
	
	public String toString()
	{
		StringBuffer buf = new StringBuffer();
        buf.append(pacno + "|");
        buf.append(time + "|");
        buf.append(pktlen + "|");
		buf.append(src + "|");
		buf.append(dst + "|");
		buf.append(seqno + "|");
		buf.append(ackno + "|");
        buf.append(getTcpFlags() + "|");
		buf.append(readable + "|");
        buf.append(truncated);
		return buf.toString();
	}

    @Override
    public int compareTo(TcpPacket rhs)
    {
        int retval = -1;
        if (pacno == rhs.pacno) retval = 0;
        if (pacno > rhs.pacno) retval = 1;
        return retval;
    }
}
