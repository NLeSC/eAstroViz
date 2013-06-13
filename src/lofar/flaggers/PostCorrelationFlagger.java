package lofar.flaggers;

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
    int nrChannels;

    public PostCorrelationFlagger(final int nrChannels, final float sensitivity, final float SIREtaValue) {
        super(sensitivity, SIREtaValue);
        this.nrChannels = nrChannels;
    }

    public void flag(final float[][] samples, final int[] nrValidSamples, final boolean[] flagged) {// copy is [nrChannels][nrCrossPolasrizations];
        initFlags(nrValidSamples, flagged);

        for (int pol = 0; pol < samples[0].length; pol++) {
            final float[] powers = calculatePowers(samples, pol);
            flag(powers, flagged, pol);
        }
    }

    protected abstract void flag(final float[] powers, boolean[] flagged, int pol);

    private void initFlags(final int[] nrValidSamples, final boolean[] flagged) {
        for (int i = 0; i < nrChannels; i++) {
            if (nrValidSamples[i] == 0) {
                flagged[i] = true;
            } else {
                flagged[i] = false;
            }
        }
    }

    private float[] calculatePowers(final float[][] samples, final int pol) {
        final float[] power = new float[nrChannels];
        // calculate powers
        for (int i = 0; i < nrChannels; i++) {
            power[i] = samples[i][pol];
        }
        return power;
    }
}
