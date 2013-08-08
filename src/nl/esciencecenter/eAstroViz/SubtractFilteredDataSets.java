package nl.esciencecenter.eAstroViz;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import nl.esciencecenter.eAstroViz.dataFormats.DataProvider;
import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.filteredData.FilteredData;
import nl.esciencecenter.eAstroViz.gui.ColorMap;
import nl.esciencecenter.eAstroViz.gui.ColorMapInterpreter;

public class SubtractFilteredDataSets {
    public static void main(String[] args) throws IOException {
        int integrationFactor = 1;
        int maxSequenceNr = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;
        int station = 0;
        int COLOR_BLACK = 0;
        int COLOR_WHITE = 0xFFFFFF;
        
        final FilteredData filteredData1 = new FilteredData(args[0], integrationFactor, maxSequenceNr, maxSubbands, station, 0);
        filteredData1.read();

        final FilteredData filteredData2 = new FilteredData(args[1], integrationFactor, maxSequenceNr, maxSubbands, station, 0);
        filteredData2.read();

        assert filteredData1.getSizeX() == filteredData2.getSizeX();
        assert filteredData1.getSizeY() == filteredData2.getSizeY();

        
        ColorMapInterpreter colorMaps = new ColorMapInterpreter();
        ColorMap colorMap = colorMaps.getColorMap("bw");
        BufferedImage image = new BufferedImage(filteredData1.getSizeX(), filteredData1.getSizeY(), BufferedImage.TYPE_INT_RGB);

        float[][] diff = new float[filteredData1.getSizeY()][filteredData1.getSizeX()];
        for(int y=0; y<filteredData1.getSizeY(); y++) {
            for (int x=0; x < filteredData1.getSizeX(); x++) {
                float sample1 = filteredData1.getRawValue(x, y);
                float sample2 = filteredData2.getRawValue(x, y);
                diff[y][x] = Math.abs(sample2 - sample1);
            }
        }
        DataProvider.scale(diff);
        
        for(int y=0; y<filteredData1.getSizeY(); y++) {
            for (int x=0; x < filteredData1.getSizeX(); x++) {
                int color;
                
                if(filteredData1.isFlagged(x, y) && filteredData2.isFlagged(x, y)) {
                    color = COLOR_BLACK;
                } else if(filteredData1.isFlagged(x, y) && !filteredData2.isFlagged(x, y)) {
                    color = COLOR_WHITE;
                } else if (!filteredData1.isFlagged(x, y) && filteredData2.isFlagged(x, y)) {
                    color = COLOR_WHITE;
                } else {
                    color = colorMap.getColor(0.0f, 1.0f, diff[y][x]);
                }
                
                image.setRGB(x, filteredData1.getSizeY() - y - 1, color);
            }
        }
        
        ImageIO.write(image, "bmp", new File("diff.bmp"));
    }
}
