package nl.esciencecenter.eAstroViz;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import nl.esciencecenter.eAstroViz.dataFormats.beamFormedData.BeamFormedData;
import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.CompressedBeamFormedData.CompressedBeamFormedData;
import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.filteredData.FilteredData;
import nl.esciencecenter.eAstroViz.dataFormats.preprocessedData.intermediateData.IntermediateData;
import nl.esciencecenter.eAstroViz.dataFormats.rawData.RawData;
import nl.esciencecenter.eAstroViz.dataFormats.rawData.RawDataFrame;
import nl.esciencecenter.eAstroViz.dataFormats.rawData.RawDataReader;
import nl.esciencecenter.eAstroViz.dataFormats.visibilityData.MSMetaData;
import nl.esciencecenter.eAstroViz.dataFormats.visibilityData.VisibilityData;
import nl.esciencecenter.eAstroViz.gui.BeamFormedFrame;
import nl.esciencecenter.eAstroViz.gui.IntermediateFrame;
import nl.esciencecenter.eAstroViz.gui.RawFrame;
import nl.esciencecenter.eAstroViz.gui.VisibilityFrame;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Viz {
    private static final Logger logger = LoggerFactory.getLogger(Viz.class);

    public static final int REAL = 0;
    public static final int IMAG = 1;
    public static final int NR_POLARIZATIONS = 2;

    private final String fileName;

    private int maxSequenceNr = Integer.MAX_VALUE;
    private int maxSubbands = Integer.MAX_VALUE;

    private final boolean batch;
    private final boolean raw;
    private boolean intermediate = false;
    private boolean beamFormed = false;
    private boolean filtered = false;
    private boolean compressedBeamFormed = false;
    private boolean visibilities = false;
    private final int integrationFactor;

    public static int baseline(final int station1, final int station2) {
        if (station2 > station1) {
            return -1;
        }
        return station2 * (station2 + 1) / 2 + station1;
    }

    public static final class ExtFilter implements FilenameFilter {
        private final String ext;

        public ExtFilter(String ext) {
            this.ext = ext;
        }

        @Override
        public boolean accept(File dir, String name) {
            return (name.endsWith(ext));
        }
    }

    public Viz(final String fileName, final boolean batch, final boolean raw, boolean visibilities, final boolean beamFormed, final boolean intermediate,
            final boolean filtered, final boolean compressedBeamFormed, final int integrationFactor, final int maxSeqNo, final int maxSubbands) {
        this.fileName = fileName;
        this.batch = batch;
        this.raw = raw;
        this.visibilities = visibilities;
        this.beamFormed = beamFormed;
        this.intermediate = intermediate;
        this.filtered = filtered;
        this.compressedBeamFormed = compressedBeamFormed;
        this.integrationFactor = integrationFactor;
        this.maxSequenceNr = maxSeqNo;
        this.maxSubbands = maxSubbands;
    }

    public void start() throws IOException {
        final int station = 0;

        if (beamFormed) {
            final BeamFormedData beamFormedData =
                    new BeamFormedData(fileName, maxSequenceNr, maxSubbands, integrationFactor /* really the zoom factor in this case*/);
            beamFormedData.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(beamFormedData);
            beamFormedFrame.pack();

            if (batch) {
                beamFormedFrame.save("outputBeamFormed.bmp");
                System.exit(0);
            }

            beamFormedFrame.setVisible(true);
            return;
        }

        if (compressedBeamFormed) {
            final CompressedBeamFormedData compressedBeamFormedData = new CompressedBeamFormedData(fileName, integrationFactor, maxSequenceNr, maxSubbands);
            compressedBeamFormedData.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(compressedBeamFormedData);
            beamFormedFrame.pack();

            if (batch) {
                beamFormedFrame.save("outputCompressedBeamFormed.bmp");
                System.exit(0);
            }

            beamFormedFrame.setVisible(true);
            return;
        }

        if (intermediate) {
            final IntermediateData intermediateData = new IntermediateData(fileName, integrationFactor, maxSequenceNr, maxSubbands, station);
            intermediateData.read();

            final IntermediateFrame intermediateFrame = new IntermediateFrame(intermediateData);
            intermediateFrame.pack();

            if (batch) {
                intermediateFrame.save("outputIntermediate.bmp");
                System.exit(0);
            }

            intermediateFrame.setVisible(true);
            return;
        }

        if (filtered) {
            if (batch) {
                FilteredData filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, 0);
                filteredData.read();
                int nrStations = filteredData.getNrStations();

                for (int s = 0; s < nrStations; s++) {
                    filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, s);
                    filteredData.read();

                    final IntermediateFrame filteredFrame = new IntermediateFrame(filteredData);
                    filteredFrame.save("outputFiltered-station-" + s + ".bmp");
                }
                System.exit(0);
            } else {
                final FilteredData filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr, maxSubbands, station);
                filteredData.read();

                final IntermediateFrame filteredFrame = new IntermediateFrame(filteredData);
                filteredFrame.pack();

                filteredFrame.setVisible(true);
                return;
            }
        }

        if (raw) {
            final int nrSubbands = 5;
            final RawData rawData = new RawData(nrSubbands);
            final RawDataReader r = new RawDataReader(fileName, nrSubbands);
            RawDataFrame f = null;
            int count = 0;

            while ((f = r.readFrame()) != null && count < 100) {
                // data is reused, so we need to copy... 
                rawData.addFrame(f.getData());
                count++;
            }

            final RawFrame rawFrame = new RawFrame(this, rawData);
            rawFrame.pack();
            rawFrame.setVisible(true);
            return;
        }

        if (visibilities) {
            final VisibilityData visibilityData = new VisibilityData(fileName, 1/*station1*/, 0/*station2*/, maxSequenceNr, maxSubbands);
            visibilityData.read();
            final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, 0 /*pol*/);
            vizFrame.pack();

            if (batch) {
                vizFrame.save("outputVisibility.bmp");
                System.exit(0);
            }

            vizFrame.setVisible(true);
            return;
        }

        if (batch) {
            // batch processing mode
            final MSMetaData meta = VisibilityData.getMetaData(fileName);
            final int nrStations = meta.getNrStations();
            for (int station1 = 0; station1 < nrStations; station1++) {
                for (int station2 = 0; station2 < nrStations; station2++) {
                    final VisibilityData visibilityData = new VisibilityData(fileName, station1, station2, maxSequenceNr, maxSubbands);
                    visibilityData.read();
                    for (int pol = 0; pol < NR_POLARIZATIONS * NR_POLARIZATIONS; pol++) {
                        final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, pol);
                        final String fileName = "baseline-" + station1 + "-" + station2 + "-polarization-" + VisibilityFrame.polarizationToString(pol) + ".bmp";
                        logger.info("writing file: " + fileName);
                        vizFrame.save(fileName);
                        vizFrame.dispose();
                    }
                }
            }
        }

        logger.info("unknwon file type!");
    }

    public static void main(final String[] args) {
        String fileName = null;
        boolean batch = false;
        boolean raw = false;
        boolean visibilities = false;
        boolean beamFormed = false;
        boolean intermediate = false;
        boolean filtered = false;
        boolean compressedBeamFormed = false;
        int maxSeqNo = Integer.MAX_VALUE;
        int maxSubbands = Integer.MAX_VALUE;
        int integrationFactor = 1;

        if (args.length < 1) {
            logger.info("Usage: Viz [-batch] [-maxSeqNo] [-data format] <dataset directory or raw file>");
            System.exit(1);
        }

        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-batch")) {
                batch = true;
            } else if (args[i].equals("-raw")) {
                raw = true;
            } else if (args[i].equals("-maxSeqNo")) {
                i++;
                maxSeqNo = Integer.parseInt(args[i]);
            } else if (args[i].equals("-maxSubbands")) {
                i++;
                maxSubbands = Integer.parseInt(args[i]);
            } else if (args[i].equals("-integration")) {
                i++;
                integrationFactor = Integer.parseInt(args[i]);
            } else {
                // it must be the filename
                if (fileName != null) {
                    logger.info("You cannot specify the file name twice. The first one was: " + fileName + ", the second one was: " + args[i]);
                    System.exit(1);
                }

                fileName = args[i];
            }
        }

        File f = new File(fileName);
        fileName = f.getPath();

        if (fileName.endsWith("compressedBeamFormed")) {
            compressedBeamFormed = true;
        } else if (fileName.endsWith("intermediate")) {
            intermediate = true;
        } else if (fileName.endsWith("filtered")) {
            filtered = true;
        } else if (fileName.endsWith("beamFormed")) {
            beamFormed = true;
        } else if (fileName.endsWith("visibilities")) {
            visibilities = true;
        }

        try {
            new Viz(fileName, batch, raw, visibilities, beamFormed, intermediate, filtered, compressedBeamFormed, integrationFactor, maxSeqNo, maxSubbands)
                    .start();
        } catch (final IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
