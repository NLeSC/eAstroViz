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

// I = XX - Q
// I = YY + Q
// XX - Q = YY + Q
// 2Q = XX - YY
// Q = (XX - YY) / 2

// XX = I + (XX - YY) / 2
// I = XX - (XX-YY) / 2
// I = XX - .5XX - .5YY
// I = .5XX - .5YY
// I = .5 (XX - YY)

public abstract class PostCorrelationFlagger extends Flagger {
    private int nrChannels;

    public PostCorrelationFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(sensitivity, SIREtaValue);
        this.nrChannels = nrChannels;
    }

    public void flag(final float[][] samples, final boolean[] flagged) {

        boolean[][] flags = new boolean[samples[0].length][];
        
        for (int pol = 0; pol < samples[0].length; pol++) {
            flags[pol] = flagged.clone(); // start with flags that are passed in.
            
            final float[] powers = calculatePowers(samples, pol);
            flag(powers, flags[pol], pol);
        }

        // calculate union of flags
        for (int pol = 0; pol < samples[0].length; pol++) {
            for (int i = 0; i < samples.length; i++) {
                flagged[i] |= flags[pol][i];
            }
        }

        SIROperator(flagged);
    }

    protected abstract void flag(final float[] powers, boolean[] flagged, int pol);

    private float[] calculatePowers(final float[][] samples, final int pol) {
        final float[] power = new float[nrChannels];
        // calculate powers
        for (int i = 0; i < nrChannels; i++) {
            power[i] = samples[i][pol];
        }
        return power;
    }

    int getNrChannels() {
        return nrChannels;
    }

    void setNrChannels(int nrChannels) {
        this.nrChannels = nrChannels;
    }
}
