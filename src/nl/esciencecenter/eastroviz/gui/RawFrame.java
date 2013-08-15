package nl.esciencecenter.eastroviz.gui;

import javax.swing.JFrame;

import nl.esciencecenter.eastroviz.Viz;
import nl.esciencecenter.eastroviz.dataformats.raw.RawData;

@SuppressWarnings("serial")
public final class RawFrame extends JFrame {
    @SuppressWarnings("unused")
    private Viz viz;
    private RawPanel panel;

    public RawFrame(final Viz viz, final RawData rawData) {
        this.viz = viz;
        panel = new RawPanel(this, rawData);
        add(panel);
    }
}
