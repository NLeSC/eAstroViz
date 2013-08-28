package nl.esciencecenter.eastroviz.dataformats.beamformed;

public interface BeamFormedSampleHandler {
    public void handleSample(int second, int minorTime, int subband, int channel, float sample);
}
