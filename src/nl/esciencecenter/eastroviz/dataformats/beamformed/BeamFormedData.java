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

    private int nrStokes;
    private int nrSubbands;
    private int nrChannels;
    private int nrStations;
    private int totalNrSamples;
    private int bitsPerSample;
    private double clockFrequency;
    private int nrBeams;
    private int nrSamplesPerTimeStep;
    private double minFrequency;
    private double maxFrequency;
    private int nrTimes;
    private double totalIntegrationTime; // in seconds
    private float maxVal = -10000000.0f;
    private float minVal = 1.0E20f;
    private double subbandWidth; // MHz
    private double channelWidth; // MHz
    private double beamCenterFrequency; // MHz
    private final int zoomFactor;

    private int stoke = 0;

    public BeamFormedData(final String fileName, final int maxSequenceNr, final int maxSubbands, float[][][] data,
            boolean[][][] initialFlagged, int nrStokes, int nrSubbands, int nrChannels, int nrStations, int totalNrSamples,
            int bitsPerSample, double clockFrequency, int nrBeams, int nrSamplesPerTimeStep, double minFrequency,
            double maxFrequency, int nrTimes, double totalIntegrationTime, float maxVal, float minVal, double subbandWidth,
            double channelWidth, double beamCenterFrequency, int zoomFactor) {

        super();
        init(fileName, maxSequenceNr, maxSubbands, new String[] { "I" }, new String[] { "none" });

        this.data = data;
        this.initialFlagged = initialFlagged;
        this.nrStokes = nrStokes;
        this.nrSubbands = nrSubbands;
        this.nrChannels = nrChannels;
        this.nrStations = nrStations;
        this.totalNrSamples = totalNrSamples;
        this.bitsPerSample = bitsPerSample;
        this.clockFrequency = clockFrequency;
        this.nrBeams = nrBeams;
        this.nrSamplesPerTimeStep = nrSamplesPerTimeStep;
        this.minFrequency = minFrequency;
        this.maxFrequency = maxFrequency;
        this.nrTimes = nrTimes;
        this.totalIntegrationTime = totalIntegrationTime;
        this.maxVal = maxVal;
        this.minVal = minVal;
        this.subbandWidth = subbandWidth;
        this.channelWidth = channelWidth;
        this.beamCenterFrequency = beamCenterFrequency;
        this.zoomFactor = zoomFactor;

        if (CORRECT_ANTENNA_BANDPASS) {
            correctBandPass();
        }

        calculateStatistics();
    }

    private void correctBandPass() {
        AntennaBandpass bandPass = new AntennaBandpass();

        for (int s = 0; s < nrTimes; s++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
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
        for (int second = 0; second < nrTimes; second++) {
            for (int subband = 0; subband < nrSubbands; subband++) {
                for (int channel = 0; channel < nrChannels; channel++) {
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

        long nrSamples = nrTimes * nrSubbands * nrChannels;
        float percent = ((float) initialFlaggedCount / nrSamples) * 100.0f;

        LOGGER.info("samples already flagged in data set: " + initialFlaggedCount + "(" + percent + "%)");
    }

    public void dedisperse(float dm) {
        Dedispersion.dedisperse(data, initialFlagged, zoomFactor, minFrequency, channelWidth, dm);
        calculateStatistics();
    }

    public float[] fold(float period) {
        return Dedispersion.fold(data, initialFlagged, zoomFactor, period);
    }

    public double getStartFrequency(int subband, int channel) {
        double startFreq = beamCenterFrequency - Math.floor(nrSubbands / 2.0) * subbandWidth;
        return startFreq + subband * subbandWidth + channel * channelWidth;
    }

    public int getNrStokes() {
        return nrStokes;
    }

    @Override
    public int getNrSubbands() {
        return nrSubbands;
    }

    public int getNrSamples() {
        return totalNrSamples;
    }

    @Override
    public int getSizeX() {
        if (getMaxSequenceNr() > 0 && getMaxSequenceNr() < nrTimes) {
            return getMaxSequenceNr();
        }
        return nrTimes;
    }

    @Override
    public int getSizeY() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrSubbands * (nrChannels - 1);
        } else {
            return nrSubbands * nrChannels;
        }
    }

    @Override
    public int getNrChannels() {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return nrChannels - 1;
        } else {
            return 1;
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
        return subbandWidth;
    }

    public double getChannelWidth() {
        return channelWidth;
    }

    public double getBeamCenterFrequency() {
        return beamCenterFrequency;
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
        return nrStations;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    public double getClockFrequency() {
        return clockFrequency;
    }

    public int getNrBeams() {
        return nrBeams;
    }

    public double getMinFrequency() {
        return minFrequency;
    }

    public double getMaxFrequency() {
        return maxFrequency;
    }

    public int getNrSamplesPerTimeStep() {
        return nrSamplesPerTimeStep;
    }

    public int getZoomFactor() {
        return zoomFactor;
    }

    public double getTotalIntegrationTime() {
        return totalIntegrationTime;
    }

    private int getSubbandIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return frequency / (nrChannels - 1);
        } else {
            return frequency / nrChannels;
        }
    }

    private int getChannelIndex(int frequency) {
        if (REMOVE_CHANNEL_0_FROM_VIEW && nrChannels > 1) {
            return frequency % (nrChannels - 1) + 1;
        } else {
            return frequency % nrChannels;
        }
    }
}
