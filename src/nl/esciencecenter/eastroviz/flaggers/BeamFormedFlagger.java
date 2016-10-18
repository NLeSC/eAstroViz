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

public final class BeamFormedFlagger extends Flagger {

    public BeamFormedFlagger(final float sensitivity, final float SIRValue) {
        super(sensitivity, SIRValue);
    }

    public void flag(final float[] samples, final boolean[] flagged) {
        calculateStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        calculateStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        SIROperator(flagged);

        //      printNrFlagged(flagged);
    }

    public void flagSmooth(final float[] samples, final boolean[] flagged) {

        //      float[] tmp = new float[samples.length];

        calculateStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        calculateStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        float[] tmp = samples.clone();
        for (int i = 0; i < samples.length; i++) {
            if (flagged[i]) {
                tmp[i] = 0.0f;
            }
        }
        float[] smoothed = oneDimensionalGausConvolution(tmp, 3.0f);
        float[] diff = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            diff[i] = samples[i] - smoothed[i];
        }

        calculateStatistics(diff, flagged); // sets mean, median, stdDev
        sumThreshold1D(diff, flagged);

        SIROperator(flagged);

        //      printNrFlagged(flagged);
    }
}

// on datasets/pulsar-experiment/station_0-non_flagged-10_mins-16_samples_per_second-bandpass_corrected.intermediate
// maxSeqNo 1000
// non-SIR 43805 0.53%
// SIR 0.2 58267 0.71%
// SIR 0.4 94308 1.15%
