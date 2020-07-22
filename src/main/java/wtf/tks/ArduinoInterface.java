/*
 * Copyright 2017 Andy Heil (Tekks).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wtf.tks;

import com.fazecast.jSerialComm.SerialPort;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * @author Andy Heil (Tekks)
 */
public class ArduinoInterface {
	
	private static final int PORT = 1258;
	private static final Image ARDUINO_ICON = loadImage("/img/arduino.png");
	
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
	
	static SerialPort chosenPort;
	static JButton connectButton;
	static JComboBox<String> portList = new JComboBox<>();
	static String portListselect;
	static TrayIcon trayIcon;
	static JFrame window;
	
	public static void main(String[] args) {
		checkIfRunning();
		
		try {
			UIManager.setLookAndFeel(new MetalLookAndFeel());
		} catch (UnsupportedLookAndFeelException e) {
			System.err.println("Error while setting UI look and feel:");
			e.printStackTrace();
		}
		UIManager.put("swing.boldMetal", Boolean.FALSE);
		
		SwingUtilities.invokeLater(ArduinoInterface::createAndShowGUI);
	}
	
	@SuppressWarnings("resource")
	private static void checkIfRunning() {
		try {
			var port = new ServerSocket(PORT, 0, InetAddress.getByAddress(new byte[]{ 127, 0, 0, 1 }));
			port.close();
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Another Instance is Running", "Warning", JOptionPane.WARNING_MESSAGE);
			System.exit(1);
		}
	}
	
	private static void selectComPort() {
		if (window != null) return;
		
		window = new JFrame();
		window.setIconImage(ARDUINO_ICON);
		window.setTitle("Arduino Interface | ComPort");
		window.setSize(450, 100);
		window.setLayout(new BorderLayout());
		window.setLocationRelativeTo(null);
		portList.removeAllItems();
		
		SerialPort[] portNames = SerialPort.getCommPorts();
		Arrays.stream(portNames).forEachOrdered(
				portName -> portList.addItem(portName.getDescriptivePortName()));
		
		if (chosenPort != null) {
			connectButton = new JButton("Deselect");
			portList.setSelectedItem(portListselect);
			portList.setEnabled(false);
		} else {
			connectButton = new JButton("Select");
		}
		
		JPanel topPanel = new JPanel();
		topPanel.add(portList);
		topPanel.add(connectButton);
		window.add(topPanel, BorderLayout.NORTH);
		
		SystemInfo si = new SystemInfo();
		CentralProcessor processor = si.getHardware().getProcessor();
		
		connectButton.addActionListener(event -> {
			if (connectButton.getText().equals("Select")) {
				String comPort = portList.getSelectedItem().toString();
				trayIcon.setToolTip("Connected: " + comPort);
				portListselect = comPort;
				comPort = comPort.substring(comPort.indexOf("(") + 1);
				comPort = comPort.substring(0, comPort.indexOf(")"));
				chosenPort = SerialPort.getCommPort(comPort);
				chosenPort.setComPortTimeouts(SerialPort.TIMEOUT_SCANNER, 0, 0);
				
				if (chosenPort.openPort()) {
					connectButton.setText("Deselect");
					portList.setEnabled(false);
					
					Thread thread = new Thread(() -> {
						sleep(1000);
						
						PrintWriter output = new PrintWriter(chosenPort.getOutputStream());
						while (chosenPort != null) {
							String timestamp = LocalTime.now().format(TIME_FORMATTER);
							String cpu = Double.toString((int) (processor.getSystemCpuLoad() * 100 * 100) / 100.00);
							
							long availMem = si.getHardware().getMemory().getAvailable();
							long totalMem = si.getHardware().getMemory().getTotal();
							String memory = (totalMem - availMem) * 100 / (int) Math.pow(1024, 3) / 100.00
									+ " / " + totalMem * 100 / (int) Math.pow(1024, 3) / 100.00;
							
							int threads = si.getOperatingSystem().getProcessCount();
							
							String infoString = "4;" + "TIME:;" + timestamp + ';'
									+ "CPU:;" + cpu + "%;"
									+ "MEM:;" + memory + ';'
									+ "Proc:;" + threads;
							System.out.println(infoString);
							output.print(infoString);
							output.flush();
							
							sleep(1000);
						}
						output.close();
					});
					thread.setPriority(Thread.MAX_PRIORITY);
					thread.start();
				}
				
			} else {
				trayIcon.setToolTip("Disconnected");
				chosenPort.closePort();
				chosenPort = null;
				portList.setEnabled(true);
				connectButton.setText("Select");
			}
		});
		
		window.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				window.setVisible(false);
				window.dispose();
				window = null;
			}
		});
		window.setVisible(true);
	}
	
	private static void createAndShowGUI() {
		if (!SystemTray.isSupported()) {
			throw new IllegalStateException("SystemTray is not supported");
		}
		
		final PopupMenu popup = new PopupMenu();
		trayIcon = new TrayIcon(ARDUINO_ICON);
		final SystemTray tray = SystemTray.getSystemTray();
		
		MenuItem aboutItem = new MenuItem("About");
		MenuItem selectItem = new MenuItem("Select ComPort");
		MenuItem exitItem = new MenuItem("Exit");
		
		popup.add(aboutItem);
		popup.addSeparator();
		popup.add(selectItem);
		popup.addSeparator();
		popup.add(exitItem);
		
		trayIcon.setPopupMenu(popup);
		trayIcon.setToolTip("Disconnected");
		
		try {
			tray.add(trayIcon);
		} catch (AWTException e) {
			throw new IllegalStateException("TrayIcon could not be added.");
		}
		
		trayIcon.addActionListener(event -> selectComPort());
		
		aboutItem.addActionListener(event -> {
			JLabel label = new JLabel();
			Font font = label.getFont();
			
			String style = "font-family:" + font.getFamily() + ";"
					+ "font-weight:" + (font.isBold() ? "bold" : "normal") + ";"
					+ "font-size:" + font.getSize() + "pt;";
			JEditorPane ep = new JEditorPane("text/html", "<html><body style=\"" + style + "\">"
					+ "Developed by Tekks<br>" + "Licensed under the Apache License, Version 2.0<br>"
					+ "visit <a href=\"https://tks.wtf\">tks.wtf</a>  for more Information"
					+ "</body></html>");
			
			HyperlinkListener hyperlinkListener = new ExtHyperLinkListener(ep);
			ep.addHyperlinkListener(hyperlinkListener);
			ep.setEditable(false);
			ep.setBackground(label.getBackground());
			
			JOptionPane.showMessageDialog(null, ep, "Arduino Interface | About", JOptionPane.INFORMATION_MESSAGE, null);
		});
		
		selectItem.addActionListener(event -> selectComPort());
		
		exitItem.addActionListener(event -> {
			tray.remove(trayIcon);
			System.exit(0);
		});
	}
	
	private static Image loadImage(String path) {
		try {
			return ImageIO.read(ArduinoInterface.class.getResource(path));
		} catch (IOException e) {
			System.err.printf("Couldn't load image from '%s', providing fallback.%n", path);
			return new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
		}
	}
	
	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			if (Thread.interrupted()) {
				throw new IllegalStateException(e);
			}
		}
	}
}
