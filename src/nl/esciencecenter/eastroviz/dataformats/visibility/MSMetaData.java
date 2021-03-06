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
package nl.esciencecenter.eastroviz.dataformats.visibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MSMetaData {
    private static final Logger logger = LoggerFactory.getLogger(MSMetaData.class);

    private final int lofarStoreageManagerVersion;
    private final int[] stations1;
    private final int[] stations2;
    private final double IONIntegrationTime;
    private final int nrChannels;
    private final int nrPolarizations;
    private final int integrationTimeProd;
    private final int alignment;
    private final boolean isBigEndian;
    private final int nrBytesPerValidSamples;
    private final int nrBaselines;
    private final int nrStations;

    private final int nrVisibilities;
    private final int visibilitiesSize;
    private final int alignedVisibilitiesSize;
    private final int nrValidSamplesSize;
    private final int alignedNrValidSamplesSize;
    private final int nrVisibilitiesInBaseline;
    private final int nrBytesInBaseline;

    static int align(final int value, final int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    public MSMetaData(final int lofarStoreageManagerVersion, final int[][] stations1, final int[][] stations2,
            final double iONIntegrationTime, final int nrChannelsPerSubband, final int nrCrossPolarizations,
            final int integrationTimeProd, final int alignment, final boolean isBigEndian, final int nrBytesPerValidSamples) {
        this.lofarStoreageManagerVersion = lofarStoreageManagerVersion;
        this.nrBaselines = stations1.length;

        int s = 1;
        while (true) {
            final int bl = s * (s + 1) / 2;
            if (bl == nrBaselines) {
                break;
            }
            s++;
        }
        this.nrStations = s;

        this.stations1 = new int[nrBaselines];
        this.stations2 = new int[nrBaselines];

        for (int i = 0; i < nrBaselines; i++) {
            this.stations1[i] = stations1[i][0];
            this.stations2[i] = stations2[i][0];
            logger.trace("baseline " + i + " is stations " + this.stations1[i] + " and " + this.stations2[i]);
        }

        this.IONIntegrationTime = iONIntegrationTime;
        this.nrChannels = nrChannelsPerSubband;
        this.nrPolarizations = nrCrossPolarizations;
        this.integrationTimeProd = integrationTimeProd;
        this.alignment = alignment;
        this.isBigEndian = isBigEndian;
        this.nrBytesPerValidSamples = nrBytesPerValidSamples;

        nrVisibilitiesInBaseline = nrChannels * nrCrossPolarizations;
        nrBytesInBaseline = nrVisibilitiesInBaseline * 2 * 4;
        nrVisibilities = nrBaselines * nrVisibilitiesInBaseline;
        visibilitiesSize = nrVisibilities * 2 * 4;
        alignedVisibilitiesSize = align(visibilitiesSize, alignment);
        nrValidSamplesSize = nrBaselines * nrChannels * 2;
        alignedNrValidSamplesSize = align(nrValidSamplesSize, alignment);

        logger.debug("nrStations: " + nrStations + ", nrBaseLines: " + nrBaselines + ", nrChannels: " + nrChannels
                + ", nrVisibilities = " + nrVisibilities);
        logger.debug("vis size of 1 subband = " + visibilitiesSize + " bytes");
        logger.debug("aligned vis size = " + alignedVisibilitiesSize + " bytes");
        logger.debug("nrValidSamples buf size of 1 subband = " + nrValidSamplesSize + " bytes");
        logger.debug("aligned nrValidSamples buf size of 1 subband = " + alignedNrValidSamplesSize + " bytes");

    }

    public int getLofarStoreageManagerVersion() {
        return lofarStoreageManagerVersion;
    }

    public int[] getStations1() {
        return stations1;
    }

    public int[] getStations2() {
        return stations2;
    }

    public double getIONIntegrationTime() {
        return IONIntegrationTime;
    }

    public int getNrChannels() {
        return nrChannels;
    }

    public int getNrCrossPolarizations() {
        return nrPolarizations;
    }

    public int getIntegrationTimeProd() {
        return integrationTimeProd;
    }

    public int getAlignment() {
        return alignment;
    }

    public boolean getIsBigEndian() {
        return isBigEndian;
    }

    public int getNrBytesPerValidSamples() {
        return nrBytesPerValidSamples;
    }

    public int getNrPolarizations() {
        return nrPolarizations;
    }

    public int getNrBaselines() {
        return nrBaselines;
    }

    public int getNrStations() {
        return nrStations;
    }

    public int getNrVisibilities() {
        return nrVisibilities;
    }

    public int getVisibilitiesSize() {
        return visibilitiesSize;
    }

    public int getAlignedVisibilitiesSize() {
        return alignedVisibilitiesSize;
    }

    public int getNrValidSamplesSize() {
        return nrValidSamplesSize;
    }

    public int getAlignedNrValidSamplesSize() {
        return alignedNrValidSamplesSize;
    }

    public int getNrVisibilitiesInBaseline() {
        return nrVisibilitiesInBaseline;
    }

    public int getNrBytesInBaseline() {
        return nrBytesInBaseline;
    }
}
