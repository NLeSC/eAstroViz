package nl.esciencecenter.eAstroViz.gui;

import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import nl.esciencecenter.eAstroViz.Viz;
import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;
import nl.esciencecenter.eAstroViz.dataFormats.beamFormedData.BeamFormedData;
import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.CompressedBeamFormedData.CompressedBeamFormedData;


@SuppressWarnings("serial")
public final class BeamFormedFrame extends GUIFrame {
    private float[] folded;

    public BeamFormedFrame(final Viz viz, final DataProvider data) {
        super(viz, data);
    }

    @Override
    protected GUIPanel createPanel() {
        return new BeamFormedPanel(viz, data, this);
    }

    @Override
    protected JPanel createAdditionalControlsPanel() {
        // for some reason, the inner panel does not work, so lets just create a new frame for now.
        JFrame fr = new JFrame();
        BeamFormedInnerPanel innerPanel = new BeamFormedInnerPanel(viz, data, this);
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

            if (data instanceof BeamFormedData) {
                BeamFormedData bf = (BeamFormedData) data;
                bf.dedisperse(lowFreq, freqStep, dm);
                folded = bf.fold(period);
            } else if (data instanceof CompressedBeamFormedData) {
                CompressedBeamFormedData bf = (CompressedBeamFormedData) data;
                final int nrSamplesPerSecond = 64;
                bf.dedisperse(nrSamplesPerSecond /*195312.5f*/, lowFreq, freqStep, dm);
                folded = bf.fold(nrSamplesPerSecond, period);
            } else {
                throw new RuntimeException("illegal data type");
            }
            samplePanel.setData(data);
            repaint();
            return;
        }

        // disabled
        if (data instanceof BeamFormedData) {
            BeamFormedData bf = (BeamFormedData) data;
            bf.read();
        } else if (data instanceof CompressedBeamFormedData) {
            CompressedBeamFormedData bf = (CompressedBeamFormedData) data;
            try {
                bf.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.err.println("illegal data type");
        }
        samplePanel.setData(data);
        repaint();
    }
    
    public float[] getFoldedData() {
        return folded;
    }
}
