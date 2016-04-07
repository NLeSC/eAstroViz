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
package nl.esciencecenter.eastroviz.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import nl.esciencecenter.eastroviz.dataformats.raw.RawData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public final class RawPanel extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(RawPanel.class);
    @SuppressWarnings("unused")
    private RawFrame parent;
    private BufferedImage image;
    private RawData rawData;

    public RawPanel(final RawFrame parent, final RawData rawData) {
        this.parent = parent;
        this.rawData = rawData;

        logger.info("time = " + rawData.getNrTimesTime() + ", subbands = " + rawData.getNrSubbands());

        generateImage();
    }

    private int colorToRGB(final float red, final float green, final float blue) {
        final int myRed = (int) (red * 255);
        final int myGreen = (int) (green * 255);
        final int myBlue = (int) (blue * 255);

        final int rgb = myRed << 16 | myGreen << 8 | myBlue;
        return rgb;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(rawData.getNrTimesTime(), rawData.getNrSubbands());
    }

    private void generateImage() {
        image = new BufferedImage(rawData.getNrTimesTime(), rawData.getNrSubbands(), BufferedImage.TYPE_INT_RGB);

        float maxAmplitude = 0.0f;
        for (int time = 0; time < rawData.getNrTimesTime(); time++) {
            for (int subband = 0; subband < rawData.getNrSubbands(); subband++) {
                final float amplitude = rawData.getAmplitude(time, subband, 0);
                if (amplitude > maxAmplitude) {
                    maxAmplitude = amplitude;
                }
            }
        }

        for (int time = 0; time < rawData.getNrTimesTime(); time++) {
            for (int subband = 0; subband < rawData.getNrSubbands(); subband++) {
                float red = 0;
                final float green = 0;
                final float blue = 0;

                final float amplitude = rawData.getAmplitude(time, subband, 0);

                red = amplitude / maxAmplitude;

                image.setRGB(time, subband, colorToRGB(red, green, blue));
            }
        }
    }

    @Override
    protected void paintComponent(final Graphics g) {
        super.paintComponent(g);

        g.drawImage(image, 0, 0, null);
    }
}
