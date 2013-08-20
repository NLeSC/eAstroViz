package nl.esciencecenter.eAstroViz;

import static org.junit.Assert.assertEquals;
import nl.esciencecenter.eastroviz.dataformats.beamformed.BeamFormedData;

import org.junit.After;
import org.junit.Test;

public class TestBeamFormedData {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;

    static final String INPUT_FILE_NAME =
            "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.beamFormed";

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testRead() {
        BeamFormedData bfd = new BeamFormedData(INPUT_FILE_NAME, -1, -1, 1);
        bfd.read();

        assertEquals(INPUT_FILE_NAME, bfd.getFileName());
        assertEquals(NR_CHANNELS, bfd.getNrChannels());
        assertEquals(NR_SUBBANDS, bfd.getNrSubbands());
        assertEquals(NR_TIMES, bfd.getSizeX());

        float[][][] data = bfd.getData();

        // order should be [time][subband][channel]
        assertEquals(NR_TIMES, data.length);
        assertEquals(NR_SUBBANDS, data[0].length);
        assertEquals(NR_CHANNELS, data[0][0].length);
    }
}
