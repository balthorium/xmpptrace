package xmpptrace.action;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import xmpptrace.model.Pair;

/**
 * Parses TCP packet data for xmpp stanzas.  A TCP packet can contain more than
 * a single stanza, and may also contain stanza fragments.  This class attempts
 * to figure out the stanza boundaries, and then parse each stanza into its own
 * DOM document.
 * 
 * @author adb
 */
public class XmppPacketParser 
{
	/**
	 * Ctor.
	 */
	public XmppPacketParser()
	{
	}

	/**
	 * Exception used internally to the XmppPacketParser class.  Used to
	 * indicate that malformed xml is present and cannot be parsed.
	 * @author adb
	 */
	private static class PacketParseException extends Exception 
	{
		private static final long serialVersionUID = 1L;
		public PacketParseException(String message)
		{
			super(message);
		}
	}
	
	/**
	 * Parses TCP packet data into an array of DOM documents.  Note that
	 * the current implementation will fail to parse subsequent stanzas
	 * if a stanza is found to be malformed.
	 * @param tcpData Text data from a TCP packet.
	 * @return An array of DOM documents, ordered as found in the packet
	 */
	public ArrayList<Document> parse(String tcpData)
	{
		ArrayList<Document> domlist = new ArrayList<Document>();
		byte[] xmlin = tcpData.getBytes();
		int r = 0;
		
		// handle degenerate case of zero-length packet
		if (xmlin.length == 0)
		{
			return domlist;
		}
		
		// step passed any leading whitespace or XML declaration
		r = bleedWhitespace(xmlin, r);
		r = bleedXmlDeclaration(xmlin, r);
		r = bleedWhitespace(xmlin, r);

		// grab as many stanzas as possible (can be more than one)
		try
		{
			Pair<Integer, Document> result;
			while (r < xmlin.length)
			{
				result = getNextStanza(xmlin, r);
				r = result.first.intValue();
				if (result.second != null)
				{
					domlist.add(result.second);
				}
				r = bleedWhitespace(xmlin, r);
			}
		} catch (Exception e) 
		{
			// ignore errors, this is just best-effort
		}
		
		return domlist;
	}

	/**
	 * Increment buffer read cursor beyond any immediate whitespace.
	 * @param xmlin Buffer being read.
	 * @param r Current read position.
	 * @return New read position.
	 */
	private int bleedWhitespace(byte[] xmlin, int r)
	{
		while (r < xmlin.length)
		{
			if (!Character.isWhitespace(xmlin[r]))
			{
				break;
			}
			++r;
		}
		return r;
	}

	/**
	 * Increment buffer read cursor beyond any immediate XML declaration.
	 * @param xmlin Buffer being read.
	 * @param r Current read position.
	 * @return New read position.
	 */
	private int bleedXmlDeclaration(byte[] xmlin, int r)
	{
		if (xmlin.length - r < 4) return r;
		if (xmlin[r] == '<' && xmlin[r+1] == '?')
		{
			r += 2;
			while (r < xmlin.length)
			{
				if (xmlin[r-2] == '?' && xmlin[r-1] == '>')
				{
					break;
				}
				++r;
			}
		}
		return r;
	}
		
	/**
	 * Attempt to read a single stanza from the given buffer, starting at
	 * the given read location.
	 * @param xmlin Buffer to read from.
	 * @param r Position to begin reading.
	 * @return A Pair<Integer, Document>, where the integer represents
	 *         the new read location immediately after what was read by
	 *         this function, and the Document is the DOM to which the
	 *         stanza parsed.  If Parsing failed the Document will be
	 *         null.
	 * @throws PacketParseException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 * @throws IOException
	 */
	private Pair<Integer, Document> getNextStanza(byte[] xmlin, int r) throws 
			PacketParseException, 
			ParserConfigurationException, 
			SAXException, 
			IOException
	{	
		// step over any leading non-element stuff (like bosh http headers)
		while (r < xmlin.length && xmlin[r] != '<') ++r;

		// remember the beginning
		int rorig = r;
		
		// start off: no tag, empty stack, no tcpdata stream or dom
		String tag = null;
		Stack<String> tagStack = new Stack<String>();
		ByteArrayInputStream stm = null;
		Document dom = null;

		// hunt for a complete stanza
		for (; r < xmlin.length; ++r)
		{
			// handle beginning of a tag
			if (xmlin[r] == '<')
			{
				// <xxxx <
				if (tag != null)
				{
					throw new PacketParseException("Found illegal <.");
				}

				// find the tag name 
				int k = ++r;
				while (k < xmlin.length)
				{
					byte c = xmlin[k];
					if (Character.isWhitespace(c) ||
							((k != r) && (c == '/')) ||
							c == '>')
					{
						break;
					}
					k++;
				}
				if (k <= xmlin.length)
				{
					tag = new String(xmlin, r, k - r);
					r = k;
				}
			}
		
			// handle ending of a tag
			if (xmlin[r] == '>')
			{	
				// xxxx>
				if (tag == null)
				{
					throw new PacketParseException("Found illegal >.");
				}

				// </xxxx>
				else if (tag.charAt(0) == '/')
				{
					String lastTag = tagStack.pop();
					if (!lastTag.equals(tag.substring(1)))
					{
						throw new PacketParseException("Found unmatched tag.");
					}
				}
				// <xxxx>
				else if (xmlin[r-1] != '/')
				{
					tagStack.push(tag);
				}
				// <xxxx/>
				else
				{
					// nothing needs to be done here
				}
				tag = null;
			}
			
			// if no current tag, and no tagStack, we've got a stanza
			if (tag == null && tagStack.isEmpty())
			{
				stm = new ByteArrayInputStream(xmlin, rorig, ++r - rorig);
				break;
			}
		}
		
		// if a complete stanza was found, parse it
		if (stm != null)
		{
			DocumentBuilderFactory factory = 
                    DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new ErrorHandler() 
			{
				public void error(SAXParseException arg0) 
                        throws SAXException {}
				public void fatalError(SAXParseException arg0) 
                        throws SAXException {}
				public void warning(SAXParseException arg0) 
                        throws SAXException {}
				
			});
			dom = builder.parse(stm);
		}
	
		// return current read position, and a new dom if we have it
		Pair<Integer, Document> retval = new Pair<Integer, Document>();
		retval.first = new Integer(r);
		retval.second = dom;
		return retval;
	}
}
