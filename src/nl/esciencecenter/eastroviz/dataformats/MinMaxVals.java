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
package nl.esciencecenter.eastroviz.dataformats;

public final class MinMaxVals {
    private float minVal = 1.0E20f;
    private float maxVal = -10000000.0f;

    private float[] minVals;
    private float[] maxVals;

    public MinMaxVals(final int size) {
        minVals = new float[size];
        maxVals = new float[size];
        for (int sb = 0; sb < size; sb++) {
            minVals[sb] = 1.0E20f;
            maxVals[sb] = -10000000.0f;
        }
    }

    public void processValue(final float sample, final int sb) {
        if (sample < minVal) {
            minVal = sample;
        }
        if (sample > maxVal) {
            maxVal = sample;
        }

        if (sample < minVals[sb]) {
            minVals[sb] = sample;
        }
        if (sample > maxVals[sb]) {
            maxVals[sb] = sample;
        }
    }

    public float getMin() {
        return minVal;
    }

    public float getMin(final int sb) {
        return minVals[sb];
    }

    public float getMax() {
        return maxVal;
    }

    public float getMax(final int sb) {
        return maxVals[sb];
    }
}
