package nl.esciencecenter.eastroviz.gui;

import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.preprocessed.compressedbeamformed.CompressedBeamFormedData;

@SuppressWarnings("serial")
public final class BeamFormedFrame extends GUIFrame {
    private float[] folded;

    public BeamFormedFrame(final DataProvider data) {
        super(data);
    }

    @Override
    protected GUIPanel createPanel() {
        return new BeamFormedPanel(getData(), this);
    }

    @Override
    protected JPanel createAdditionalControlsPanel() {
        // for some reason, the inner panel does not work, so lets just create a new frame for now.
        JFrame fr = new JFrame();
        BeamFormedInnerPanel innerPanel = new BeamFormedInnerPanel(getData(), this);
        fr.setTitle("LOFAR visualizer dedispersion");
        fr.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        fr.add(innerPanel);
        fr.pack();
        fr.setVisible(true);

        return new JPanel();

        //        return new BeamFormedInnerPanel(viz, data, this);
    }

    protected void dedisperse(boolean enabled) {
        if (enabled) {
            // observation params, should be in hdf5 / compressed file
            float lowFreq = 138.96484375f;
            float freqStep = 0.1955f;

            // pulsar parameters.
            float dm = 12.455f;
            float period = 1.3373f;

            if (getData() instanceof BeamFormedData) {
                BeamFormedData bf = (BeamFormedData) getData();
                bf.dedisperse(lowFreq, freqStep, dm);
                folded = bf.fold(period);
            } else if (getData() instanceof CompressedBeamFormedData) {
                CompressedBeamFormedData bf = (CompressedBeamFormedData) getData();
                final int nrSamplesPerSecond = 64;
                bf.dedisperse(nrSamplesPerSecond /*195312.5f*/, lowFreq, freqStep, dm);
                folded = bf.fold(nrSamplesPerSecond, period);
            } else {
                throw new RuntimeException("illegal data type");
            }
            getSamplePanel().setData(getData());
            repaint();
            return;
        }

        // disabled
        if (getData() instanceof BeamFormedData) {
            BeamFormedData bf = (BeamFormedData) getData();
            bf.read();
        } else if (getData() instanceof CompressedBeamFormedData) {
            CompressedBeamFormedData bf = (CompressedBeamFormedData) getData();
            try {
                bf.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("illegal data type");
        }
        getSamplePanel().setData(getData());
        repaint();
    }

    public float[] getFoldedData() {
        return folded;
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
