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
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
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
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpringLayout;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

public class IECUI implements Runnable {
	
	private static final String acOpen = "open";
	private static final String acSave = "save";
	private static final String acQuit = "quit";

	private static final String acClearSelection = "clear";
	private static final String acInvertSelection = "invert";
	private static final String acSelectAll = "selectall";
	private static final String acKeepSelected = "keepselected";
	private static final String acRemoveSelected = "removeselected";
	private static final String acToggleSelected = "toggleselected";
	
	private static final String keyFileLabel = "file-label";
	private static final String keyOpenDialog = "open-dialog";
	private static final String keySaveDialog = "save-dialog";
	private static final String keyTable = "table";
	private static final String keyPopup = "popup";

	private static final String keyDoc = "document";
	
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
		
		public void dataKeepChange() {
			for(TableModelListener l : listener) {
				l.tableChanged(new TableModelEvent(this, 0, items.size()-1, colKeep));
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
			jfc.setFileFilter(svgFileFilter);
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
	
	private void saveSVGDialog() {
		JFileChooser jfc = getGlobal(keySaveDialog, JFileChooser.class);
		
		if(jfc == null) {
			JFileChooser lchooser = getGlobal(keyOpenDialog, JFileChooser.class);
			jfc = new JFileChooser(lchooser == null ? new File(".") : lchooser.getCurrentDirectory());
			jfc.setAcceptAllFileFilterUsed(true);
			jfc.addChoosableFileFilter(svgFileFilter);
			jfc.setFileFilter(svgFileFilter);
			putGlobal(keySaveDialog, jfc);
		}
		
		while(jfc.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
			File f = jfc.getSelectedFile();
			
			if(f.exists()) {
				int r  = JOptionPane.showConfirmDialog(frame, String.format("File '%s' exists. Overwrite ?", f.getName()), "Overwrite ?", JOptionPane.YES_NO_CANCEL_OPTION);
				
				if(r == JOptionPane.NO_OPTION)
					continue;
				
				if(r != JOptionPane.YES_OPTION)
					break;
			}
			
			saveSVGFile(f);
		}
	}

	private boolean openSVG(File f) {

		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			dbf.setValidating(false);
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document d = db.parse(f);
			
			putGlobal(keyDoc, d);
			
			exportItemTableModel.getItems().clear();
			IEC.listExports(System.out, d.getDocumentElement(), IEC.defaultExportItemFormat, null, null, exportItemTableModel.getItems());
			exportItemTableModel.updateCells();

			JLabel fileLabel = getGlobal(keyFileLabel, JLabel.class);
			fileLabel.setText(f.getName());
			fileLabel.setToolTipText(f.getPath());

			return true;
		} catch(Exception e) {
			JOptionPane.showMessageDialog(frame, String.format("There was an error while reading '%s'!\n"+e.getClass().getName() + ": " + e.getMessage(), f.getPath()), "Error!", JOptionPane.ERROR_MESSAGE);
		}

		return false;
	}
	
	private boolean saveSVGFile(File f) {
		return false;
	}

	private AbstractAction menuAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;

		@Override
		public void actionPerformed(ActionEvent e) {
			do {
				if(acQuit.equals(e.getActionCommand())) {
					frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
					break;
				}
				
				if(acOpen.equals(e.getActionCommand())) {
					openSVGDialog();
					break;
				}
				
				if(acSave.equals(e.getActionCommand())) {
					if(getGlobal(keyDoc, Document.class) != null) {
						saveSVGDialog();
					} else {
						JOptionPane.showMessageDialog(frame, "Nothing is currently loaded...", "Nothing to Save!", JOptionPane.INFORMATION_MESSAGE);
					}
					break;
				}
			} while(false);
		}
	};
	
	private AbstractAction markSelectedKeepAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			int [] selrows = tab.getSelectedRows();
			for(int i=0; i<selrows.length; i++) {
				int mrow = tab.convertRowIndexToModel(selrows[i]);
				exportItemTableModel.keepMap.put(mrow, Boolean.TRUE);
			}
			exportItemTableModel.dataKeepChange();
		}
	};

	private AbstractAction markSelectedRemoveAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			int [] selrows = tab.getSelectedRows();
			for(int i=0; i<selrows.length; i++) {
				int mrow = tab.convertRowIndexToModel(selrows[i]);
				exportItemTableModel.keepMap.put(mrow, Boolean.FALSE);
			}
			exportItemTableModel.dataKeepChange();
		}
	};
	
	private AbstractAction clearSelectionAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			ListSelectionModel selm = tab.getSelectionModel();
			selm.clearSelection();
		}
	};
	
	private AbstractAction selectAllAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			ListSelectionModel selm = tab.getSelectionModel();
			selm.setSelectionInterval(0, exportItemTableModel.getRowCount()-1);
		}
	};
	
	private AbstractAction invertSelectionAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			ListSelectionModel selm = tab.getSelectionModel();
			selm.setValueIsAdjusting(true);
			for(int i=0; i<exportItemTableModel.getRowCount(); i++) {
				if(selm.isSelectedIndex(i)) {
					selm.removeSelectionInterval(i, i);
				} else {
					selm.addSelectionInterval(i, i);
				}
			}
			selm.setValueIsAdjusting(false);
		}
	};
	
	private AbstractAction toggleKeepSelectedAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			JTable tab = getGlobal(keyTable, JTable.class);
			int [] selrows = tab.getSelectedRows();
			for(int i=0; i<selrows.length; i++) {
				int mrow = tab.convertRowIndexToModel(selrows[i]);
				exportItemTableModel.keepMap.put(mrow, def(exportItemTableModel.keepMap.get(mrow), Boolean.FALSE) ? Boolean.FALSE : Boolean.TRUE);
			}
			exportItemTableModel.dataKeepChange();
		}
	};
	
	private AbstractAction tablePopupAction = new AbstractAction() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public void actionPerformed(ActionEvent e) {
			do {
				if(acSelectAll.equals(e.getActionCommand())) {
					selectAllAction.actionPerformed(e);
					break;
				}
				if(acClearSelection.equals(e.getActionCommand())) {
					clearSelectionAction.actionPerformed(e);
					break;
				}
				if(acInvertSelection.equals(e.getActionCommand())) {
					invertSelectionAction.actionPerformed(e);
					break;
				}
				if(acToggleSelected.equals(e.getActionCommand())) {
					toggleKeepSelectedAction.actionPerformed(e);
					break;
				}
				if(acKeepSelected.equals(e.getActionCommand()) || acRemoveSelected.equals(e.getActionCommand())) {
					if(acKeepSelected.equals(e.getActionCommand())) {
						markSelectedKeepAction.actionPerformed(e);
					} else {
						markSelectedRemoveAction.actionPerformed(e);
					}
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
		actionMenu.add(withKeyStroke(setACAndText(new JMenuItem(menuAction), acQuit, "Quit", 'Q'), KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK)));
		
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
		
		putGlobal(keyTable, tab);
		
		JScrollPane tableScroll = new JScrollPane(tab);

		uipanel.add(tableScroll);
		
		JPopupMenu tablePopup = new JPopupMenu();
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acSelectAll, "Select All", 'a'));
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acInvertSelection, "Invert Selection", 'I'));
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acClearSelection, "Select None", 'N'));
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acKeepSelected, "Keep Selected", 'K'));
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acRemoveSelected, "Remove Selected", 'R'));
		tablePopup.add(setACAndText(new JMenuItem(tablePopupAction), acToggleSelected, "Toggle Selected", 'T'));

		// select all is a standard action
		
		tab.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK), acInvertSelection);
		tab.getActionMap().put(acInvertSelection, invertSelectionAction);

		tab.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), acClearSelection);
		tab.getActionMap().put(acClearSelection, clearSelectionAction);
		
		tab.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK), acKeepSelected);
		tab.getActionMap().put(acKeepSelected, markSelectedKeepAction);

		tab.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK), acRemoveSelected);
		tab.getActionMap().put(acRemoveSelected, markSelectedRemoveAction);

		tab.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK), acToggleSelected);
		tab.getActionMap().put(acToggleSelected, toggleKeepSelectedAction);
		
		putGlobal(keyPopup, tablePopup);
		
		tab.addMouseListener(new MouseAdapter() {
			
			private void showPopup(MouseEvent e) {
				JPopupMenu pop = getGlobal(keyPopup, JPopupMenu.class);
				Point mshow = e.getComponent().getMousePosition();
				
				pop.show(e.getComponent(), mshow.x, mshow.y);
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				if(e.isPopupTrigger()) {
					showPopup(e);
				}
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				if(e.isPopupTrigger()) {
					showPopup(e);
				}
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				if(e.isPopupTrigger()) {
					showPopup(e);
				}
			}
		});
		
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
