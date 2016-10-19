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
package nl.esciencecenter.eastroviz.flaggers;

public class PostCorrelationThresholdFlagger extends PostCorrelationFlagger {

    private final float cutoffThreshold = 7.0f;

    public PostCorrelationThresholdFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(nrChannels, sensitivity, SIREtaValue);
    }

    // we have the data for one second, all frequencies in a subband.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        //                calculateStatistics();
        calculateStatistics(powers, flagged);

        final float threshold = getMedian() + cutoffThreshold * getBaseSensitivity() * getStdDev();

        // if one of the polarizations exceeds the threshold, flag them all.
        for (int channel = 0; channel < getNrChannels(); channel++) {
            if (powers[channel] >= threshold) {
                flagged[channel] = true;
            }
        }
    }

    //        printNrFlagged(flagged);
}
