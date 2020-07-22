package wtf.tks;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

class ExtHyperLinkListener implements HyperlinkListener {
	
	JEditorPane editorPane;
	
	public ExtHyperLinkListener(JEditorPane editorPane) {
		this.editorPane = editorPane;
	}
	
	public void hyperlinkUpdate(HyperlinkEvent event) {
		if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
			try {
				Desktop.getDesktop().browse(new URI(event.getURL().toString()));
			} catch (IOException | URISyntaxException e) {
				System.err.printf("Error while opening hyperlink '%s'.%n", event.getURL().toString());
				e.printStackTrace();
			}
		}
	}
}