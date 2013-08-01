package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class IntermediateFrame extends GUIFrame {
    public IntermediateFrame(final DataProvider data) {
        super(data);
    }

    @Override
    protected GUIPanel createPanel() {
        return new IntermediatePanel(getData(), this);
    }
}
