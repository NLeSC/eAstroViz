package lofar.gui;

import lofar.Viz;
import lofar.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class IntermediateFrame extends GUIFrame {
    public IntermediateFrame(final Viz viz, final DataProvider data) {
        super(viz, data);
    }

    @Override
    protected GUIPanel createPanel() {
        return new IntermediatePanel(viz, data, this);
    }
}
