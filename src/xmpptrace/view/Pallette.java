/**
 * (c) Copyright 2015 Andrew Biggs
 * This code is available under the Apache License, version 2: http://www.apache.org/licenses/LICENSE-2.0.html
 */

package xmpptrace.view;

import java.awt.Color;
import java.awt.Font;

/**
 * Interface to capture general color and font settings.
 * 
 * @author adb
 */
public interface Pallette 
{
    // change these if you prefer different theme colors for the display
    public static final Color THEME_COLOR_1 = new Color(0x2e5994);
    // public static final Color THEME_COLOR_2 = new Color(0x800000);
    public static final Color THEME_COLOR_2 = new Color(0x008000);
    
	// colors for xml content rendering
	public static final Color ELEMENT = THEME_COLOR_1;
	public static final Color ATTRIBUTE = THEME_COLOR_2;
	public static final Color XML_TEXT = Color.black;
	
    // colors for sequence event panel rendering
	public static final Color BG_EVENT_PANEL = Color.white;
	public static final Color ACTOR_TEXT = Color.black;
	public static final Color ACTOR = THEME_COLOR_1;
	public static final Color SELECTED_ACTOR = Color.white;
	public static final Color EVENT = Color.black; // THEME_COLOR_1;
	public static final Color EVENT_TEXT  = Color.black; // THEME_COLOR_1;
	public static final Color SELECTED_EVENT = THEME_COLOR_1;

	// colors for xpath/regex response text
	public static final Color FOUND = Color.black;
	public static final Color NOT_FOUND = Color.black;
	public static final Color BAD_SYNTAX = Color.red;

	// fonts used for sequence event panel and xml content
	public static final Font FONT_EVENT_TEXT = new Font(null,0,12);
    public static final Font FONT_SELECTED_EVENT_TEXT = new Font(null,Font.BOLD,12);
	public static final Font FONT_ACTOR_NAME = new Font(null,Font.BOLD,14);
}
