package lofar.gui;

import java.awt.Graphics;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JPanel;

import lofar.Viz;
import lofar.dataFormats.DataProvider;
import lofar.dataFormats.beamFormedData.BeamFormedData;
import lofar.dataFormats.preprocessedData.CompressedBeamFormedData.CompressedBeamFormedData;

@SuppressWarnings("serial")
public final class BeamFormedFrame extends GUIFrame {
    BeamFormedInnerPanel innerPanel;
    JFrame fr;
    
    public BeamFormedFrame(final Viz viz, final DataProvider data) {
        super(viz, data);
    }

    @Override
    protected GUIPanel createPanel() {
        return new BeamFormedPanel(viz, data, this);
    }

    @Override
    public void paintComponents(Graphics g) {
        super.paintComponents(g);
        fr.repaint();
    }
    
    @Override
    protected JPanel createAdditionalControlsPanel() {
        // for some reason, the inner panel does not work, so lets just create a new frame for now.
        fr = new JFrame();
        innerPanel = new BeamFormedInnerPanel(viz, data, this);
        fr.add(innerPanel);
        fr.pack();
        fr.setVisible(true);

        // return new BeamFormedInnerPanel(viz, data, this);
        return new JPanel();
    }

    protected void dedisperse(boolean enabled) {
        if (enabled) {
            // observation params, should be in hdf5 / compressed file
            float lowFreq = 138.96484375f;
            float freqStep = 0.1955f;

            // pulsar parameters.
            float dm = 12.455f;
            float period = 1.3373f;
            
            float[] folded = null;
            
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
            innerPanel.setFoldedData(folded);
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
        innerPanel.setFoldedData(null);
        repaint();
    }
}
