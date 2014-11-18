package xmpptrace.model;

import java.io.InputStream;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Class to implement the NameSpaceContext interface for XMPP related XPath
 * queries.  This is used throughout the application when instantiating
 * new XPath objects.  It provides a mapping between standard XMPP namespaces
 * and prefixes which can be used in XPath queries.  This mapping is displayed
 * in editable tablular form in the user interface. It is preloaded with 
 * namespaces from:
 * 	
 * http://xmpp.org/registrar/namespaces.xml
 * 
 * @author adb
 */
public class XmppNamespaceContext
		implements NamespaceContext 
{
	TreeMap<String, String> mPrefix2NsMap;
	TreeMap<String, TreeSet<String>> mNs2PrefixMap;
	
	private class XmlErrorHandler
			implements ErrorHandler
	{
		public void error(SAXParseException arg0) throws SAXException {}
		public void fatalError(SAXParseException arg0) throws SAXException {}
		public void warning(SAXParseException arg0) throws SAXException {}
	}
		
	// this class is a singleton
	static XmppNamespaceContext sInstance;
	static
	{
		sInstance = new XmppNamespaceContext();
	}
	
	/**
	 * Get reference to singleton instance.
	 * @return Reference to singleton instance.
	 */
	static public XmppNamespaceContext getInstance()
	{
		return sInstance;
	}
	
	/**
	 * Ctor is private, only one instance exists and is returned
	 * by getInstance() method.
	 */
	private XmppNamespaceContext()
	{
		mPrefix2NsMap = new TreeMap<String, String>();
		mNs2PrefixMap = new TreeMap<String, TreeSet<String>>();
		
		// we add these explicitly, as they are needed by the xmpp augur
		addPrefixNsPair("jc", "jabber:client");
		addPrefixNsPair("jia", "jabber:iq:auth");
		addPrefixNsPair("xmpp-sasl", "urn:ietf:params:xml:ns:xmpp-sasl");
		addPrefixNsPair("xmpp-bind", "urn:ietf:params:xml:ns:xmpp-bind");
		addPrefixNsPair("bosh", "http://jabber.org/protocol/httpbind");
		addPrefixNsPair("xdata", "jabber:x:data"); 
		
		// load namespaces.xml downloaded from xmpp.org
		try
		{
			DocumentBuilderFactory factory = 
					DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new XmlErrorHandler());
			InputStream is = getClass().getResourceAsStream("namespaces.xml");
			if (is == null)
			{
			    System.out.println("namespace.xml file not found.");
			    return;
			}
			Document doc = builder.parse(is);
			
			NodeList namespaces = 
					doc.getChildNodes().item(0).getChildNodes();
			int numNamespaces = namespaces.getLength();
			for (int i = 0; i < numNamespaces; ++i)
			{
				Node ns = namespaces.item(i);
				if (!ns.getNodeName().equals("ns")) continue;
				NodeList nsc = ns.getChildNodes();
				for (int j = 0; j < nsc.getLength(); ++j)
				{
					Node name = nsc.item(j);
					if (name.getNodeName().equals("name"))
					{
						addPrefixNsPair("ns" + String.valueOf(i), 
								name.getTextContent());
						break;
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

	}
	
	/**
	 * Inserts a prefix-to-namespace mapping to the context.
	 * @param prefix Prefix string.
	 * @param ns Matching namespace string.
	 */
	public void addPrefixNsPair(String prefix, String ns)
	{
		// update prefix-to-namespace mapping
		String prevNs = mPrefix2NsMap.get(prefix);
		if (prevNs != null)
		{
			mNs2PrefixMap.get(prevNs).remove(prefix);
		}
		mPrefix2NsMap.put(prefix, ns);
		
		// update namespace-to-prefix mapping
		TreeSet<String> prefixes = mNs2PrefixMap.get(ns);
		if (prefixes == null)
		{
			prefixes = new TreeSet<String>();
			mNs2PrefixMap.put(ns, prefixes);
		}
		prefixes.add(prefix);
	}
	
	/**
	 * Returns a namespace-to-prefix map.
	 * @return Map of namespaces to prefixes.
	 */
	public TreeMap<String, TreeSet<String>> getPrefixMap()
	{
		return mNs2PrefixMap;
	}
	
	/**
	 * Returns the namespace corresponding to the given prefix.
	 * @param prefix Prefix for which a namespace is requested.
	 * @return The namespace for the given prefix.
	 */
	@Override
    public String getNamespaceURI(String prefix) 
    {
    	String ns = mPrefix2NsMap.get(prefix);
    	if (ns == null)
    	{
    		ns = XMLConstants.NULL_NS_URI;
    	}
    	return ns;
    }

    /**
	 * Returns a prefix corresponding to the given namespace.
	 * @param namespaceURI Namespace for which a prefix is requested.
	 * @return The prefix for the given namespace.
	 */    
	@Override
    public String getPrefix(String namespaceURI) 
    {
		TreeSet<String> prefixes = mNs2PrefixMap.get(namespaceURI);
		if (prefixes != null)
		{
			return prefixes.first();
		}
		return null;
    }
    
	/**
	 * Returns an iterator over a set of prefixes which match
	 * the given namespace.  
	 * @param namespaceURI Namespace for which prefixes are requested.
	 */
	@Override
    public Iterator<String> getPrefixes(String namespaceURI) 
    {
		return mNs2PrefixMap.get(namespaceURI).iterator();
    }
}
