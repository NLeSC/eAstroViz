package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

public final class VisibilityPanel extends GUIPanel {
    private static final long serialVersionUID = 1L;

    VisibilityPanel(final Viz viz, final DataProvider data, final VisibilityFrame parent) {
        super(viz, parent, data);
    }
}
