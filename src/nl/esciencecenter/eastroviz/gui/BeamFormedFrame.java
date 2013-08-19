package nl.esciencecenter.eastroviz.gui;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;

@SuppressWarnings("serial")
public final class BeamFormedFrame extends GUIFrame {

    public BeamFormedFrame(final DataProvider data) {
        super(data);

        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new PulsarFrame(getData(), BeamFormedFrame.this).setVisible(true);
            }
        });
    }

    @Override
    protected GUIPanel createPanel() {
        return new BeamFormedPanel(getData(), this);
    }

    @Override
    public int setStation1(int newVal) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int setStation2(int newVal) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getBaselineText() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String setPolarization(String s) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int setPolarization(int pol) {
        // TODO Auto-generated method stub
        return 0;
    }
}
