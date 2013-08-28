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
package nl.esciencecenter.eastroviz.dataformats.beamformed;

import nl.esciencecenter.eastroviz.AntennaBandpass;
import nl.esciencecenter.eastroviz.AntennaType;
import nl.esciencecenter.eastroviz.Dedispersion;
import nl.esciencecenter.eastroviz.dataformats.DataProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BeamFormedData extends DataProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeamFormedData.class);

    public static final boolean REMOVE_CHANNEL_0_FROM_VIEW = true;
    public static final boolean CORRECT_ANTENNA_BANDPASS = false;

    private float[][][] data; // [second][subband][channel]
    private boolean[][][] initialFlagged; // [second][subband][channel]

    private final int zoomFactor;
    private BeamFormedMetaData m;

    private float maxVal = -10000000.0f;
    private float minVal = 1.0E20f;

    private int stoke = 0;

    public BeamFormedData(final String fileName, final int maxSequenceNr, final int maxSubbands, int zoomFactor,
            float[][][] data, boolean[][][] initialFlagged, BeamFormedMetaData m) {

        super();
        init(fileName, maxSequenceNr, maxSubbands, new String[] { "I" }, new String[] { "none" });

        this.data = data;
        this.initialFlagged = initialFlagged;
        this.zoomFactor = zoomFactor;
        this.m = m;

        if (data == null || initialFlagged == null) {
            return;
        }

        if (CORRECT_ANTENNA_BANDPASS) {
            correctBandPass();
        }

        calculateStatistics();
    }

    private void correctBandPass() {
        AntennaBandpass bandPass = new AntennaBandpass();

        for (int s = 0; s < m.nrTimes; s++) {
            for (int subband = 0; subband < m.nrSubbands; subband++) {
                for (int channel = 0; channel < m.nrChannels; channel++) {
                    double frequency = getStartFrequency(subband, channel);
                    double correctionFactor = bandPass.getBandPassCorrectionFactor(AntennaType.HBA_LOW, frequency);
                    data[s][subband][channel] *= correctionFactor;
                }
            }
        }
    }

    private void calculateStatistics() {
        long initialFlaggedCount = 0;

        // calc min and max for scaling
        for (int second = 0; second < m.nrTimes; second++) {
            for (int subband = 0; subband < m.nrSubbands; subband++) {
                for (int channel = 0; channel < m.nrChannels; channel++) {
                    if (initialFlagged[second][subband][channel]) {
                        initialFlaggedCount++;
                    } else {
                        if (data[second][subband][channel] < minVal) {
                            minVal = data[second][subband][channel];
                        }
                        if (data[second][subband][channel] > maxVal) {
                            maxVal = data[second][subband][channel];
                        }
                    }
                }
            }
        }

        long nrSamples = m.nrTimes * m.nrSubbands * m.nrChannels;
        float percent = ((float) initialFlaggedCount / nrSamples) * 100.0f;

        LOGGER.info("samples already flagged in data set: " + initialFlaggedCount + "(" + percent + "%)");
    }

    public void dedisperse(float dm) {
        Dedispersion.dedisperse(data, initialFlagged, zoomFactor, m.minFrequency, m.channelWidth, dm);
        calculateStatistics();
    }

    public float[] fold(float period) {
        return Dedispersion.fold(data, initialFlagged, zoomFactor, period);
    }

    public double getStartFrequency(int subband, int channel) {
        double startFreq = m.beamCenterFrequency - Math.floor(m.nrSubbands / 2.0) * m.subbandWidth;
        return startFreq + subband * m.subbandWidth + channel * m.channelWidth;
    }

    public int getNrStokes() {
        return m.nrStokes;
    }

    @Override
    public int getNrSubbands() {
        return m.nrSubbands;
    }

    public int getNrSamples() {
        return m.totalNrSamples;
    }

    @Override
    public int getSizeX() {
        if (getMaxSequenceNr() > 0 && getMaxSequenceNr() < m.nrTimes) {
            return getMaxSequenceNr();
        }
        return m.nrTimes;
    }

    @Override
    public int getSizeY() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && m.nrChannels > 1) {
            return m.nrSubbands * (m.nrChannels - 1);
        } else {
            return m.nrSubbands * m.nrChannels;
        }
    }

    @Override
    public int getNrChannels() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && m.nrChannels > 1) {
            return m.nrChannels - 1;
        } else {
            return 1;
        }
    }

    private int getSubbandIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && m.nrChannels > 1) {
            return frequency / (m.nrChannels - 1);
        } else {
            return frequency / m.nrChannels;
        }
    }

    private int getChannelIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && m.nrChannels > 1) {
            return frequency % (m.nrChannels - 1) + 1;
        } else {
            return frequency % m.nrChannels;
        }
    }

    @Override
    public float getValue(final int x, final int y) {
        return (getRawValue(x, y) - minVal) / (maxVal - minVal);
    }

    @Override
    public float getRawValue(final int x, final int y) {
        return data[x][getSubbandIndex(y)][getChannelIndex(y)];
    }

    @Override
    public boolean isFlagged(final int x, final int y) {
        return initialFlagged[x][getSubbandIndex(y)][getChannelIndex(y)];
    }

    public float[][][] getData() {
        return data;
    }

    @Override
    public void flag() {
        // We do not have a beam formed data flagger at the moment.
    }

    public double getSubbandWidth() {
        return m.subbandWidth;
    }

    public double getChannelWidth() {
        return m.channelWidth;
    }

    public double getBeamCenterFrequency() {
        return m.beamCenterFrequency;
    }

    @Override
    public int getStation1() {
        return -1;
    }

    @Override
    public int setStation1(int station1) {
        return -1;
    }

    @Override
    public int getStation2() {
        return -1;
    }

    @Override
    public int setStation2(int station2) {
        return -1;
    }

    @Override
    public int getPolarization() {
        return stoke;
    }

    @Override
    public int setPolarization(int newValue) {
        return stoke;
    }

    @Override
    public String polarizationToString(int pol) {
        if (pol == 0) {
            return "I";
        } else {
            LOGGER.warn("illegal polarization");
            return "illegal polarization";
        }
    }

    @Override
    public int StringToPolarization(String polString) {
        if (polString.equals("I")) {
            return 0;
        } else {
            LOGGER.warn("illegal polarization");
            return -1;
        }
    }

    public int getNrStations() {
        return m.nrStations;
    }

    public int getBitsPerSample() {
        return m.bitsPerSample;
    }

    public double getClockFrequency() {
        return m.clockFrequency;
    }

    public int getNrBeams() {
        return m.nrBeams;
    }

    public double getMinFrequency() {
        return m.minFrequency;
    }

    public double getMaxFrequency() {
        return m.maxFrequency;
    }

    public int getNrSamplesPerTimeStep() {
        return m.nrSamplesPerTimeStep;
    }

    public int getZoomFactor() {
        return zoomFactor;
    }

    public double getTotalIntegrationTime() {
        return m.totalIntegrationTime;
    }
}
