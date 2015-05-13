/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.action;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;

import xmpptrace.action.BitUtils.ByteOrder;
import xmpptrace.model.TcpPacket;

/**
 * TcpDumpStreamParser parses tcpdump formatted trace data
 * into discrete TcpPacket objects.  Recommended usage is demonstrated
 * by the following:
 *
 *      TcpDumpStreamParser parser = new TcpDumpStreamParser(...);
 *      TcpPacket p = parser.getNextPacket();
 *      while (p != null)
 *      {
 *          do.something.with(p);
 *          p = parser.getNextPacket();
 *      }   
 * 
 * @author adb
 */
public class TcpDumpStreamParser implements StreamParser
{
    private InputStream mStream;
    private BitUtils.ByteOrder mByteOrder;
    private long mStreamVersionMajor;
    private long mStreamVersionMinor;
    private long mStreamTimeZone;
    private long mStreamTimePrecision;
    private long mStreamSnapLen;
    private long mStreamDataLinkType;
    private int mPacketCount;
    
    // these link layer codes come from libpcap bpf.h and pcap-common.c
    private static final int DLT_EN10MB = 1;
    private static final int LINKTYPE_ETHERNET = DLT_EN10MB;
    
    private static final int DLT_LINUX_SLL = 113;
    private static final int LINKTYPE_LINUX_SLL = DLT_LINUX_SLL;
    
    @SuppressWarnings("serial")
    static public class TcpDumpParseException
        extends IOException {}
    
    public TcpDumpStreamParser(InputStream is)
    {
        mStream = is;
        mByteOrder = ByteOrder.BIG_ENDIAN;
        mStreamVersionMajor = 0;
        mStreamVersionMinor = 0;
        mStreamTimeZone = 0;
        mStreamTimePrecision = 0;
        mStreamSnapLen = 0;
        mStreamDataLinkType = -1;
        mPacketCount = 0;
    }
   
	/**
	 * Reads the next TCP packet entry from the stream provided in the ctor.
	 * @return The next TcpPacket parsed from the stream, null if reached eof.
	 * @throws IOException If an error occurs while reading from the file.
	 */
    public TcpPacket getNextPacket() throws IOException
    {
        // read the stream header, if we haven't already
        if (mStreamDataLinkType == -1)
        {
            readStreamHeader();
        }
        
        int ipidx = -1;
        int tcpidx = -1;
        long sec = 0;
        long usec = 0;
        long caplen = 0;
        byte[] dlf = null;
        do
        {
            // read pcap file record header
            byte[] hdr = new byte[16];
            if (!blockReadFromStream(hdr))
            {
                return null;
            }
            
            // read pcap record header
            sec = BitUtils.bytesToLong(hdr, 0, 4, mByteOrder);
            usec = BitUtils.bytesToLong(hdr, 4, 4, mByteOrder);
            caplen = BitUtils.bytesToLong(hdr, 8, 4, mByteOrder);
            
            // read in a complete frame, repeat until an ip packet is found
            dlf = new byte[(int)caplen];
            if (!blockReadFromStream(dlf))
            {
                return null;
            }

            // discover where the ip packet is, based on link layer fields            
            ipidx = getIpPacketOffset(dlf);
            
            // if link layer indicated IP protocol, check for stuff we need
            if (ipidx != -1)
            {
                // if any of these checks falls through, ignore frame
                tcpidx = ipidx + (dlf[ipidx] & 0x0F) * 4;        
                if ((dlf[ipidx] >>> 4 != 4) ||    // we require IPv4
                    (dlf[ipidx + 9] != 6) ||      // we require TCP
                    (dlf.length < tcpidx + 20))   // we require basic TCP header
                {
                    ipidx = -1;
                }
            }
        }
        while (ipidx == -1);

        TcpPacket p = new TcpPacket();

        // parse the ip headers
        p.pktlen = (int)BitUtils.bytesToLong(
                dlf, ipidx + 2, 2, ByteOrder.BIG_ENDIAN);
        p.srca = (int)BitUtils.bytesToLong(
                dlf, ipidx + 12, 4, ByteOrder.BIG_ENDIAN);
        p.dsta = (int)BitUtils.bytesToLong(
                dlf, ipidx + 16, 4, ByteOrder.BIG_ENDIAN);

        // parse the tcp headers
        p.srcp = (int)BitUtils.bytesToLong(
                dlf, tcpidx, 2, ByteOrder.BIG_ENDIAN);
        p.dstp = (int)BitUtils.bytesToLong(
                dlf, tcpidx + 2, 2, ByteOrder.BIG_ENDIAN);
        p.seqno = BitUtils.bytesToLong(
                dlf, tcpidx + 4, 4, ByteOrder.BIG_ENDIAN);
        p.ackno = BitUtils.bytesToLong(
                dlf, tcpidx + 8, 4, ByteOrder.BIG_ENDIAN);
        p.tcpflags = dlf[tcpidx + 13];

        // verify we have payload data of at least 1 byte
        int offset = (int)BitUtils.bytesToLong(
        		dlf, tcpidx + 12, 1, ByteOrder.BIG_ENDIAN) >>> 4; 
        int dataidx = tcpidx + (offset * 4);
        if (dlf.length > dataidx)
        {
        	if (isUtf8(dlf, dataidx, dlf.length - dataidx))
        	{
        		p.data = new String(dlf, dataidx, dlf.length - dataidx, "UTF-8");
        		if (p.data.trim().length() == 0)
        		{
        			p.data = null;
        		}
        		else
        		{
        			p.stanzas = new XmppPacketParser().parse(p.data);
        			p.readable = true;
        		}
        	}
        	else
        	{
        		p.data = new String("[data not readable]");
        		p.readable = false;
        	}
        }        

        // add packet metadata
        p.pacno = this.mPacketCount++;
        p.time = new Timestamp(sec * 1000 + usec / 1000);
        p.src = TcpPacket.stringifyAddress(p.srca, p.srcp);
        p.dst = TcpPacket.stringifyAddress(p.dsta, p.dstp);
        if ((int)caplen != ipidx + (int)p.pktlen)
        {
            p.truncated = true;
        }
                    
        return p;
    }
    
    private boolean isUtf8(byte[] buf, int offset, int length)
    {
        int end = offset + length;
        for (int i = offset; i < end; ++i)
        {
            int c = 0;
            byte a = buf[i];
            for (int m = 0x80; m > 0 && (a & m) != 0; m >>>= 1, ++c);            
            if ((c == 1) || ( c > 6)) 
                return false;
            for (c += i, ++i; i < c; ++i)
                if ((buf[i] & 0xC0) != 0x80) 
                    return false;
        }
        return true;
    }

    private void readStreamHeader() throws IOException
    {
        // read 24 byte stream header
        byte[] hdr = new byte[24];
        blockReadFromStream(hdr);
         
        // check magic number, determine stream byte order
        if (hdr[0] == (byte)0xD4 &&
            hdr[1] == (byte)0xC3 &&
            hdr[2] == (byte)0xB2 &&
            hdr[3] == (byte)0xA1)
        {
            mByteOrder = ByteOrder.LITTLE_ENDIAN;
        }
        else if (
            hdr[0] == (byte)0xA1 &&
            hdr[1] == (byte)0xB2 &&
            hdr[2] == (byte)0xC3 &&
            hdr[3] == (byte)0xD4)
        {
            mByteOrder = ByteOrder.BIG_ENDIAN;
        }
        else
        {
            throw new TcpDumpParseException();
        }
        
        // get stream header fields
        mStreamVersionMajor = BitUtils.bytesToLong(hdr, 4, 2, mByteOrder);
        mStreamVersionMinor = BitUtils.bytesToLong(hdr, 6, 2, mByteOrder);
        mStreamTimeZone = BitUtils.bytesToLong(hdr, 8, 4, mByteOrder);
        mStreamTimePrecision = BitUtils.bytesToLong(hdr, 12, 4, mByteOrder);
        mStreamSnapLen = BitUtils.bytesToLong(hdr, 16, 4, mByteOrder);
        mStreamDataLinkType = BitUtils.bytesToLong(hdr, 20, 4, mByteOrder);
    }

    private int getIpPacketOffset(byte[] dlf)
    {
        int ipidx = -1;
        if (mStreamDataLinkType == LINKTYPE_ETHERNET)
        {
            // eth:ip
            if (dlf.length >= 15 &&
                dlf[12] == (byte)0x08 &&
                dlf[13] == (byte)0x00)
            {
                ipidx = 14;
            }
            // eth:vlan:ip
            if (dlf.length >= 19 &&
                dlf[12] == (byte)0x81 &&
                dlf[13] == (byte)0x00 &&
                dlf[16] == (byte)0x08 &&
                dlf[17] == (byte)0x00)
            {
                ipidx = 18;
            }
        }
        else if (mStreamDataLinkType == LINKTYPE_LINUX_SLL)
        {
            // sll:ip
            if (dlf.length >= 17 &&
                dlf[14] == (byte)0x08 &&
                dlf[15] == (byte)0x00)
            {
                ipidx = 16;
            }
        }
        return ipidx;
    }
    
    private boolean blockReadFromStream(byte[] b) throws IOException
    {
        int size = b.length;
        int bytesRead = 0;
        int totalBytesRead = 0;
        do
        {
            bytesRead = mStream.read(b, totalBytesRead, size - totalBytesRead);
            totalBytesRead += bytesRead;
        }
        while (bytesRead != -1 && totalBytesRead < size);
        
        return (bytesRead != -1);
    }
}
