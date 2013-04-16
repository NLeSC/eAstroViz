package lofar.gui;

import lofar.Viz;
import lofar.dataFormats.DataProvider;

public final class VisibilityPanel extends GUIPanel {
    private static final long serialVersionUID = 1L;

    VisibilityPanel(final Viz viz, final DataProvider data, final VisibilityFrame parent) {
        super(viz, parent, data);
    }
}
