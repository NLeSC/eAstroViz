package nl.esciencecenter.eAstroViz.gui;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;

@SuppressWarnings("serial")
public final class PreProcessedFrame extends GUIFrame {
    public PreProcessedFrame(final DataProvider data) {
        super(data);
        
        disableStation2Spinner();
    }

    @Override
    protected GUIPanel createPanel() {
        return new PreProcessedPanel(getData(), this);
    }

    @Override
    protected int setStation1(int newVal) {
        int s = samplePanel.setStation1(newVal);
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
    protected String setPolarization(String newString) {
        int newPol = getData().StringToPolarization(newString);
        int result = samplePanel.setPolarization(newPol);
        repaint();
        return getData().polarizationToString(result);
    }
}
