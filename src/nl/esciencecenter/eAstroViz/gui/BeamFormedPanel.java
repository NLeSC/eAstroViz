package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class BeamFormedPanel extends GUIPanel {
    public BeamFormedPanel(final Viz viz, final DataProvider data, final BeamFormedFrame parent) {
        super(viz, parent, data);
    }
}
