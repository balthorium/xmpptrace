/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.action;

import java.util.ArrayList;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.ProgressMonitor;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import xmpptrace.model.XmppNamespaceContext;
import xmpptrace.model.TcpPacket;
import xmpptrace.store.Database;

/**
 * The Augur was a priest and official in the classical world, especially 
 * ancient Rome and Etruria. His main role was to interpret the will of the
 * gods by studying the flight of birds: whether they are flying in 
 * groups/alone, what noises they make as they fly, direction of flight and
 * what kind of birds they are.  This was known as "taking the auspices."
 *  
 *     - Wikipedia
 * 
 * @author adb
 */
public class XmppAugur
{	
	private XPathExpression mXpathClientLegacyAuth;
	private XPathExpression mXpathClientSaslAuth;
	private XPathExpression mXpathComponentFrom;
	private XPathExpression mXpathComponentTo;
	private XPathExpression mXpathXmppSessionLocalAddress;
	private XPathExpression mXpathXmppSessionLocalPort;
	private XPathExpression mXpathBoshSessionLogicalJid;
	private Pattern mRegexBoshClientSessionId;

	// database on which to operate
    Database mDb;

	// map of SESSIONID to client address
	private TreeMap<String, ArrayList<String>> mBoshSessionToClientAddressMap;
	
	// map of client actor to xmpp/bosh service address
	private TreeMap<String, String> mClientActorToServiceAddressMap;

	/**
	 * Constructor.
	 */
	public XmppAugur()
	{
	    // get reference to database singleton
        mDb = Database.getInstance();

	    mBoshSessionToClientAddressMap = 
				new TreeMap<String, ArrayList<String>>();
		mClientActorToServiceAddressMap = 
				new TreeMap<String, String>();
		
		XPathFactory xpf = XPathFactory.newInstance();
		XPath xp = xpf.newXPath();
		xp.setNamespaceContext(XmppNamespaceContext.getInstance());
		try 
		{
			// xpath strings are defined along with the methods that use them
			mXpathClientLegacyAuth = xp.compile(mXpathStrClientLegacyAuth);
			mXpathClientSaslAuth = xp.compile(mXpathStrClientSaslAuth);
			mXpathComponentFrom = xp.compile(mXpathStrComponentFrom);
			mXpathComponentTo = xp.compile(mXpathStrComponentTo);	
			mXpathXmppSessionLocalAddress = 
					xp.compile(mXpathStrXmppSessionLocalAddress);
			mXpathXmppSessionLocalPort = 
					xp.compile(mXpathStrXmppSessionLocalPort);
			mXpathBoshSessionLogicalJid = 
				    xp.compile(mXpathStrBoshSessionLogicalJid);
			mRegexBoshClientSessionId = 
                    Pattern.compile(mRegexStrBoshClientSessionId);
		} 
		catch (XPathExpressionException e) 
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * From the packets contained within the table provided in the constructor, 
	 * this will search the xmpp traffic data and attempt to discover the 
	 * identity of the participating xmpp clients and components, and associate 
	 * these with the various ip addresses and ports.  When such an association 
	 * can be made, this will automatically set the actor name in the address
	 * table.
	 * 
	 * Note - its best not execute this directly on the swing event thread.
	 */
	public void takeAuspices() 
	{
	    // pop up a progress monitor
	    final int numPackets = mDb.getPacketCount();
        final ProgressMonitor pm = new ProgressMonitor(
                xmpptrace.view.XmppTraceFrame.getInstance(),
                "Identifying actors...", null, 0, numPackets * 2);
        
		// primary pass: actor discovery
		mDb.iterateOverPackets(new Database.XmppPacketFetchCallback() 
		{
		    private int progress = 0;
		    public void processPacket(TcpPacket p)		    
    		{
		        if (p.stanzas != null)
    			{
    				for (Document stanza: p.stanzas)
    				{
    					try 
    					{
    						discoverXmppClientByLegacyAuth(stanza, p);
    						discoverXmppClientBySaslAuth(stanza, p);
    						discoverXmppComponentByFromAttr(stanza, p);
    						discoverXmppComponentByToAttr(stanza, p);
    					} 
    					catch (XPathExpressionException e) 
    					{
    						e.printStackTrace();
    					}
    				}
    			}
    			discoverBoshClientAddresses(p);
    			pm.setProgress(++progress);
                if (pm.isCanceled()) return;
    		}
		});

		// secondary pass: socket matching, actor linking
        mDb.iterateOverPackets(new Database.XmppPacketFetchCallback() 
        {
            private int progress = numPackets;
            public void processPacket(TcpPacket p)      
            {
     			if (p.stanzas != null)
    			{
    				for (Document stanza: p.stanzas)
    				{
    					try 
    					{
    						matchXmppServiceActorBySessionCreate(stanza, p);
    						matchBoshServiceActorBySessionCreate(stanza, p);
    					} 
    					catch (XPathExpressionException e) 
    					{
    						e.printStackTrace();
    					}
    				}
    			}
                pm.setProgress(++progress);
                if (pm.isCanceled()) return;
            }
        });
        
		matchBoshClientActorsBySessionID();
        pm.setProgress(numPackets * 2);
	}

	/**
	 * Private function to discover the identity of an xmpp client based
	 * on legacy auth.  If the given stanza matches the legacy auth xpath
	 * query below, then the text contents of the matched <username> element
	 * should be used as the actor name for the IP address that sent the
	 * packet.
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void discoverXmppClientByLegacyAuth(
			Document stanza, TcpPacket packet)
			throws XPathExpressionException
	{		
		NodeList nodes = (NodeList)mXpathClientLegacyAuth.evaluate(
				stanza, XPathConstants.NODESET);

		if ((nodes == null) || (nodes.getLength() == 0)) return;
		Node node = nodes.item(0);

		String username = node.getTextContent();
		if (username == null || username.length() == 0) return;
		
		username = username.trim();
		mDb.setAddressActor(packet.src, username);
		
		mClientActorToServiceAddressMap.put(username, packet.dst);
	}

	private static final String mXpathStrClientLegacyAuth = 
			"/jc:iq[@type='get']/jia:query/jia:username | " +
			"/iq[@type='get']/jia:query/jia:username |" +
			"/bosh:body/jc:iq[@type='get']/jia:query/jia:username";

	/**
	 * Private function to discover the identity of an xmpp client based
	 * on SASL authentication.  If the given stanza matches the xpath 
	 * query below, then the text contents of the matched 
	 * <bind> element should be used as the actor name for the IP address 
	 * that received the packet.  
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void discoverXmppClientBySaslAuth(
			Document stanza, TcpPacket packet)
			throws XPathExpressionException
	{		
			NodeList nodes = (NodeList)mXpathClientSaslAuth.evaluate(
					stanza, XPathConstants.NODESET);

			if ((nodes == null) || (nodes.getLength() == 0)) return;
			Node node = nodes.item(0);

			String username = node.getTextContent();
			if (username == null || username.length() == 0) return;
			
			username = username.trim();
			mDb.setAddressActor(packet.dst, username);

			mClientActorToServiceAddressMap.put(username, packet.src);
	}
	
	private static final String mXpathStrClientSaslAuth = 
		"/jc:iq[@type='result']/xmpp-bind:bind/xmpp-bind:jid | " +
		"/iq[@type='result']/xmpp-bind:bind/xmpp-bind:jid";

	/**
	 * Private function to discover the identity of an xmpp component based
	 * on a packet sent.  If the given stanza matches the xpath query below, 
	 * the matched element's "from" attribute should be the comp-id of the 
	 * component that sent the packet.  This is all based on the assumption
	 * that packets sent to "config@-internal" and "control@-internal" are 
	 * confined to the socket immediately between a component and its own 
	 * router (not forwarded between routers or sent to other components). 
	 * Could be a dicey assumption, may have to revisit.
	 * 
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void discoverXmppComponentByFromAttr(
			Document stanza, TcpPacket packet) 
		throws XPathExpressionException
    {
		NodeList nodes = (NodeList)mXpathComponentFrom.evaluate(
				stanza, XPathConstants.NODESET);
	
		if ((nodes != null) && (nodes.getLength() == 1))
		{
			Node node = nodes.item(0);
			
			NamedNodeMap attrs = node.getAttributes();
			if (attrs == null) return;
			
			Node from = attrs.getNamedItem("from");
			if ((from == null) || !(from instanceof Attr)) return;
			
			String fromstr = ((Attr)from).getValue();
			if ((fromstr == null) || (fromstr.length() == 0)) return;
			
			fromstr = fromstr.trim();
			fromstr = stripGuid(fromstr);
	        mDb.setAddressActor(packet.src, fromstr);
		}	
	}

	private static final String mXpathStrComponentFrom =
		"/*[@to='config@-internal'] | " +
		"/*[@to='control@-internal'] ";

	/**
	 * Private function to discover the identity of an xmpp component based
	 * on a packet received.  If given stanza matches the xpath query below, 
	 * the matched element's "to" attribute should be the comp-id of the 
	 * component that owns the receiving IP address.  This assumes that <route>
	 * packets of type="component-presence", and not to="presence@-internal",
	 * are only sent directly to components, and not forwarded.  This is
	 * almost certainly a bad assumption, and will probably need to be
	 * modified for clustered deployments.  I have it here for now, because 
	 * it works fine for single-server configurations, and because components 
	 * receive component presence much more often than they send it, so an 
	 * xpath query of this sort is going to get more matches than the "from"
	 * heuristic above.
	 * 
	 * Note: I believe this heuristic breaks down when analyzing clusters
	 * which are not fully meshed.  Will have to revisit when a better 
	 * heuristic can be developed.
	 * 
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void discoverXmppComponentByToAttr(
            Document stanza, TcpPacket packet) 
		throws XPathExpressionException
	{
		NodeList nodes = (NodeList)mXpathComponentTo.evaluate(
				stanza, XPathConstants.NODESET);
	
		if ((nodes != null) && (nodes.getLength() == 1))
		{
			Node node = nodes.item(0);
			
			NamedNodeMap attrs = node.getAttributes();
			if (attrs == null) return;
			
			Node to = attrs.getNamedItem("to");
			if ((to == null) || !(to instanceof Attr)) return;
			
			String tostr = ((Attr)to).getValue();
			if ((tostr == null) || (tostr.length() == 0)) return;
			
			tostr = tostr.trim();
            tostr = stripGuid(tostr);
	        mDb.setAddressActor(packet.dst, tostr);
		}	
	}

	private static final String mXpathStrComponentTo =
		"/route[@type='component-presence']" +
		"[not(@to='presence@-internal')]";
	
	/**
	 * Discovers BOSH client-sent packets and map their session IDs to the 
	 * packet sender's address.  This is used later to match bosh client 
	 * sockets which service the same client (usually two per client).
	 * @param packet A tcp packet which may contain http/bosh headers.
	 * @throws XPathExpressionException
	 */
	private void discoverBoshClientAddresses(TcpPacket packet)
	{
		Matcher m = mRegexBoshClientSessionId.matcher(packet.data);
		if (m.find()) 
		{
			String sessionid = m.group(1);
			ArrayList<String> addresses = 
					mBoshSessionToClientAddressMap.get(sessionid);
			if (addresses == null)
			{
				addresses = new ArrayList<String>();
				mBoshSessionToClientAddressMap.put(sessionid, addresses);
			}
			addresses.add(packet.src);
		}
	}

	private static final String mRegexStrBoshClientSessionId =
		"[^-]Cookie:\\s+BOSHSESSIONID=(\\S+)";

	/**
	 * Goes through the map of BOSH client addresses and looks for
	 * any that have non-trivial actor names assigned to them.
	 * When these are found, all other BOSH client sockets mapped
	 * to that same SESSIONID will inherit the same actor name.
	 */
	private void matchBoshClientActorsBySessionID()
	{
		for (String sessionid: mBoshSessionToClientAddressMap.keySet())
		{
			String actor = null;
			for (String a1: mBoshSessionToClientAddressMap.get(sessionid))
			{
				actor = mDb.getAddressActor(a1);
				if (actor != null && !actor.equals(a1))
				{
					for (String a2: 
							mBoshSessionToClientAddressMap.get(sessionid))
					{	
						if (a1 != a2)
						{
							mDb.setAddressActor(a2, actor);
						}
					}
					break;
				}
			}
		}
	}

	/**
	 * This looks for session creation stanzas from jsmcp to the router,
	 * and probes for the local-address and local-port fields in the 
	 * form.  The service port identified by those two fields is then
	 * matched to the actor to which the sending component is associated.
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void matchXmppServiceActorBySessionCreate(
            Document stanza, TcpPacket packet) 
		throws XPathExpressionException
	{
        // not useful to proceed unless the packet sender has non-trivial actor
		String actor = mDb.getAddressActor(packet.src); 
		if (actor == null || actor.equals(packet.src)) return;
		
		NodeList ipNode = (NodeList)mXpathXmppSessionLocalAddress.evaluate(
				stanza, XPathConstants.NODESET);
	
		if ((ipNode == null) || (ipNode.getLength() != 1)) return;

		NodeList portNode = (NodeList)mXpathXmppSessionLocalPort.evaluate(
				stanza, XPathConstants.NODESET);
		
		if ((portNode == null) || (portNode.getLength() != 1)) return;
		
		String ip = ipNode.item(0).getTextContent().trim();
		String port = portNode.item(0).getTextContent().trim();
		String svcAddress = ip + ":" + port;
				
		String svcActor = mDb.getAddressActor(svcAddress);
		if (svcActor != null && svcActor.equals(svcAddress))
		{
			mDb.setAddressActor(svcAddress, actor);
		}
	}	
	
	private static final String mXpathStrXmppSessionLocalAddress =
		"/route/xdata:x[@type='submit']" +
		"/xdata:field[@var='local-address']/xdata:value";

	private static final String mXpathStrXmppSessionLocalPort =
		"/route/xdata:x[@type='submit']" +
		"/xdata:field[@var='local-port']/xdata:value";
	
	/**
	 * This looks for session creation stanzas from jsmcp to the router,
	 * and probes for the logical-jid field in the form.  If such a match
	 * is made, it will check the mClientActorToServiceAddressMap for a client 
	 * actor name that matches the logical-jid (either full or just username),
	 * and if such a client actor is found, it is then inferred that the 
	 * service port to which that client communicated as part of its 
	 * authentication is the client-facing service address of the component 
	 * that sent this session creation stanza.  If this component has a non
	 * trivial actor name (ie. not identical to its address:port) then
	 * this same actor name may then be assigned to that service port.
	 * 
	 * @param stanza A stanza that has been sent.
	 * @param packet The tcp packet which contains the stanza.
	 * @throws XPathExpressionException
	 */
	private void matchBoshServiceActorBySessionCreate(
            Document stanza, TcpPacket packet) 
		throws XPathExpressionException
	{
        // not useful to proceed unless the packet sender has non-trivial actor
		String actor = mDb.getAddressActor(packet.src); 
		if (actor == null || actor.equals(packet.src)) return;
		
		NodeList jidNode = (NodeList)mXpathBoshSessionLogicalJid.evaluate(
				stanza, XPathConstants.NODESET);
	
		if ((jidNode == null) || (jidNode.getLength() != 1)) return;

		// try to match on full jid first
		String jid = jidNode.item(0).getTextContent().trim();
		String svcAddress = mClientActorToServiceAddressMap.get(jid);

		// if full jid didn't have a match, try just username
		if (svcAddress == null)
		{
			int idx = jid.indexOf('@');
			if (idx != -1)
			{
				String username = jid.substring(0, idx);
				svcAddress = mClientActorToServiceAddressMap.get(username);
			}			
		}
		
		// if we have a matching service address for the jid/username...
		if (svcAddress != null)
		{
			String svcActor = mDb.getAddressActor(svcAddress);
			if (svcActor != null && svcActor.equals(svcAddress))
			{
				mDb.setAddressActor(svcAddress, actor);
			}		
		}	
	}

	private static final String mXpathStrBoshSessionLogicalJid =
		"/route/xdata:x[@type='submit']" +
		"/xdata:field[@var='logical-jid']/xdata:value";	

    /**
     * Helper function to remove large guid portion from component ids.
     * @param actor Actor name from which to detect and strip guid.
     * @return A guid-stripped version of the actor name.
     */
    private String stripGuid(String actor)
    {
        if ((actor.length() > 32) && (actor.indexOf('.') != -1))
        {   
            actor = actor.substring(0, actor.indexOf('.'));
        }
        return actor;
    }
}
