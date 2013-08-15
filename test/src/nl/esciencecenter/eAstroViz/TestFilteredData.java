package nl.esciencecenter.eAstroViz;

import java.io.IOException;
import static org.junit.Assert.*;

import nl.esciencecenter.eastroviz.dataformats.preprocessed.filtered.FilteredData;

import org.junit.Test;

public class TestFilteredData {

    static final int NR_CHANNELS = 16;
    static final int NR_SUBBANDS = 32;
    static final int NR_STATIONS = 5;
    static final int NR_POLARIZATIONS = 2;
    static final int NR_TIMES = 3 * 16 - 1;
    
    static final String INPUT_FILE_NAME = "test/fixtures/Flaggertest-01-11-11_dataset_tiny-5_stations-32_subbands-16_channels-flagged/result.filtered";
    
    @Test
    public void testRead() {
        int integrationFactor = 1;
        int maxSequenceNr = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;
        int station = 0;
        int pol = 0;
        
        final FilteredData filteredData = new FilteredData(INPUT_FILE_NAME, integrationFactor, maxSequenceNr, maxSubbands, station, pol);
        
        try {
            filteredData.read();
        } catch (IOException e) {
            fail("read failed: " + e);
        }
        
        assertEquals(INPUT_FILE_NAME, filteredData.getFileName());
        assertEquals(NR_CHANNELS, filteredData.getNrChannels());
        assertEquals(NR_POLARIZATIONS, filteredData.getNrPolarizations());
        assertEquals(NR_STATIONS, filteredData.getNrStations());
        assertEquals(NR_SUBBANDS, filteredData.getNrSubbands());
        assertEquals(NR_TIMES, filteredData.getTotalTime());
    }
}
