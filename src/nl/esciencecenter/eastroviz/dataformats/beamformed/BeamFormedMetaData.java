package nl.esciencecenter.eastroviz.dataformats.beamformed;

public class BeamFormedMetaData {
    public int nrStokes;
    public int nrSubbands;
    public int nrChannels;
    public int nrStations;
    public int totalNrSamples;
    public int bitsPerSample;
    public double clockFrequency;
    public int nrBeams;
    public int nrSamplesPerTimeStep;
    public double minFrequency;
    public double maxFrequency;
    public int nrTimes;
    public double totalIntegrationTime; // in seconds
    public double subbandWidth; // MHz
    public double channelWidth; // MHz
    public double beamCenterFrequency; // MHz
}
