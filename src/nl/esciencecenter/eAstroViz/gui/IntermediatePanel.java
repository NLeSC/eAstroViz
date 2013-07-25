package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class IntermediatePanel extends GUIPanel {
    public IntermediatePanel(final Viz viz, final DataProvider data, final IntermediateFrame parent) {
        super(viz, parent, data);
    }
}
