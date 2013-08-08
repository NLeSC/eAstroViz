package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class IntermediateFrame extends GUIFrame {
    public IntermediateFrame(final DataProvider data) {
        super(data);
        
        disableStation2Spinner();
    }

    @Override
    protected GUIPanel createPanel() {
        return new IntermediatePanel(getData(), this);
    }

    @Override
    protected int setStation1(int newVal) {
        int s = getData().setStation1(newVal);
        repaint();
        return s;
    }

    @Override
    protected int setStation2(int newVal) {
        return -1;
    }

    @Override
    protected String getBaselineText() {
        return "N.A.";
    }

    @Override
    protected String setPolarization(String newVal) {
        int pol = getData().StringToPolarization(newVal);
        String s = getData().polarizationToString(getData().setPolarization(pol));
        repaint();
        return s;
    }
}
