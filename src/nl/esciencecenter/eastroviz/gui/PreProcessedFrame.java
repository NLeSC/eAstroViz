package nl.esciencecenter.eastroviz.gui;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

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
    public int setStation1(int newVal) {
        int s = samplePanel.setStation1(newVal);
        repaint();
        return s;
    }

    @Override
    public int setStation2(int newVal) {
        return -1;
    }

    @Override
    public String getBaselineText() {
        return "N.A.";
    }

    @Override
    public String setPolarization(String newString) {
        int newPol = getData().StringToPolarization(newString);
        int result = samplePanel.setPolarization(newPol);
        repaint();
        return getData().polarizationToString(result);
    }

    @Override
    public int setPolarization(int newPol) {
        int result = samplePanel.setPolarization(newPol);
        repaint();
        return result;
    }
}
