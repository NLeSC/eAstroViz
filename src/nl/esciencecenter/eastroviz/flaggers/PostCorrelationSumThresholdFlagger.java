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

public class PostCorrelationSumThresholdFlagger extends PostCorrelationFlagger {

    public PostCorrelationSumThresholdFlagger(final int nrChannels, final float baseSensitivity, final float SIREtaValue) { // higher baseSensitivity means less sensitive
        super(nrChannels, baseSensitivity, SIREtaValue);
    }

    // we have the data for one second, all frequencies in a subband.
    // if one of the polarizations exceeds the threshold, flag them all.
    @Override
    protected void flag(final float[] powers, final boolean[] flagged, final int pol) {
        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        sumThreshold1D(powers, flagged);

        calculateWinsorizedStatistics(powers, flagged); // sets mean, median, stdDev
        sumThreshold1D(powers, flagged);

        SIROperator(flagged);

        //        printNrFlagged(flagged);
    }
}
