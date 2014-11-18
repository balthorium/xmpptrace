package xmpptrace.action;

import java.io.IOException;

import xmpptrace.model.TcpPacket;
/**
 * A simple common interface for both TcpDumpStreamParser and 
 * XmppDumpStreamParser, so the db may use them identically
 * for slurping in protocol input streams.
 * 
 * @author adb
 *
 */
public interface StreamParser 
{
	public TcpPacket getNextPacket() throws IOException;
}
