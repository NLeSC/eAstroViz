package nl.esciencecenter.eastroviz;

import ucar.nc2.NetcdfFile;

public class NetCDFTest {
    public static void main(String[] args) throws Exception {
        String filename = args[0];
        NetcdfFile ncfile = null;
        ncfile = NetcdfFile.open(filename);
        ncfile.close();
    }
}
