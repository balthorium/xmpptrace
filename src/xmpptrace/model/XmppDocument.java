/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.model;


import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import xmpptrace.view.Pallette;

/**
 * Derives from the Swing DefaultStyledDocument, and implements a document
 * model specifically suited for presenting the tcp contents of a packet on
 * an xmpp stream.  This is similar to how you would present other types
 * of xml formatted text, however this document must be tolerant of malformed 
 * xml.  Malformed xml can come from having more than one stanza in a given 
 * packet (with no common root in the doc itself), or from fragmentation of 
 * stanzas across multiple packets.
 * 
 * @author adb
 */
public class XmppDocument 
		extends DefaultStyledDocument 
{
	// formatting attribute objects, init'd in static block
	private static final long serialVersionUID = 1L;
	private static final String sIndent;	
	private static final SimpleAttributeSet sDefaultAttrSet;
	private static final SimpleAttributeSet sTagNameAttrSet;
	private static final SimpleAttributeSet sAttrNameAttrSet;
	private static final SimpleAttributeSet sAttrValueAttrSet;

	// used for parsing and pretty-printing logic
	private String mXmpp;
	private int mIndentLevel;
	private boolean mSlashFound;
	
	// exception used internally to signal an indent underflow.
	// this can happen if the xmpp begins with a fragmented stanza.
	@SuppressWarnings("serial")
	private static class IndentUnderflowException extends Exception {}
	
	// static block to initialize basic formatting objects
	static
	{
		sIndent = new String("        ");
		
		sDefaultAttrSet = new SimpleAttributeSet();
		sTagNameAttrSet = new SimpleAttributeSet();
		sAttrNameAttrSet = new SimpleAttributeSet();
		
		sDefaultAttrSet.addAttribute(
	    		StyleConstants.ColorConstants.Foreground, 
				Pallette.XML_TEXT);
	
		sTagNameAttrSet.addAttribute(
				StyleConstants.ColorConstants.Foreground, 
				Pallette.ELEMENT);
	
		sAttrNameAttrSet.addAttribute(
				StyleConstants.ColorConstants.Foreground, 
				Pallette.ATTRIBUTE);

		sAttrValueAttrSet = sDefaultAttrSet;
	}

	/**
	 * Ctor, initializes the document with the given xmpp protocol string.
	 * @param xmpp Protocol string taken directly from payload of tcp packet.
	 */
	public XmppDocument(String xmpp)
	{
		mXmpp = xmpp;
		boolean done = false;
		int initIndentLevel = 0;
		while (!done)
		{
			try
			{
				parse(initIndentLevel);
				done = true;
			}
			catch (IndentUnderflowException e)
			{
				try {
					remove(0, getLength());
				} catch (Exception e1) {}
				++initIndentLevel;
			}
			catch (Exception e)
			{
				e.printStackTrace();
				done = true;
			}
		}
	}
	
	/**
	 * Parse the mXmpp string into the document, with suitable formatting.
	 * Start at the given indent level.  If this fails with an indent
	 * underflow, an exception will be thrown and the caller may try
	 * to re-parse at a deeper indent level.
	 * @param initIndentLevel Indentation level to start at.
	 * @throws BadLocationException 
	 * @throws IndentUnderflowException
	 */
	private void parse(int initIndentLevel) 
			throws BadLocationException, 
			IndentUnderflowException 
	{
		mIndentLevel = initIndentLevel;
		char[] xmlin = mXmpp.toCharArray();
		int r = 0; 
		while (r < xmlin.length) 
		{
			if (xmlin[r] == '<')
			{
				mSlashFound = false;
				r = appendOpeningBrace(xmlin, r);
				r = bleedWhitespace(xmlin, r);
				r = appendTagName(xmlin, r);
				r = bleedWhitespace(xmlin, r);
				int attrCount = 0;
				while ((r < xmlin.length) && 
						(xmlin[r] != '/') && 
						(xmlin[r] != '?') && 
						(xmlin[r] != '>'))
				{
					if (attrCount++ > 0)
					{
						appendString("\n", sDefaultAttrSet);				
						appendIndent(mIndentLevel+1);
					}
					r = appendAttribute(xmlin, r);
					r = bleedWhitespace(xmlin, r);
				}
				r = appendClosingBrace(xmlin, r);
				if (!mSlashFound)
				{
					mIndentLevel++;
				}
			}
			else
			{
				r = appendCDATA(xmlin, r);
			}
			r = bleedWhitespace(xmlin, r);
		}
	}
	
	/**
	 * Advance the read position to the next non-whitespace character.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */
	private int bleedWhitespace(char[] xmlin, int r)
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
	 * Read from the current position, which should be an opening brace.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendOpeningBrace(char[] xmlin, int r) 
		throws IndentUnderflowException
	{
		if (r < xmlin.length)
		{
			if ((r+1 < xmlin.length) && 
					(xmlin[r] == '<') && 
					(xmlin[r+1] == '/'))
			{	
				mIndentLevel--;
				if (mIndentLevel < 0)
				{
					throw new IndentUnderflowException();
				}
				appendIndent(mIndentLevel);
				appendString("</", sDefaultAttrSet);
				mSlashFound = true;
				r+=2;
			}
			else if ((r+1 < xmlin.length) && 
					(xmlin[r] == '<') && 
					(xmlin[r+1] == '?'))
			{
				appendString("<?", sDefaultAttrSet);
				mSlashFound = true;
				r+=2;
			}
			else if (xmlin[r] == '<')
			{
				appendIndent(mIndentLevel);
				appendString("<", sDefaultAttrSet);
				++r;
			}
		}
		return r;
	}

	/**
	 * Append an indent to the end of the document.
	 * @param level
	 */
	private void appendIndent(int level)
	{
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < level; ++i)
		{
			sb.append(sIndent);
		}
		appendString(sb.toString(), sDefaultAttrSet);
	}
	
	/**
	 * Read from the current position, which should be a closing brace.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendClosingBrace(char[] xmlin, int r)
	{
		if (r < xmlin.length)
		{
			if ((r+1 < xmlin.length) && (xmlin[r] == '/') && 
                    (xmlin[r+1] == '>'))
			{	
				appendString("/>\n", sDefaultAttrSet);
				mSlashFound = true;
				r+=2;
			}
			else if ((r+1 < xmlin.length) && (xmlin[r] == '?') && 
                    (xmlin[r+1] == '>'))
			{	
				appendString("?>\n", sDefaultAttrSet);
				r+=2;
			}
			else if (xmlin[r] == '>')
			{	
				appendString(">\n", sDefaultAttrSet);
				++r;
			}
		}
		return r;
	}
	
	/**
	 * Read from the current position, which should be the beginning of a
	 * tag name.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendTagName(char[] xmlin, int r)
	{
		while (r < xmlin.length)
		{
			if (Character.isWhitespace(xmlin[r]) || 
					xmlin[r] == '>' ||
					xmlin[r] == '/')
			{
				break;
			}
			appendString(
                    String.valueOf(xmlin[r++]), sTagNameAttrSet);			
		}
		return r;
	}
	
	/**
	 * Read from the current position, which should be the beginning of an
	 * attribute.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendAttribute(char[] xmlin, int r)
	{
		r = appendAttributeName(xmlin, r);
		r = bleedWhitespace(xmlin, r);
		r = appendEquals(xmlin, r);
		r = bleedWhitespace(xmlin, r);
		r = appendQuote(xmlin, r);
		r = appendAttributeValue(xmlin, r);
		r = appendQuote(xmlin, r);
		return r;
	}

	/**
	 * Read from the current position, which should be the beginning of an
	 * attribute name.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendAttributeName(char[] xmlin, int r)
	{
		appendString(" ", sDefaultAttrSet);			
		while (r < xmlin.length)
		{
			if (Character.isWhitespace(xmlin[r]) || (xmlin[r] == '='))
			{
				break;
			}
			appendString(
                    String.valueOf(xmlin[r++]), sAttrNameAttrSet);			
		}
		return r;		
	}

	/**
	 * Read from the current position, which should be an '='.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendEquals(char[] xmlin, int r)
	{
		if ((r < xmlin.length) && (xmlin[r] == '='))
		{	
			appendString("=", sDefaultAttrSet);
			++r;
		}
		return r;
	}

	/**
	 * Read from the current position, which should be a quote.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendQuote(char[] xmlin, int r)
	{
		if ((r < xmlin.length) && ((xmlin[r] == '\'') || (xmlin[r] == '\"')))
		{	
			appendString(String.valueOf(xmlin[r++]), sDefaultAttrSet);
		}
		return r;
	}

	/**
	 * Read from the current position, which should be the beginning of an
	 * attribute value.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendAttributeValue(char[] xmlin, int r)
	{
		while (r < xmlin.length)
		{
			if ((xmlin[r] == '\'') || (xmlin[r] == '\"'))
			{
				break;
			}
			appendString(
                    String.valueOf(xmlin[r++]), sAttrValueAttrSet);			
		}
		return r;
	}
	
	/**
	 * Read from the current position, which should be the beginning of an
	 * CDATA block.
	 * @param xmlin Read buffer.
	 * @param r Current read position.
	 * @return New read position.
	 */	
	private int appendCDATA(char[] xmlin, int r)
	{
		appendIndent(mIndentLevel);
		
		StringBuffer cdata = new StringBuffer();
		while (r < xmlin.length && xmlin[r] != '<')
		{
			cdata.append(String.valueOf(xmlin[r]));
			if (xmlin[r] == '\n' || xmlin[r] == '\r')
			{
				for (int i = 0; i < mIndentLevel; ++i)
				{
					cdata.append(sIndent);
				}	
			}
			r++;
		}
		appendString(cdata.toString().trim(), sDefaultAttrSet);
		appendString("\n", sDefaultAttrSet);
		return r;
	}	
	
	/**
	 * Append the given string to this document, using the given
	 * attribute set.
	 * @param str String to append to the end of the document.
	 * @param as Attribute set to use when formatting the write.
	 */
	private void appendString(String str, SimpleAttributeSet as)
	{
		try 
		{
			insertString(getLength(), str, as);
		} 
		catch (BadLocationException e) 
		{
			e.printStackTrace();
		}
	}
}

