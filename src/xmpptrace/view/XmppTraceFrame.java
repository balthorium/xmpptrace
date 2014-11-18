package xmpptrace.view;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.SQLException;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;

import xmpptrace.model.ActorTableModel;
import xmpptrace.model.AddressTableModel;
import xmpptrace.model.PacketTableModel;
import xmpptrace.model.XmppDocument;
import xmpptrace.model.XpathPrefixTableModel;
import xmpptrace.store.Database;

/**
 * This is the main frame of the application, and encapsulates most of the 
 * user interface (that which is not implemented in custom components).
 * 
 * @author adb
 */
public class XmppTraceFrame extends JFrame 
{
	private static final long serialVersionUID = 1L;
    
    private static final String sAppName = "XMPP Trace v0.96";

	// standard Swing components
	private JTable mAddressTable;
	private JTable mActorTable;
	private JButton mDownButton;
	private JButton mUpButton;
	private JButton mBottomButton;
	private JButton mTopButton;
	private JLabel mSearchResultLabel;
	private JLabel mLenValue;
	private JLabel mAckValue;
	private JLabel mSeqValue;
	private JLabel mFlagsValue;
	private JLabel mToValue;
	private JLabel mFromValue;
	private JLabel mTimeValue;
	private JScrollBar mSdEventPanelScrollBarHoriz;
	private JScrollBar mSdEventPanelScrollBarVert;
	private JComboBox mSearchComboBox;
	private JTextField mSearchTextField;
	private JCheckBox mXmppOnly;
	private JMenuItem mNewFileMenuItem;
	private JMenuItem mOpenFileMenuItem;
    private JMenuItem mSaveAsFileMenuItem;
    private JMenuItem mImportFileMenuItem;
    private JMenuItem mExitFileMenuItem;
    private JMenuItem mReduceFileMenuItem;

	// singleton instance of the frame
	private static XmppTraceFrame sInstance = null;
	
	// custom components
	private SequenceEventPanel mSdEventPanel;
	private XmppDocumentTextPane mStanzaTextPane;

	// data models for actors and events
	private PacketTableModel mPacketTableModel;
	private AddressTableModel mAddressTableModel;
	private ActorTableModel mActorTableModel;
	
	/**
	 * File filter class for the selection dialog for opening
	 * new db files.
	 * 
	 * @author adb
	 *
	 */
	class XmppTraceFileFilter extends FileFilter
    {                    
        @Override
        public boolean accept(File f)
        {
            return f.getName().endsWith(".h2.db") ||
                    f.isDirectory();
        }

        @Override
        public String getDescription()
        {
            return "XmppTrace Database File Filter";
        }
    };

    /**
     * getInstance returns the singleton instance of this class.
     * 
     * @return The singleton instance of this class.
     */
	public static synchronized XmppTraceFrame getInstance()
	{
	    if (sInstance == null)
	    {
	        sInstance = new XmppTraceFrame();
	    }
	    return sInstance;
	}
	
	/**
	* Application main.  Instantiates and displays the root frame.
	* @param args If non empty, first index contains the name of an xmppdump
	*             file to be read in.
	*/
	public static void main(final String[] args) 
	{
		// Swing calls should be made on the AWT EventQueue thread.
		SwingUtilities.invokeLater(new Runnable() 
		{
			public void run() 
			{
				// instantiate root frame, centered, and visible
				XmppTraceFrame app = XmppTraceFrame.getInstance();
				app.setLocationRelativeTo(null);
                app.setVisible(true);
			
				// if user provided an xmppdump file name on the cl, load it
				if (args.length > 0) 
				{
	                Database db = Database.getInstance();
	                db.loadFromFile(new File(args[0]));
				}
			}
		});
	}
	
	/**
	 * Ctor.  Constructs and initializes the application user interface.
	 */
	public XmppTraceFrame() 
    {
		super();
		
		// create the data models
		mAddressTableModel = new AddressTableModel();
		mActorTableModel = new ActorTableModel();
		mPacketTableModel = new PacketTableModel(mAddressTableModel);
		
		// add the data models as listeners of the database.
		// order is important here, to ensure address table 
		// model is updated before the packet table model.
        Database db = Database.getInstance();
        db.addListener(mAddressTableModel);
        db.addListener(mActorTableModel);
        db.addListener(mPacketTableModel);

			
		// initialize the user interface and listeners	
		initGuiComponents();		
		initListeners();
	}

	private void setTitle()
	{
        Database db = Database.getInstance();
        String dbFileName = db.getDbFileName();
        if (dbFileName != null)
        {
            setTitle(sAppName + " (" + db.getDbFileName() + ")");
        }
        else
        {
            setTitle(sAppName);
        }
	}
	/**
	 * Instantiate Swing components, build user interface.
	 */
	private void initGuiComponents() 
	{
		// configure main application frame
        setTitle();
		GridBagLayout gbl = new GridBagLayout();
		gbl.rowWeights = new double[] {0.1};
		gbl.rowHeights = new int[] {7};
		gbl.columnWeights = new double[] {0.1};
		gbl.columnWidths = new int[] {7};
		getContentPane().setLayout(gbl);

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		getContentPane().add(createVertSplitPane(), new GridBagConstraints(
				0,                         // gridx
				0,                         // gridy
				1,                         // gridwidth
				1,                         // gridheight
				0.0,                       // weightx
				0.0,                       // weighty
				GridBagConstraints.CENTER, // anchor 
				GridBagConstraints.BOTH,   // fill
				new Insets(0, 0, 0, 0),    // insets
				0,                         // ipadx
				0));                       // ipady

		setJMenuBar(createMenuBar());

		pack();
		setSize(1200, 800);
	}

	/**
	 * Exits the application: closes frame and database.
	 */
	private void exit()
	{
        Database db = Database.getInstance();
        try
        {
            db.close();
        } 
        catch (SQLException e1)
        {
            e1.printStackTrace();
        }	  
	}
	
	/**
	 * Initialize listeners, implement basic handling logic in some cases.
	 */
	private void initListeners()
	{
	    // if window is closed, be sure to close database

        this.addWindowListener(new WindowAdapter() 
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                XmppTraceFrame.this.exit();
            }
        });

        // event panel wants to know about keyboard events
	    KeyboardFocusManager.getCurrentKeyboardFocusManager()
	    		.addKeyEventDispatcher(mSdEventPanel);
	
	    // event panel also wants references and listens to scrollbars.
		mSdEventPanel.setScrollBars(
				mSdEventPanelScrollBarHoriz, 
				mSdEventPanelScrollBarVert);
	    
		// update the xmpp text pane as event panel selections change
		mSdEventPanel.addListSelectionListener(new ListSelectionListener() 
		{
			public void valueChanged(ListSelectionEvent e) 
			{
				String xmpp = (String)mPacketTableModel.getValueAt(
						((SequenceEventPanel)e.getSource()).getSelectedEvent(),
						PacketTableModel.TCPDATA);
				if (xmpp == null) xmpp = new String();
				mStanzaTextPane.setDocument(new XmppDocument(xmpp));
			}			
		});
		
		// update the tcp packet header panel as event panel selections change
		mSdEventPanel.addListSelectionListener(new ListSelectionListener() 
		{
			public void valueChanged(ListSelectionEvent e) 
			{
				int index = mSdEventPanel.getSelectedEvent();
				if (index >= 0 && index < mPacketTableModel.getRowCount())
				{
					mTimeValue.setText((String)mPacketTableModel.
							    getValueAt(index, PacketTableModel.TIME));
			
					String from = (String)mPacketTableModel.getValueAt(
                                index, PacketTableModel.SENDER_ALIAS);
					String fromAddress = (String)mPacketTableModel.getValueAt(
                                index, PacketTableModel.SENDER_ADDRESS);
					if (!from.equals(fromAddress))
					{
						from += " (" + fromAddress + ")";
					}
					mFromValue.setText(from);
					
					String to = (String)mPacketTableModel.getValueAt(
                                index, PacketTableModel.RECIPIENT_ALIAS);
					String toAddress = (String)mPacketTableModel.getValueAt(
                                index, PacketTableModel.RECIPIENT_ADDRESS);
					if (!to.equals(toAddress))
					{
						to += " (" + toAddress + ")";
					}
					mToValue.setText(to);
					
					mFlagsValue.setText((String)mPacketTableModel.getValueAt(
                            index, PacketTableModel.TCP_FLAGS));
					mSeqValue.setText(String.valueOf(
                                (Long)mPacketTableModel.getValueAt(
                                index, PacketTableModel.SEQ_NO)));
					mAckValue.setText(String.valueOf(
                                (Long)mPacketTableModel.getValueAt(
                                index, PacketTableModel.ACK_NO)));
					mLenValue.setText(String.valueOf(
                                (Integer)mPacketTableModel.getValueAt(
                                index, PacketTableModel.LENGTH)));
				}
			}
		});
		
		// if xmpp-only checkbox marked/unmarked, set new value on packet table
		mXmppOnly.addActionListener(new ActionListener()
		{
            @Override
            public void actionPerformed(ActionEvent e)
			{
		        Database db = Database.getInstance();
		        if (mXmppOnly.isSelected())
		        {
		            db.updateSetting(
		                    Database.SETTINGS_XMPP_ONLY, 
		                    Database.SETTINGS_TRUE);
		        }
		        else
		        {
                    db.updateSetting(
                            Database.SETTINGS_XMPP_ONLY, 
                            Database.SETTINGS_FALSE);		            
		        }
			}
		});
				
		// user wants to increase selected actor's precedence
		mUpButton.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent evt) 
			{
				final int index = mActorTableModel.moveUp(
                        mActorTable.getSelectedRow());
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        mActorTable.getSelectionModel().setSelectionInterval(
                                index, index);                      
                    }
                });
			}
		});
		
		// user wants to decrease the selected actor's precedence
		mDownButton.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent evt) 
			{
				final int index = mActorTableModel.moveDown(
                        mActorTable.getSelectedRow());
				SwingUtilities.invokeLater(new Runnable()
				{
				    public void run()
				    {
		                mActorTable.getSelectionModel().setSelectionInterval(
		                        index, index);				        
				    }
				});
			}
		});
		
		// user wants to set the selected actor to highest precedence
		mTopButton.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent evt) 
			{
				final int index = mActorTableModel.moveTop(
                        mActorTable.getSelectedRow());
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        mActorTable.getSelectionModel().setSelectionInterval(
                                index, index);                      
                    }
                });
			}
		});

		// user wants to set the selected actor to lowest precedence
		mBottomButton.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent evt) 
			{
				final int index = mActorTableModel.moveBottom(
                        mActorTable.getSelectedRow());
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        mActorTable.getSelectionModel().setSelectionInterval(
                                index, index);                      
                    }
                });
			}
		});

	    // handle regex/xpath search requests
		mSearchTextField.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent e) 
			{
                mSearchResultLabel.setText(" ");

                SequenceEventPanel.SearchType vartype = null;
                final String pattern = e.getActionCommand();
                if (pattern == null || pattern.length() == 0)
                {
                    return;
                }
                
                // identify the type of search we are doing
                switch (mSearchComboBox.getSelectedIndex())
                {
                case 0:
                    vartype = SequenceEventPanel.SearchType.XPATH;
                    break;
                case 1:
                    vartype = SequenceEventPanel.SearchType.REGEX;
                    break;
                default:
                }

                // do the actual search in background thread.
                final SequenceEventPanel.SearchType type = vartype;
			    new Thread() 
			    {
			        public void run()
			        {
			            // execute search
		                final SequenceEventPanel.SearchResult result = 
		                        mSdEventPanel.search(type, pattern);
		                
		                // update result indicator from swing thread
        				SwingUtilities.invokeLater(new Runnable()
        				{
        				    public void run()
        				    {
                				switch (result)
                				{
                				case FOUND:
                					mSearchResultLabel
                					        .setForeground(Pallette.FOUND);
                					mSearchResultLabel
                					        .setText("Match found.");
                					break;
                				case NOT_FOUND:
                					mSearchResultLabel
                					        .setForeground(Pallette.NOT_FOUND);
                					mSearchResultLabel
                					        .setText("Match not found.");
                					break;
                				case BAD_SYNTAX:
                					mSearchResultLabel
                					        .setForeground(Pallette.BAD_SYNTAX);
                					mSearchResultLabel
                					        .setText("Invalid syntax.");
                					break;
                				}                
                            }
                        });
                    }
                }.start();
			}
		});

        // handle new file menu option
        mNewFileMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                try
                {
                    Database db = Database.getInstance();
                    db.open(null);
                } 
                catch (SQLException e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }
                XmppTraceFrame.this.setTitle();
           }
        });     

        // handle open file menu option
        mOpenFileMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle(
                        "Open XMPP Trace Database File");
                chooser.setFileFilter(new XmppTraceFileFilter());
                if(chooser.showOpenDialog(XmppTraceFrame.this) == 
                        JFileChooser.APPROVE_OPTION) 
                {
                    try
                    {
                        Database db = Database.getInstance();
                        db.open(chooser.getSelectedFile().getAbsolutePath());
                    } 
                    catch (SQLException e)
                    {
                        JOptionPane.showMessageDialog(XmppTraceFrame.this, 
                                "File " + chooser.getSelectedFile().
                                getAbsolutePath() + " could not be opened.\n" +
                                "Is this file already open in another " +
                                "instance of XMPP Trace?");
                    }
                }
                XmppTraceFrame.this.setTitle();
           }
        });     

        // handle save-as file menu option
        mSaveAsFileMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Save As");
                chooser.setApproveButtonText("Save");
                chooser.setFileFilter(new XmppTraceFileFilter());
                if(chooser.showOpenDialog(XmppTraceFrame.this) == 
                        JFileChooser.APPROVE_OPTION) 
                {
                    File f = chooser.getSelectedFile();
                    if (f.exists())
                    {
                        int response = JOptionPane.showConfirmDialog(
                                XmppTraceFrame.this, "File already exists. Overwrite?",
                                "Warning", JOptionPane.OK_CANCEL_OPTION);
                        if (response == JOptionPane.CANCEL_OPTION)
                        {
                            return;
                        }
                    }
                    try
                    {
                        Database db = Database.getInstance();
                        db.saveAs(chooser.getSelectedFile().getAbsolutePath());
                    } 
                    catch (Exception e)
                    {
                        String msg = "File " + 
                                chooser.getSelectedFile().getAbsolutePath() + 
                                " could not be saved.";
                        JOptionPane.showMessageDialog(XmppTraceFrame.this, msg);
                    }
                }
                XmppTraceFrame.this.setTitle();
            }
        });     

		// handle import xmppdump menu option
		mImportFileMenuItem.addActionListener(new ActionListener() 
		{
			public void actionPerformed(ActionEvent evt) 
			{
			    JFileChooser chooser = new JFileChooser();
			    FileNameExtensionFilter filter = new FileNameExtensionFilter(
			            "xmppdump (.xml) or tcpdump (.pcap)", "xml", "pcap");
			    chooser.setFileFilter(filter);
                chooser.setDialogTitle("Import From Packet Trace File");
                int returnVal = chooser.showOpenDialog(XmppTraceFrame.this);
			    if(returnVal == JFileChooser.APPROVE_OPTION) 
			    {
			    	Database db = Database.getInstance();
			    	db.loadFromFile(chooser.getSelectedFile());
			    }
			}
		});		

		// handle import xmppdump menu option
        mExitFileMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                XmppTraceFrame.this.exit();
                dispose();
            }
        });  
        
        // handle reduce menu option
        mReduceFileMenuItem.addActionListener(new ActionListener() 
        {
            public void actionPerformed(ActionEvent evt) 
            {
                Database db = Database.getInstance();
                try
                {
                    db.reduce();
                } 
                catch (SQLException e)
                {
                    e.printStackTrace();
                }
            }
        });     
	}
	
	/**
	 * Instantiate the top level horizontal split pane, and children.
	 * @return The top level split pane.
	 */
	private JSplitPane createVertSplitPane()
	{
		JSplitPane sp = new JSplitPane();
		sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
		sp.setDividerLocation(450);
		sp.setDividerSize(3);
		sp.add(createTopPanel(), JSplitPane.TOP);
		sp.add(createBottomPanel(), JSplitPane.BOTTOM);
		return sp;
	}

	/**
	 * Instantiate the top panel of horizontal split pane, and children.
	 * @return The top level split pane.
	 */
	private JPanel createTopPanel() 
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.rowWeights = new double[] {0.1, 0.1, 0.1};
		gbl.rowHeights = new int[] {7, 7, 7};
		gbl.columnWeights = new double[] {0.1, 0.1, 0.1, 0.1};
		gbl.columnWidths = new int[] {7, 7, 7, 7};
		p.setLayout(gbl);
		p.add(createSearchComboBox(), new GridBagConstraints(
				0, 0, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createSearchTextField(), new GridBagConstraints(
				1, 0, 1, 1, 100.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(0, 4, 0, 0), 
				0, 0));
		p.add(createSearchResultLabel(), new GridBagConstraints(
				2, 0, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.NONE, 
				new Insets(0, 4, 0, 4), 
				0, 0));
		p.add(createSequenceEventPanel(), new GridBagConstraints(
				0, 1, 3, 1, 10.0, 10.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.BOTH, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createSdEventPanelScrollBarVert(), new GridBagConstraints(
				3, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.VERTICAL, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createSdEventPanelScrollBarHoriz(), new GridBagConstraints(
				0, 2, 3, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.setPreferredSize(new Dimension(1188, 449));
		return p;
	}
	
	/**
	 * Instantiate the bottom panel of horizontal split pane, and children.
	 * @return The top level split pane.
	 */
	private JPanel createBottomPanel()
	{
		JPanel p = new JPanel();
		GridLayout gl = new GridLayout(1, 3);
		gl.setColumns(3);
		gl.setHgap(5);
		gl.setVgap(5);
		p.setLayout(gl);
		p.add(createBottomLeftPanel());
		p.add(createXmppDocumentScrollPane());
		return p;
	}
	
	private JPanel createBottomLeftPanel()
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWeights = new double[] {0.1, 0.1, 0.1, 0.1};
		p.setLayout(gbl);
		p.setAutoscrolls(true);
		p.add(createXmppOnly(), new GridBagConstraints(
				0, 0, 1, 1, 1.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createTabbedPane(), new GridBagConstraints(
				0, 1, 1, 1, 1.0, 1.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.BOTH, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		return p;
	}
	
	/**
	 * Instantiate the lower left tabbed pane, and children.
	 * @return The lower left tabbed pane.
	 */
	private JTabbedPane createTabbedPane() 
	{
		JTabbedPane tp = new JTabbedPane();
		tp.addTab("Actor Addresses", null, createAddressPanel(), null);
		tp.addTab("Actor Display Order", null, createActorPanel(), null);
		tp.addTab("XPath Prefixes", null, 
                createXpathPrefixPanel(), null); 
		tp.addTab("TCP Headers", null, 
                createPacketHeaderPanel(), null);
		return tp;
	}

	/**
	 * Instantiate the address panel, and children.
	 * @return The address panel.
	 */
	private JPanel createAddressPanel()
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		p.setLayout(gbl);
		p.setAutoscrolls(true);
		p.add(createAdressTableScrollPane(), new GridBagConstraints(
				0, 0, 1, 1, 1.0, 1.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.BOTH, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		return p;
	}

	/**
	 * Instantiate the address panel, and children.
	 * @return The address panel.
	 */
	private JPanel createActorPanel()
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.columnWeights = new double[] {0.1, 0.1, 0.1, 0.1};
		p.setLayout(gbl);
		p.setAutoscrolls(true);
		p.add(createActorTableScrollPane(), new GridBagConstraints(
				0, 0, 4, 1, 1.0, 1.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.BOTH, 
				new Insets(5, 5, 5, 5), 
				0, 0));
		p.add(createTopButton(), new GridBagConstraints(
				0, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(5, 5, 5, 5), 
				0, 0));
		p.add(createUpButton(), new GridBagConstraints(
				1, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(5, 5, 5, 5), 
				0, 0));
		p.add(createDownButton(), new GridBagConstraints(
				2, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(5, 5, 5, 5), 
				0, 0));
		p.add(createBottomButton(), new GridBagConstraints(
				3, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.HORIZONTAL, 
				new Insets(5, 5, 5, 5), 
				0, 0));
		return p;
	}

	/**
	 * Instantiate the xpath prefix panel, and children.
	 * @return The xpath prefix panel.
	 */
	private JPanel createXpathPrefixPanel() 
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.rowWeights = new double[] {0.1};
		gbl.rowHeights = new int[] {7};
		gbl.columnWeights = new double[] {0.1};
		gbl.columnWidths = new int[] {7};
		p.setLayout(gbl);
		p.add(createXpathPrefixScrollPane(), new GridBagConstraints(
				0, 0, 1, 1, 0.0, 0.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.BOTH, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		return p;
	}
	
	/**
	 * Instantiate the packet header panel, and children.
	 * @return The packet header panel.
	 */
	private JPanel createPacketHeaderPanel() 
	{
		JPanel p = new JPanel();
		GridBagLayout gbl = new GridBagLayout();
		gbl.rowWeights = new double[] {0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1};
		gbl.rowHeights = new int[] {7, 7, 7, 7, 7, 7, 7};
		gbl.columnWeights = new double[] {0.1, 0.1};
		gbl.columnWidths = new int[] {7, 7};
		p.setLayout(gbl);
		p.add(createTimeLabel(), new GridBagConstraints(
				0, 0, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createTimeValue(), new GridBagConstraints(
				1, 0, 1, 1, 1.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createFromLabel(), new GridBagConstraints(
				0, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createFromValue(), new GridBagConstraints(
				1, 1, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createToValue(), new GridBagConstraints(
				1, 2, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createFlagsValue(), new GridBagConstraints(
				1, 3, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createSeqValue(), new GridBagConstraints(
				1, 4, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createAckValue(), new GridBagConstraints(
				1, 5, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createLenValue(), new GridBagConstraints(
				1, 6, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createToLabel(), new GridBagConstraints(
				0, 2, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createFlagsLabel(), new GridBagConstraints(
				0, 3, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createSeqLabel(), new GridBagConstraints(
				0, 4, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createAckLabel(), new GridBagConstraints(
				0, 5, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		p.add(createLenLabel(), new GridBagConstraints(
				0, 6, 1, 1, 0.0, 0.0, 
				GridBagConstraints.WEST, 
				GridBagConstraints.NONE, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		
		// spacer to compress labels and values to top of panel
		JPanel spacer = new JPanel();
		p.add(spacer, new GridBagConstraints(
				0, 7, 2, 1, 10.0, 10.0, 
				GridBagConstraints.CENTER, 
				GridBagConstraints.BOTH, 
				new Insets(0, 0, 0, 0), 
				0, 0));
		
		return p;
	}
	
	/**
	 * Instantiate the xmpp doc scroll pane, and children.
	 * @return The xmpp doc scroll pane.
	 */
	private JScrollPane createXmppDocumentScrollPane()
	{
		JScrollPane sp = new JScrollPane();
		sp.setPreferredSize(new java.awt.Dimension(4096, 4096));
		sp.setSize(4096, 4096);
		sp.setAutoscrolls(true);
		sp.setBorder(
				BorderFactory.createTitledBorder(
						null, 
						"Packet Payload", 
						TitledBorder.LEADING, 
						TitledBorder.DEFAULT_POSITION, 
						null, 
						null));
		sp.setViewportView(createXmppDocumentTextPane());
		return sp;
	}
	
	/**
	 * Instantiate the xmpp doc text pane, and children.
	 * @return The xmpp doc text pane.
	 */
	private XmppDocumentTextPane createXmppDocumentTextPane()
	{
		mStanzaTextPane = new XmppDocumentTextPane();
		mStanzaTextPane.setPreferredSize(new java.awt.Dimension(1200, 300));
		mStanzaTextPane.setDocument(new XmppDocument(new String()));
		mStanzaTextPane.setRequestFocusEnabled(false);
		mStanzaTextPane.setEditable(false);
		mStanzaTextPane.setFont(Pallette.FONT_EVENT_TEXT);
		return mStanzaTextPane;
	}
		
	/**
	 * Instantiate the xmpp-only checkbox.
	 * @return The xmpp-only checkbox.
	 */
	private JCheckBox createXmppOnly() 
	{
		mXmppOnly = new JCheckBox();
		mXmppOnly.setText("Show XMPP Data Packets Only");

		Database db = Database.getInstance();
		String value = db.fetchSetting(Database.SETTINGS_XMPP_ONLY);
		mXmppOnly.setSelected(
		        (value != null) && 
		        value.equals(Database.SETTINGS_TRUE));
	
		return mXmppOnly;
	}

	/**
	 * Instantiate the address table scroll pane.
	 * @return The address table scroll pane.
	 */
	private JScrollPane createAdressTableScrollPane() 
	{
		JScrollPane sp = new JScrollPane();
		mAddressTable = new JTable();
		mAddressTable.setModel(mAddressTableModel);
		mAddressTable.setSurrendersFocusOnKeystroke(true);
		mAddressTable.getSelectionModel().setSelectionMode(
					ListSelectionModel.SINGLE_SELECTION);
		mAddressTable.setRowSelectionAllowed(true);
		mAddressTable.getColumn("Visible").setMaxWidth(50);
		sp.setViewportView(mAddressTable);
		return sp;
	}

	/**
	 * Instantiate the actor table scroll pane.
	 * @return The actor table scroll pane.
	 */
	private JScrollPane createActorTableScrollPane() 
	{
		JScrollPane sp = new JScrollPane();
		mActorTable = new JTable();
		mActorTable.setModel(mActorTableModel);
		mActorTable.setSurrendersFocusOnKeystroke(true);
		mActorTable.getSelectionModel().setSelectionMode(
					ListSelectionModel.SINGLE_SELECTION);
		mActorTable.setRowSelectionAllowed(true);
		sp.setViewportView(mActorTable);
		return sp;
	}
	
	/**
	 * Instantiate the sequence event panel.
	 * @return The sequence event panel.
	 */
	private SequenceEventPanel createSequenceEventPanel() 
	{
		mSdEventPanel = new SequenceEventPanel(
                mAddressTableModel, mPacketTableModel, mActorTableModel);
		mSdEventPanel.setPreferredSize(new java.awt.Dimension(1200, 611));
		return mSdEventPanel;
	}
	
	private JLabel createTimeLabel() 
	{
		JLabel l = new JLabel();
		l.setText("Time:");
		return l;
	}
	
	private JLabel createTimeValue() 
	{
		mTimeValue = new JLabel();
		return mTimeValue;
	}
	
	private JLabel createFromLabel() 
	{
		JLabel l = new JLabel();
		l.setText("From:");
		return l;
	}
	
	private JLabel createFromValue() 
	{
		mFromValue = new JLabel();
		return mFromValue;
	}

	private JLabel createToLabel() 
	{
		JLabel l = new JLabel();
		l.setText("To:");
		return l;
	}
	
	private JLabel createToValue() 
	{
		mToValue = new JLabel();
		return mToValue;
	}
	
	private JLabel createFlagsLabel() 
	{
		JLabel l = new JLabel();
		l.setText("Flags:");
		return l;
	}	
	
	private JLabel createFlagsValue() 
	{
		mFlagsValue = new JLabel();
		return mFlagsValue;
	}

	private JLabel createSeqLabel() 
	{
		JLabel l = new JLabel();
		l.setText("Seq:");
		return l;
	}
	
	private JLabel createSeqValue() 
	{
		mSeqValue = new JLabel();
		return mSeqValue;
	}
	
	private JLabel createAckLabel() 
	{
		JLabel l = new JLabel();
		l.setText("Ack:");
		return l;
	}

	private JLabel createAckValue() 
	{
		mAckValue = new JLabel();
		return mAckValue;
	}
	
	private JLabel createLenLabel() 
	{
		JLabel l = new JLabel();
		l.setText("Data Len:");
		return l;
	}
	
	private JLabel createLenValue() 
	{
		mLenValue = new JLabel();
		return mLenValue;
	}
		
	private JComboBox createSearchComboBox() 
	{
		ComboBoxModel mSearchModel = 
				new DefaultComboBoxModel(new String[] 
				{ "XPath Search:", "Regex Search:" });
		mSearchComboBox = new JComboBox();
		mSearchComboBox.setModel(mSearchModel);
		mSearchComboBox.setDoubleBuffered(true);
		return mSearchComboBox;
	}
	
	@SuppressWarnings("serial")
	private JTextField createSearchTextField() 
	{
		// want text field to be same height as combo box.  just coz.
		mSearchTextField = new JTextField() 
		{
			public Dimension getMinimumSize()
			{
				Dimension d = super.getMinimumSize();
				d.setSize(d.getWidth(), mSearchComboBox.getHeight());
				return d;
			}
		};
		return mSearchTextField;
	}
	
	private JScrollBar createSdEventPanelScrollBarVert() 
	{
		mSdEventPanelScrollBarVert = new JScrollBar();
		mSdEventPanelScrollBarVert.setOrientation(SwingConstants.VERTICAL);
		return mSdEventPanelScrollBarVert;
	}
	
	private JScrollBar createSdEventPanelScrollBarHoriz() 
	{
		mSdEventPanelScrollBarHoriz = new JScrollBar();
		mSdEventPanelScrollBarHoriz.setOrientation(SwingConstants.HORIZONTAL);
		return mSdEventPanelScrollBarHoriz;
	}

	private JScrollPane createXpathPrefixScrollPane() 
	{
		JScrollPane sp = new JScrollPane();
		sp.setViewportView(createXpathPrefixTable());
		return sp;
	}
	
	private JTable createXpathPrefixTable() 
	{
		TableModel tm = new XpathPrefixTableModel();
		JTable t = new JTable();
		t.setModel(tm);
		t.setAutoCreateRowSorter(true);
		t.getSelectionModel().setSelectionMode(
				ListSelectionModel.SINGLE_SELECTION);
		return t;
	}
	
	@SuppressWarnings("serial")
	private JLabel createSearchResultLabel() 
	{
		mSearchResultLabel = new JLabel()
		{
			public Dimension getMinimumSize() 
			{
				Dimension d = super.getMinimumSize();
				return new Dimension(120, (int)d.getHeight());
			}
			
		};
		mSearchResultLabel.setFont(Pallette.FONT_EVENT_TEXT);
		return mSearchResultLabel;
	}
	
	private JButton createUpButton() 
	{
		mUpButton = new JButton();
		mUpButton.setText("Move Up");
		return mUpButton;
	}
	
	private JButton createDownButton() 
	{
		mDownButton = new JButton();
		mDownButton.setText("Move Down");
		return mDownButton;
	}
	
	private JButton createTopButton() 
	{
		mTopButton = new JButton();
		mTopButton.setText("Move to Top");
		return mTopButton;
	}
	
	private JButton createBottomButton() 
	{
		mBottomButton = new JButton();
		mBottomButton.setText("Move to Bottom");
		return mBottomButton;
	}

	private JMenuBar createMenuBar()
	{
		JMenuBar mb = new JMenuBar();
		mb.add(createFileMenu());
		return mb;
	}
	
	private JMenu createFileMenu()
	{
		JMenu m = new JMenu();
		m.setText("File");
        m.add(createNewFileMenuItem());
		m.add(createOpenFileMenuItem());
        m.add(createSaveAsFileMenuItem());
        m.add(createImportFileMenuItem());
        m.addSeparator();
        m.add(createReduceFileMenuItem());
        m.addSeparator();
        m.add(createExitFileMenuItem());
		return m;
	}

    private JMenuItem createNewFileMenuItem()
	{
		JMenuItem mi = new JMenuItem();
		mi.setText("New");
		mNewFileMenuItem = mi;
		return mi;
	}

    private JMenuItem createOpenFileMenuItem()
    {
        JMenuItem mi = new JMenuItem();
        mi.setText("Open...");
        mOpenFileMenuItem = mi;
        return mi;
    }

    private JMenuItem createSaveAsFileMenuItem()
    {
        JMenuItem mi = new JMenuItem();
        mi.setText("Save As...");
        mSaveAsFileMenuItem = mi;
        return mi;
    }

	private JMenuItem createImportFileMenuItem()
	{
	    JMenuItem mi = new JMenuItem();
	    mi.setText("Import...");
	    mImportFileMenuItem = mi;
	    return mi;
	}
	
    private JMenuItem createExitFileMenuItem()
    {
        JMenuItem mi = new JMenuItem();
        mi.setText("Exit");
        mExitFileMenuItem = mi;
        return mi;
    }

    private JMenuItem createReduceFileMenuItem()
    {
        JMenuItem mi = new JMenuItem();
        mi.setText("Reduce");
        mReduceFileMenuItem = mi;
        return mi;
    }
	
	/**
	 * Useful for browsing Swing display settings.  Pops up a child window
	 * with a table containing default UI settings and (more importantly)
	 * the type of objects which need to be assigned to these settings when
	 * overriding them.
	 */
//	private void displayUiManagerDefaults()
//	{	
//	    UIDefaults defaults = UIManager.getDefaults();
//	    String[ ] colName = {"Key", "Value"};
//	    String[ ][ ] rowData = new String[ defaults.size() ][ 2 ];
//	    int i = 0;
//	    for(Enumeration<Object> e = defaults.keys(); e.hasMoreElements(); i++){
//	        Object key = e.nextElement();
//	        rowData[ i ] [ 0 ] = key.toString();
//	        rowData[ i ] [ 1 ] = ""+defaults.get(key);
//	    }
//	    JFrame f = new JFrame("UIManager properties default values");
//	    JTable t = new JTable(rowData, colName);
//	    t.setAutoCreateRowSorter(true);
//	    f.setContentPane(new JScrollPane(t));
//	    //f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//	    f.pack();
//	    f.setVisible(true);
//	}
}
