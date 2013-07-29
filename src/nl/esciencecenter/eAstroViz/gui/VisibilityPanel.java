package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

public final class VisibilityPanel extends GUIPanel {
    private static final long serialVersionUID = 1L;

    VisibilityPanel(final DataProvider data, final VisibilityFrame parent) {
        super(parent, data);
    }
}
