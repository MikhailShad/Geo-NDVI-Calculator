package NDVI_Task;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FileSaver {

    public static void save(FeatureCollection<SimpleFeatureType, SimpleFeature> resultFeatureCollection, String filename, SaveMode saveMode) {
    try {
        switch (saveMode) {
            case SHP:
                saveToSHP(resultFeatureCollection, filename);
                break;
            case GeoJSON:
                saveToGeoJSON(resultFeatureCollection, filename);
                break;
        }

        System.out.println(String.format("File was successfully saved to %s", filename + "." + saveMode.name()));

    } catch (IOException ex) {
        System.out.println(String.format(
                "Error occurred during the saving to %s file: %s in %s",
                saveMode.name(),
                ex.getMessage(),
                ex.getStackTrace()
                )
        );
    }
}

    private static void saveToSHP(FeatureCollection<SimpleFeatureType, SimpleFeature> resultFeatureCollection, String filename) throws IOException {
        File file = new File(filename + ".shp");

        Map<String, Serializable> creationParams = new HashMap<String, Serializable>();
        creationParams.put("url", DataUtilities.fileToURL(file));

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(creationParams);

        SimpleFeatureType featureType = resultFeatureCollection.getSchema();
        dataStore.createSchema(featureType);

        String typeName = dataStore.getTypeNames()[0];
        SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);

        Transaction transaction = new DefaultTransaction();
        try {
            featureStore.addFeatures(resultFeatureCollection);
            transaction.commit();
        } catch (IOException ex) {
            transaction.rollback();
        } finally {
            transaction.close();
        }
    }
    private static void saveToGeoJSON(FeatureCollection<SimpleFeatureType, SimpleFeature> resultFeatureCollection, String filename) throws IOException {
        File file = new File(filename + ".geojson");

        FeatureJSON featureJSON = new FeatureJSON();
        featureJSON.writeFeatureCollection(resultFeatureCollection, file);
    }
}
