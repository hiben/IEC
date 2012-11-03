/*
IEC - Copyright (c) 2012 Hendrik Iben - hendrik [dot] iben <at> googlemail [dot] com
Inkscape Export Cleaner - get's rid of export definitions in Inkscape documents

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package iec;

import iec.IEC.ExportItem;


import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GraphicsDevice;
import java.awt.MouseInfo;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SpringLayout;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class IECUI implements Runnable {
	
	private static final String acOpen = "open";
	private static final String acSave = "save";
	private static final String acExit = "exit";

	private static final String keyFileLabel = "file-label";
	private static final String keyOpenDialog = "open-dialog";
	
	private String [] args;
	private JFrame frame;
	
	private Map<String, Object> globalObjects = new TreeMap<String, Object>();
	
	private <A> A getGlobal(String key, Class<A> clazz) {
		Object o = globalObjects.get(key);
		if(o!=null && clazz.isInstance(o)) {
			return clazz.cast(o);
		}
		return null;
	}
	
	private void putGlobal(String key, Object o) {
		globalObjects.put(key, o);
	}
	
	
	private static <A> A def(A a, A def) {
		if(a==null)
			return def;
		return a;
	}
	
	private static final int colID = 0;
	private static final int colFilename = 1;
	private static final int colDPIX = 2;
	private static final int colDPIY = 3;
	private static final int colTag = 4;
	private static final int colKeep = 5;

	private class ExportItemTableModel implements TableModel {
		
		private List<ExportItem> items = new ArrayList<ExportItem>();
		private Map<Integer, Boolean> keepMap = new TreeMap<Integer, Boolean>();
		
		private List<TableModelListener> listener = new LinkedList<TableModelListener>();
		
		public List<ExportItem> getItems() {
			return items;
		}
		
		public void updateCells() {
			keepMap.clear();
			for(TableModelListener l : listener) {
				l.tableChanged(new TableModelEvent(this));
			}
		}
		
		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			if(columnIndex==colKeep && aValue instanceof Boolean) {
				keepMap.put(rowIndex, (Boolean)aValue);
			}
		}
		
		@Override
		public void removeTableModelListener(TableModelListener l) {
			listener.remove(l);
		}
		
		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return columnIndex == colKeep;
		}
		
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if(rowIndex<0 ||rowIndex > items.size())
				return null;
			
			ExportItem ei = items.get(rowIndex);
			
			switch(columnIndex) {
			case colID:
				return def(ei.id, "<no-id>");
			case colFilename:
				return def(ei.filename, "<no-filename>");
			case colDPIX:
				return def(ei.xdpi, "<no-x-dpi>");
			case colDPIY:
				return def(ei.ydpi, "<no-y-dpi>");
			case colTag:
				return def(ei.tag, "<no-tag>");
			case colKeep:
				return def(keepMap.get(rowIndex), Boolean.FALSE);
			}
			
			return null;
		}
		
		@Override
		public int getRowCount() {
			return items.size();
		}
		
		@Override
		public String getColumnName(int columnIndex) {
			switch(columnIndex) {
			case colID:
				return "Id";
			case colFilename:
				return "Filename";
			case colDPIX:
				return "DPI (x)";
			case colDPIY:
				return "DPI (y)";
			case colTag:
				return "Tag";
			case colKeep:
				return "Keep";
			}
			return null;
		}
		
		@Override
		public int getColumnCount() {
			return 6;
		}
		
		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if(columnIndex==colKeep)
				return Boolean.class;
			
			return String.class;
		}
		
		@Override
		public void addTableModelListener(TableModelListener l) {
			listener.add(l);
		}
	};
	
	private ExportItemTableModel exportItemTableModel = new ExportItemTableModel();
	
	private class ExportItemTableCellRenderer implements TableCellRenderer {
		
		private TableCellRenderer defRender;
		
		public ExportItemTableCellRenderer(JTable tab) {
			defRender = tab.getDefaultRenderer(String.class);
		}
		
		@Override
		public Component getTableCellRendererComponent(JTable table,
				Object value, boolean isSelected, boolean hasFocus, int row,
				int column) {
			Component c = defRender.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			
			if(c instanceof JComponent) {
				JComponent jc = (JComponent)c;
				
				int mcol = table.convertColumnIndexToModel(column);
				int mrow = table.convertRowIndexToModel(row);
				
				if(mcol == colFilename) {
					Object v = exportItemTableModel.getValueAt(mrow, mcol);
					
					if(v instanceof String) {
						jc.setToolTipText((String)v);
					} else {
						jc.setToolTipText(null);
					}
				} else {
					jc.setToolTipText(null);
				}				
			}
			
			return c;
		}
		
		
	}
	
	public IECUI(String [] args) {
		this.args = args;
	}
	
	private WindowAdapter closeAction = new WindowAdapter() {
		@Override
		public void windowClosing(WindowEvent e) {
			frame.dispose();
		}
	};
	
	private static String getExt(String filename) {
		int idx = filename.lastIndexOf('.');
		
		if(idx == -1)
			return null;
		
		if(idx == filename.length()-1)
			return "";
		
		return filename.substring(idx+1).toLowerCase();
	}
	
	private FileFilter svgFileFilter = new FileFilter() {
		
		@Override
		public String getDescription() {
			return "SVG Files";
		}
		
		@Override
		public boolean accept(File f) {
			if(f.isDirectory())
				return true;
			
			return "svg".equals(getExt(f.getName()));
		}
	};
	
	private void openSVGDialog() {
		JFileChooser jfc = getGlobal(keyOpenDialog, JFileChooser.class);
		
		if(jfc == null) {
			jfc = new JFileChooser(new File("."));
			jfc.setAcceptAllFileFilterUsed(true);
			jfc.addChoosableFileFilter(svgFileFilter);
			putGlobal(keyOpenDialog, jfc);
		}
		
		if(jfc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			File f = jfc.getSelectedFile();
			
			if(!f.canRead()) {
				JOptionPane.showMessageDialog(frame, "Unable to read selected file...", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}

			openSVG(f);
		}
	}

	private boolean openSVG(File f) {

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setValidating(false);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document d = db.parse(f);
			
			exportItemTableModel.getItems().clear();
			IEC.listExports(System.out, d.getDocumentElement(), IEC.defaultExportItemFormat, null, null, exportItemTableModel.getItems());
			exportItemTableModel.updateCells();

			JLabel fileLabel = getGlobal(keyFileLabel, JLabel.class);
			fileLabel.setText(f.getName());
			fileLabel.setToolTipText(f.getPath());

			return true;
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(frame, String.format("There was an error while reading '%s'!\n"+e.getClass().getName() + ": " + e.getMessage(), f.getPath()), "IO-Error!", JOptionPane.ERROR_MESSAGE);
		}

		return false;
	}

	private AbstractAction menuAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e) {
			do {
				if(acExit.equals(e.getActionCommand())) {
					frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
					break;
				}
				
				if(acOpen.equals(e.getActionCommand())) {
					openSVGDialog();
					break;
				}
				
				if(acSave.equals(e.getActionCommand())) {
					break;
				}
			} while(false);
		}
	};

	public static JMenuItem setACAndText(JMenuItem jmi, String actionCommand, String text, Character mnemonic) {
		jmi.setActionCommand(actionCommand);
		jmi.setText(text);
		if(mnemonic!=null)
			jmi.setMnemonic(mnemonic);
		return jmi;
	}

	public static JMenuItem withKeyStroke(JMenuItem jmi, KeyStroke ks) {
		jmi.setAccelerator(ks);
		return jmi;
	}
	
	@Override
	public void run() {
		GraphicsDevice gd = MouseInfo.getPointerInfo().getDevice();
		frame = new JFrame("IEC", gd.getDefaultConfiguration());
		frame.setLocationByPlatform(true);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(closeAction);
		
		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);
		
		JMenu actionMenu = new JMenu("Action");
		
		actionMenu.add(withKeyStroke(setACAndText(new JMenuItem(menuAction), acOpen, "Open SVG", 'o'), KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK)));
		actionMenu.add(withKeyStroke(setACAndText(new JMenuItem(menuAction), acSave, "Save SVG", 's'), KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK)));
		actionMenu.addSeparator();
		actionMenu.add(withKeyStroke(setACAndText(new JMenuItem(menuAction), acExit, "Exit", 'x'), KeyStroke.getKeyStroke(KeyEvent.VK_X, KeyEvent.CTRL_DOWN_MASK)));
		
		menuBar.add(actionMenu);
		
		JPanel uipanel = new JPanel();
		frame.add(uipanel);

		SpringLayout sl = new SpringLayout();
		uipanel.setLayout(sl);
		
		JLabel lFileLabel = new JLabel("<no file open>");
		putGlobal(keyFileLabel, lFileLabel);
		
		uipanel.add(lFileLabel);
		
		JTable tab = new JTable(exportItemTableModel);
		tab.setDefaultRenderer(String.class, new ExportItemTableCellRenderer(tab));
		
		tab.setAutoCreateColumnsFromModel(true);
		tab.setFillsViewportHeight(true);
		tab.setAutoCreateRowSorter(true);
		
		JScrollPane tableScroll = new JScrollPane(tab);

		uipanel.add(tableScroll);
		
		sl.putConstraint(SpringLayout.WEST, lFileLabel, 5, SpringLayout.WEST, uipanel);
		sl.putConstraint(SpringLayout.NORTH, lFileLabel, 5, SpringLayout.NORTH, uipanel);

		sl.putConstraint(SpringLayout.WEST, tableScroll, 5, SpringLayout.WEST, uipanel);
		sl.putConstraint(SpringLayout.NORTH, tableScroll, 5, SpringLayout.SOUTH, lFileLabel);

		sl.putConstraint(SpringLayout.EAST, uipanel, 5, SpringLayout.EAST, tableScroll);
		sl.putConstraint(SpringLayout.SOUTH, uipanel, 5, SpringLayout.SOUTH, tableScroll);
		
		frame.pack();
		frame.setVisible(true);
		
		if(args.length>0) {
			openSVG(new File(args[0]));
		}
	}
	
	public static void main(String [] args) {
		EventQueue.invokeLater(new IECUI(args));
	}
}
