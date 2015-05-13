/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.action;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xmpptrace.model.TcpPacket;

/**
 * XmppDumpStreamParser parses xmppdump formatted trace data
 * into discrete TcpPacket objects.  Recommended usage is demonstrated
 * by the following:
 *
 *      XmppDumpStreamParser parser = new XmppDumpStreamParser(...);
 *      TcpPacket p = parser.getNextPacket();
 *      while (p != null)
 *      {
 *          do.something.with(p);
 *          p = parser.getNextPacket();
 *      }   
 * 
 * @author adb
 *
 */
public class XmppDumpStreamParser implements StreamParser
{
	private BufferedReader mReader;
	private Pattern mPatEvent;
	private Pattern mPatFrom;
	private Pattern mPatTo;
	private Pattern mPatTcpflags;
	private Pattern mPatTime;
	private Pattern mPatSeqNo;
	private Pattern mPatAckNo;
	private Pattern mPatLength;
	private Pattern mPatFlength;
	private Pattern mPatReadable;
	
	public XmppDumpStreamParser(InputStream is)
	{
		mReader = new BufferedReader(new InputStreamReader(is)); 
		
		// compile regex patterns used for parsing
		mPatEvent = Pattern.compile("^tcp");
		mPatFrom = Pattern.compile("from\\s*=\\s*\"([^\"]+)\"");
		mPatTo = Pattern.compile("to\\s*=\\s*\"([^\"]+)\"");
		mPatTcpflags = Pattern.compile("flags\\s*=\\s*\"([^\"]+)\"");
		mPatTime = Pattern.compile("time\\s*=\\s*\"([^\"]+)\"");
		mPatSeqNo = Pattern.compile("seqno\\s*=\\s*\"([^\"]+)\"");
		mPatAckNo = Pattern.compile("ackno\\s*=\\s*\"([^\"]+)\"");
		mPatLength = Pattern.compile("length\\s*=\\s*\"([^\"]+)\"");
		mPatFlength = Pattern.compile("flength\\s*=\\s*\"([^\"]+)\"");
		mPatReadable = Pattern.compile("readable\\s*=\\s*\"([^\"]+)\"");
	}
	
	/**
	 * Reads the next TCP packet entry from the stream provided in the ctor.
	 * @return The next TcpPacket parsed from the stream, null if reached eof.
	 * @throws IOException If an error occurs while reading from the file.
	 */
	public TcpPacket getNextPacket() throws IOException
	{
		StringBuffer sb = new StringBuffer();
		
		// read until the first [ is found, or quit
		char c = (char)mReader.read();
		while (c != (char)-1 && c != '[') 
		{
			c = (char)mReader.read();
		}
		if (c == -1) return null;
		
		// next read until the first ] is found, or quit
		c = (char)mReader.read();
		while (c != (char)-1 && c != ']') 
		{
			sb.append(c);
			c = (char)mReader.read();
		}
		if (c == (char)-1) return null;

		// read one more character off the reader (the newline)
		c = (char)mReader.read();
		if (c == (char)-1) return null;
		
		// if string does not match the xmppdump header pattern, quit
		Matcher m = mPatEvent.matcher(sb);
		if (!m.find())
		{
			return null;
		}
		
		// instantiate a packet and set member data
		TcpPacket p = new TcpPacket();
		
		m = mPatFrom.matcher(sb);
		if (m.find()) p.src = m.group(1);
		
		m = mPatTo.matcher(sb);
		if (m.find()) p.dst = m.group(1);
		
		m = mPatTcpflags.matcher(sb);
		if (m.find()) 
		{
			p.tcpflags = 0;
			String flags = m.group(1);
			if (flags.indexOf('U') >= 0) p.tcpflags |= 0x20;
			if (flags.indexOf('A') >= 0) p.tcpflags |= 0x10;
			if (flags.indexOf('P') >= 0) p.tcpflags |= 0x08;
			if (flags.indexOf('R') >= 0) p.tcpflags |= 0x04;
			if (flags.indexOf('S') >= 0) p.tcpflags |= 0x02;
			if (flags.indexOf('F') >= 0) p.tcpflags |= 0x01;
		}
		
		m = mPatTime.matcher(sb);
		if (m.find()) p.time = new Timestamp(Long.parseLong(m.group(1)));

		m = mPatSeqNo.matcher(sb);
		if (m.find()) p.seqno = Long.parseLong(m.group(1)); 

		m = mPatAckNo.matcher(sb);
		if (m.find()) p.ackno = Long.parseLong(m.group(1));

		m = mPatLength.matcher(sb);
		if (m.find()) p.pktlen = Integer.parseInt(m.group(1));

		int flength = 0;
		m = mPatFlength.matcher(sb);
		if (m.find()) flength = Integer.parseInt(m.group(1));

		p.readable = true;
		m = mPatReadable.matcher(sb);
		if (m.find()) p.readable = Boolean.parseBoolean(m.group(1));

		// if the tcp packet had a payload of non-zero length, read it
		if (flength > 0)
		{
			// if xmppdump flagged the data as readable, copy to the event
			if (p.readable)
			{
				// read packet data, note that flength is the length of the
				// text data as represented in the file, which will include
				// any whitespace added by the xmppdump pretty-printer.
				char []tcpData = new char[flength];
				if (mReader.read(tcpData, 0, flength) != flength) 
				{
					System.err.println(
                            "Read underflow on tcpdata.  Expected " + 
                            flength + " bytes.");
				}
				
				// remove excess spaces and tabs here 
				// (will be reformatted in the GUI anyway)
				char[] before = tcpData;
				char[] after = new char[before.length];
				int cur = 0;
				boolean lastWasWhitespace = false;
				for (char cc : before)
				{
					if (cc == ' ' || cc == '\t')
					{
						if (!lastWasWhitespace)
						{
							after[cur++] = ' ';
							lastWasWhitespace = true;
						}
					}
					else
					{
						after[cur++] = cc;
						lastWasWhitespace = false;
					}
				}
				p.data = new String(after);
				
				// guard against huge packets
				if (p.data.length() > 65535)
				{
					p.data = p.data.substring(0, 65523) + "\n[TRUNCATED]";
				}

				// attempt to parse xmpp stanzas from the packet
				p.stanzas = new XmppPacketParser().parse(p.data);
			}
			else
			{
				p.data = new String("[data not readable]");
			}
		}
		else
		{
			p.data = new String();
		}
	
		return p;
	}
}
