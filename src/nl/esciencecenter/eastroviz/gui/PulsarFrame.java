/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.esciencecenter.eastroviz.gui;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import nl.esciencecenter.eastroviz.dataformats.DataProvider;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedDataReader;
import nl.esciencecenter.eastroviz.dataformats.preprocessed.compressedbeamformed.CompressedBeamFormedData;

/**
 * 
 * @author rob
 */
public class PulsarFrame extends javax.swing.JFrame {
    private static final long serialVersionUID = 1L;
    private DataProvider data;
    private float[] foldedData;
    private BeamFormedFrame parentFrame;

    public PulsarFrame(final DataProvider data, BeamFormedFrame f) {
        this.data = data;
        this.parentFrame = f;
        initComponents();
        setTitle("LOFAR pulsar visualizer");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drawProfile();
    }

    private void drawProfile() {
        int height = pulseProfilePanel.getHeight();
        int width = pulseProfilePanel.getWidth();

        if (foldedData == null) {
            pulseProfilePanel.getGraphics().clearRect(0, 0, width, height);
            return;
        }

        int background = pulseProfilePanel.getBackground().getRGB();

        // calculate min and max, so we can scale
        float max = Float.MIN_VALUE;
        float min = Float.MAX_VALUE;
        for (float element : foldedData) {
            if (element > max) {
                max = element;
            }
            if (element < min) {
                min = element;
            }
        }

        BufferedImage profileImage = new BufferedImage(foldedData.length, height, BufferedImage.TYPE_INT_RGB);

        for (int i = 0; i < foldedData.length; i++) {
            int lineHeight = (int) (((foldedData[i] - min) / (max - min)) * height);
            for (int y = 0; y < height - lineHeight; y++) {
                profileImage.setRGB(i, y, background);
            }

            for (int y = height - lineHeight; y < height; y++) {
                profileImage.setRGB(i, y, i /*100*/);
            }
        }

        pulseProfilePanel.getGraphics().drawImage(profileImage.getScaledInstance(width, height, Image.SCALE_DEFAULT), 0, 0, null);
    }

    protected void dedisperse(boolean enabled) {
        if (enabled) {

            // pulsar parameters.
            float dm = 12.455f;
            float period = 1.3373f;

            if (data instanceof BeamFormedData) {
                BeamFormedData bf = (BeamFormedData) data;
                bf.dedisperse(dm);
                foldedData = bf.fold(period);
            } else if (data instanceof CompressedBeamFormedData) {
                // observation params, should be in hdf5 / compressed file
                float lowFreq = 138.96484375f;
                float freqStep = 0.1955f / 256; // TODO COMPUTE! // 256 is nrChannels
                CompressedBeamFormedData bf = (CompressedBeamFormedData) data;
                final int nrSamplesPerSecond = 64;
                bf.dedisperse(nrSamplesPerSecond /*195312.5f*/, lowFreq, freqStep, dm);
                foldedData = bf.fold(nrSamplesPerSecond, period);
            } else {
                throw new RuntimeException("illegal data type");
            }
            parentFrame.getSamplePanel().setData(data);
            repaint();
            return;
        }

        // disabled
        if (data instanceof BeamFormedData) {
            BeamFormedData bf = (BeamFormedData) data;
            BeamFormedDataReader r =
                    new BeamFormedDataReader(bf.getFileName(), bf.getMaxSequenceNr(), bf.getMaxSubbands(), bf.getZoomFactor());
            try {
				data = r.read();
			} catch (IOException e) {
				e.printStackTrace();
			}
        } else if (data instanceof CompressedBeamFormedData) {
            CompressedBeamFormedData bf = (CompressedBeamFormedData) data;
            try {
                bf.read();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            throw new RuntimeException("illegal data type");
        }
        parentFrame.getSamplePanel().setData(data);
        repaint();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The content of
     * this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        dedisperseCheckBox = new javax.swing.JCheckBox();
        foldingCheckBox = new javax.swing.JCheckBox();
        pulseProfileLabel = new javax.swing.JLabel();
        pulseProfilePanel = new javax.swing.JPanel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        dedisperseCheckBox.setText("dedisperse");
        dedisperseCheckBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dedisperseCheckBoxActionPerformed(evt);
            }
        });

        foldingCheckBox.setText("show folding");
        foldingCheckBox.addActionListener(new java.awt.event.ActionListener() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                foldingCheckBoxActionPerformed(evt);
            }
        });

        pulseProfileLabel.setText("pulse profile:");

        pulseProfilePanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        javax.swing.GroupLayout pulseProfilePanelLayout = new javax.swing.GroupLayout(pulseProfilePanel);
        pulseProfilePanel.setLayout(pulseProfilePanelLayout);
        pulseProfilePanelLayout.setHorizontalGroup(pulseProfilePanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 0, Short.MAX_VALUE));
        pulseProfilePanelLayout.setVerticalGroup(pulseProfilePanelLayout.createParallelGroup(
                javax.swing.GroupLayout.Alignment.LEADING).addGap(0, 230, Short.MAX_VALUE));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(
                                                layout.createSequentialGroup()
                                                        .addGroup(
                                                                layout.createParallelGroup(
                                                                        javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addGroup(
                                                                                layout.createSequentialGroup()
                                                                                        .addComponent(dedisperseCheckBox)
                                                                                        .addGap(18, 18, 18)
                                                                                        .addComponent(foldingCheckBox))
                                                                        .addGroup(
                                                                                layout.createSequentialGroup().addGap(6, 6, 6)
                                                                                        .addComponent(pulseProfileLabel)))
                                                        .addGap(0, 137, Short.MAX_VALUE))
                                        .addComponent(pulseProfilePanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)).addContainerGap()));
        layout.setVerticalGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING).addGroup(
                layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(
                                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(dedisperseCheckBox).addComponent(foldingCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pulseProfileLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pulseProfilePanel, javax.swing.GroupLayout.DEFAULT_SIZE,
                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE).addContainerGap()));

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void dedisperseCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dedisperseCheckBoxActionPerformed
        dedisperse(dedisperseCheckBox.isSelected());
        repaint();
    }//GEN-LAST:event_dedisperseCheckBoxActionPerformed

    private void foldingCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_foldingCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_foldingCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox dedisperseCheckBox;
    private javax.swing.JCheckBox foldingCheckBox;
    private javax.swing.JLabel pulseProfileLabel;
    private javax.swing.JPanel pulseProfilePanel;
    // End of variables declaration//GEN-END:variables
}
