package NDVI_Task;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.data.*;
import org.geotools.feature.*;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.geometry.BoundingBox;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;

import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.FormatDescriptor;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.util.ArrayList;

public class CalculatorNDVI {
    FileReader fileReader;

    FeatureSource<SimpleFeatureType, SimpleFeature> boundariesSHP;
    FeatureCollection<SimpleFeatureType, SimpleFeature> featureCollection;
    FeatureCollection<SimpleFeatureType, SimpleFeature> resultFeatureCollection;

    GridCoverage2D nirCoverage, redCoverage;

    ArrayList<GridCoverage2D> ndviCoverageForBoundaries;
    ArrayList<BoundingBox> boundaryBoxes;

    class MinMaxAvg {
        private double min, max, avg;

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public double getAverage() {
            return avg;
        }

        public MinMaxAvg(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }
    }

    public CalculatorNDVI() {
        fileReader = new FileReader();
        boundaryBoxes = new ArrayList<BoundingBox>();
        ndviCoverageForBoundaries = new ArrayList<GridCoverage2D>();

        openPreSetFiles();
    }
    public FeatureCollection<SimpleFeatureType, SimpleFeature> getResultFeatureCollection(){
        return resultFeatureCollection;
    }

    private void openPreSetFiles() {
        try {
            boundariesSHP = fileReader.openShapeFile(FileReader.shpFilePath);
            redCoverage = fileReader.openGeoTiffFile(FileReader.band4FilePath);
            nirCoverage = fileReader.openGeoTiffFile(FileReader.band5FilePath);
        } catch (IOException ex) {
            //TODO
            System.out.println(ex.getMessage());
        }
    }

    public void calculateNDVI() {
        try {
            featureCollection = boundariesSHP.getFeatures();
            resultFeatureCollection = calculateResultFeatureCollection();
        } catch (Exception ex) {
            //TODO
            System.out.println(ex.getMessage() + " in " + ex.getStackTrace().toString());
        }
    }

    private FeatureCollection<SimpleFeatureType, SimpleFeature> calculateResultFeatureCollection() throws FactoryException, TransformException {

        SimpleFeatureType resultFeatureType = createResultSimpleFeatureType(featureCollection.getSchema());
        ArrayList<SimpleFeature> resultSimpleFeatures = new ArrayList<SimpleFeature>();

        int currentRegion = 0, allRegions = featureCollection.size();
        System.out.println(String.format("Completed regions: %d / %d", currentRegion, allRegions));

        for (FeatureIterator<SimpleFeature> iterator = featureCollection.features(); iterator.hasNext(); ) {

            SimpleFeature feature = iterator.next();
            BoundingBox boundingBox = feature.getBounds();

            GridCoverage2D ndviCoverage = calculateNDVIForSpecificBoundary(boundingBox);
            MinMaxAvg minMaxAvg = calculateMinMaxAvgForSpecificBoundary(feature, ndviCoverage);

            resultSimpleFeatures.add(
                    createFeatureWithMinMaxAvg(
                            feature,
                            resultFeatureType,
                            minMaxAvg.getMin(),
                            minMaxAvg.getMax(),
                            minMaxAvg.getAverage()
                    )
            );

            System.out.println(String.format("Completed regions: %d / %d", ++currentRegion, allRegions));
        }

        return DataUtilities.collection(resultSimpleFeatures);
    }

    private GridCoverage2D cropCoverage(GridCoverage2D source, org.opengis.geometry.Envelope boundaryEnvelope) throws FactoryException, TransformException {
        CoverageProcessor processor = CoverageProcessor.getInstance();

        GeneralEnvelope generalEnvelope = CRS.transform(boundaryEnvelope, source.getCoordinateReferenceSystem());
        //An example of manually creating the operation and parameters we want
        final ParameterValueGroup param = processor.getOperation("CoverageCrop").getParameters();
        param.parameter("Source").setValue(source);
        param.parameter("Envelope").setValue(generalEnvelope);

        return (GridCoverage2D) processor.doOperation(param);

    }

    private GridCoverage2D calculateNDVIForSpecificBoundary(Envelope boundingBox) throws FactoryException, TransformException {

        GridCoverage2D localNIRCoverage = cropCoverage(nirCoverage, boundingBox),
                localREDCoverage = cropCoverage(redCoverage, boundingBox);

        RenderedImage nirImage = localNIRCoverage.getRenderedImage(),
                redImage = localREDCoverage.getRenderedImage();

        ParameterBlock pbSubtracted = new ParameterBlock();
        pbSubtracted.addSource(nirImage);
        pbSubtracted.addSource(redImage);

        RenderedOp subtractedImage = JAI.create("subtract", pbSubtracted);

        ParameterBlock pbAdded = new ParameterBlock();
        pbAdded.addSource(nirImage);
        pbAdded.addSource(redImage);

        RenderedOp addedImage = JAI.create("add", pbAdded);

        RenderedOp typeAdd = FormatDescriptor.create(addedImage, DataBuffer.TYPE_DOUBLE, null);
        RenderedOp typeSub = FormatDescriptor.create(subtractedImage, DataBuffer.TYPE_DOUBLE, null);

        ParameterBlock pbNDVI = new ParameterBlock();
        pbNDVI.addSource(typeSub);
        pbNDVI.addSource(typeAdd);

        RenderedOp NDVIop = JAI.create("divide", pbNDVI);

        GridCoverageFactory gridCoverageFactory = new GridCoverageFactory();
        return gridCoverageFactory.create("Raster", NDVIop, redCoverage.getEnvelope());
    }

    private MinMaxAvg calculateMinMaxAvgForSpecificBoundary(SimpleFeature feature, GridCoverage2D ndviCoverage) {
        double min = Integer.MAX_VALUE,
                max = Integer.MIN_VALUE,
                average = 0;

        RenderedImage ndviImage = ndviCoverage.getRenderedImage();
        Raster ndviImageData = ndviImage.getData();

        int width = ndviImageData.getWidth(),
                height = ndviImageData.getHeight();

        double[] temp = new double[width * height];
        double[] ndviPixels = ndviImageData.getPixels(ndviImageData.getMinX(), ndviImageData.getMinY(), width, height, temp);

        for (int i = 0; i < ndviPixels.length; ++i) {
            if (ndviPixels[i] < min)
                min = ndviPixels[i];

            if (ndviPixels[i] > max)
                max = ndviPixels[i];

            average += ndviPixels[i];
        }

        average /= ndviPixels.length;

        return new MinMaxAvg(min, max, average);
    }

    private SimpleFeatureType createResultSimpleFeatureType(SimpleFeatureType sampleFeatureType) {

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();

        typeBuilder.setName(sampleFeatureType.getName());
        typeBuilder.setCRS(sampleFeatureType.getCoordinateReferenceSystem());

        for (AttributeDescriptor attributeDescriptor : sampleFeatureType.getAttributeDescriptors())
            typeBuilder.add(attributeDescriptor);

        typeBuilder.add("MinNDVI", Double.class);
        typeBuilder.add("MaxNDVI", Double.class);
        typeBuilder.add("AvgNDVI", Double.class);

        return typeBuilder.buildFeatureType();
    }

    private SimpleFeature createFeatureWithMinMaxAvg(SimpleFeature sampleFeature, SimpleFeatureType sampleFeatureType, double min, double max, double average) {
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(sampleFeatureType);

        for (Property property : sampleFeature.getProperties()) {
            builder.set(property.getName(), property.getValue());
        }

        builder.set("MinNDVI", min);
        builder.set("MaxNDVI", max);
        builder.set("AvgNDVI", average);

        return builder.buildFeature(sampleFeature.getID());
    }
}
