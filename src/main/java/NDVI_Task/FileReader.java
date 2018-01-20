package NDVI_Task;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.TreeMap;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.gce.geotiff.GeoTiffReader;

public class FileReader {
    public static final String shpFilePath = "src/data/RU-MOW/data/boundary-polygon.shp";
    public static final String band4FilePath = "src/data/rgeo_bands/rgeo_bands/LC81790212015146LGN00_sr_band4.tif";
    public static final String band5FilePath = "src/data/rgeo_bands/rgeo_bands/LC81790212015146LGN00_sr_band5.tif";

    public FeatureSource<SimpleFeatureType, SimpleFeature> openShapeFile(String path) throws IOException {
        File file = new File(path);
        Map<String, Object> map = new TreeMap<String, Object>();

        try {
            map.put("url", file.toURI().toURL());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        DataStore dataStore = DataStoreFinder.getDataStore(map);

        String typeName = dataStore.getTypeNames()[0];

        return dataStore.getFeatureSource(typeName);
    }

    public GridCoverage2D openGeoTiffFile(String path) throws IOException{
        GeoTiffReader geoTiffReader = new GeoTiffReader(new File(path));
        return  geoTiffReader.read(null);
    }

}
