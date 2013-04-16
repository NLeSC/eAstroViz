package lofar.gui;

import lofar.Viz;
import lofar.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class BeamFormedPanel extends GUIPanel {
    public BeamFormedPanel(final Viz viz, final DataProvider data, final BeamFormedFrame parent) {
        super(viz, parent, data);
    }
}
