/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

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
