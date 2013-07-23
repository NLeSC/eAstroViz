package lofar.flaggers;

public final class IntermediateFlagger extends Flagger {

    public IntermediateFlagger(final float sensitivity) {
        this.baseSensitivity = sensitivity;
    }

    public void flag(final float[] samples, final boolean[] flagged) {
      calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
      sumThreshold1D(samples, flagged);

      calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
      sumThreshold1D(samples, flagged);
      
      SIROperator(flagged, 0.4f);
      
//      printNrFlagged(flagged);
  }

    public void flagSmooth(final float[] samples, final boolean[] flagged) {
        
//      float[] tmp = new float[samples.length];
      
        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        System.err.println("samples flagged after 1st iter: " + getNrFlaggedSamples(flagged));
        
        calculateWinsorizedStatistics(samples, flagged); // sets mean, median, stdDev
        sumThreshold1D(samples, flagged);

        System.err.println("samples flagged after 2nd iter: " + getNrFlaggedSamples(flagged));

        float[] tmp = samples.clone();
        for(int i=0; i<samples.length; i++) {
            if(flagged[i]) {
                tmp[i] = 0.0f;
            }
        }
        float[] smoothed = oneDimensionalGausConvolution(tmp, 2.0f);
        float[] diff = new float[samples.length];
        for(int i=0; i<samples.length; i++) {
            diff[i] = samples[i] - smoothed[i];
        }        
            
      calculateWinsorizedStatistics(diff, flagged); // sets mean, median, stdDev
      sumThreshold1D(diff, flagged);

      System.err.println("samples flagged after smooth: " + getNrFlaggedSamples(flagged));

      SIROperator(flagged, 0.4f);
      
      System.err.println("samples flagged after SIR: " + getNrFlaggedSamples(flagged));

//      printNrFlagged(flagged);
  }
}

// on datasets/pulsar-experiment/station_0-non_flagged-10_mins-16_samples_per_second-bandpass_corrected.intermediate
// maxSeqNo 1000
// non-SIR 43805 0.53%
// SIR 0.2 58267 0.71%
// SIR 0.4 94308 1.15%
