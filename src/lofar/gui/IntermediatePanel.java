package lofar.gui;

import lofar.Viz;
import lofar.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class IntermediatePanel extends GUIPanel {
    public IntermediatePanel(final Viz viz, final DataProvider data, final IntermediateFrame parent) {
        super(viz, parent, data);
    }
}
