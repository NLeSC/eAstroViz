package lofar;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import lofar.dataFormats.beamFormedData.BeamFormedData;
import lofar.dataFormats.preprocessedData.CompressedBeamFormedData.CompressedBeamFormedData;
import lofar.dataFormats.preprocessedData.filteredData.FilteredData;
import lofar.dataFormats.preprocessedData.intermediateData.IntermediateData;
import lofar.dataFormats.rawData.RawData;
import lofar.dataFormats.rawData.RawDataFrame;
import lofar.dataFormats.rawData.RawDataReader;
import lofar.dataFormats.visabilityData.MSMetaData;
import lofar.dataFormats.visabilityData.VisibilityData;
import lofar.gui.BeamFormedFrame;
import lofar.gui.IntermediateFrame;
import lofar.gui.RawFrame;
import lofar.gui.VisibilityFrame;

public final class Viz {
    public static final int REAL = 0;
    public static final int IMAG = 1;
    public static final int NR_POLARIZATIONS = 2;

    private final String fileName;
    private final String dataSetName;

    private int maxSequenceNr = Integer.MAX_VALUE;

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
            final boolean filtered, final boolean compressedBeamFormed, final int integrationFactor, final int maxSeqNo) {
        this.fileName = fileName;
        final File f = new File(fileName);
        dataSetName = f.getName();
        this.batch = batch;
        this.raw = raw;
        this.visibilities = visibilities;
        this.beamFormed = beamFormed;
        this.intermediate = intermediate;
        this.filtered = filtered;
        this.compressedBeamFormed = compressedBeamFormed;
        this.integrationFactor = integrationFactor;
        this.maxSequenceNr = maxSeqNo;
    }

    public String getDataSetName() {
        return dataSetName;
    }

    public void start() throws IOException {
        if (beamFormed) {
            final BeamFormedData beamFormedData = new BeamFormedData(fileName, maxSequenceNr, integrationFactor /* really the zoom factor in this case*/);
            beamFormedData.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(this, beamFormedData);
            beamFormedFrame.pack();
            beamFormedFrame.setVisible(true);
            //            beamFormedFrame.save("outputHDF5.bmp");

            return;
        }

        if (compressedBeamFormed) {
            final CompressedBeamFormedData compressedBeamFormedData = new CompressedBeamFormedData(fileName, integrationFactor, maxSequenceNr);
            compressedBeamFormedData.read();
            final BeamFormedFrame beamFormedFrame = new BeamFormedFrame(this, compressedBeamFormedData);
            beamFormedFrame.pack();
            beamFormedFrame.setVisible(true);
            //            IntermediateDataFrame.save("outputCompressedBeamFormed.bmp");

            return;
        }
        
        if (intermediate) {
            final IntermediateData intermediateData = new IntermediateData(fileName, integrationFactor, maxSequenceNr);
            intermediateData.read();

            final IntermediateFrame IntermediateDataFrame = new IntermediateFrame(this, intermediateData);
            IntermediateDataFrame.pack();
            IntermediateDataFrame.setVisible(true);
            //            IntermediateDataFrame.save("outputIntermediate.bmp");
            return;
        }

        if (filtered) {
            final FilteredData filteredData = new FilteredData(fileName, integrationFactor, maxSequenceNr);
            filteredData.read();

            final IntermediateFrame IntermediateDataFrame = new IntermediateFrame(this, filteredData);
            IntermediateDataFrame.pack();
            IntermediateDataFrame.setVisible(true);
            //            IntermediateDataFrame.save("outputFiltered.bmp");
            return;
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
            final VisibilityData visibilityData = new VisibilityData(fileName, 1/*station1*/, 0/*station2*/, maxSequenceNr);
            visibilityData.read();
            final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, 0 /*pol*/);
            vizFrame.pack();
            vizFrame.setVisible(true);
            return;
        }

        if (batch) {
            // batch processing mode
            final MSMetaData meta = VisibilityData.getMetaData(fileName);
            final int nrStations = meta.getNrStations();
            for (int station1 = 0; station1 < nrStations; station1++) {
                for (int station2 = 0; station2 < nrStations; station2++) {
                    final VisibilityData visibilityData = new VisibilityData(fileName, station1, station2, maxSequenceNr);
                    visibilityData.read();
                    for (int pol = 0; pol < NR_POLARIZATIONS * NR_POLARIZATIONS; pol++) {
                        final VisibilityFrame vizFrame = new VisibilityFrame(this, visibilityData, pol);
                        final String fileName = "baseline-" + station1 + "-" + station2 + "-polarization-" + VisibilityFrame.polarizationToString(pol) + ".bmp";
                        System.err.println("writing file: " + fileName);
                        vizFrame.save(fileName);
                        vizFrame.dispose();
                    }
                }
            }
        }
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
        int integrationFactor = 1;

        if (args.length < 1) {
            System.err.println("Usage: Viz [-batch] [-maxSeqNo] [-data format] <dataset directory or raw file>");
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
            } else if (args[i].equals("-integration")) {
                i++;
                integrationFactor = Integer.parseInt(args[i]);
            } else {
                // it must be the filename
                if (fileName != null) {
                    System.err.println("You cannot specify the file name twice. The first one was: " + fileName + ", the second one was: " + args[i]);
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
            new Viz(fileName, batch, raw, visibilities, beamFormed, intermediate, filtered, compressedBeamFormed, integrationFactor, maxSeqNo).start();
        } catch (final IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
