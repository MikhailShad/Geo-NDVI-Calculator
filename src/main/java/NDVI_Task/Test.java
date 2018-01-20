package NDVI_Task;

public class Test {
    public static void main(String[] args){
        CalculatorNDVI calculatorNDVI = new CalculatorNDVI();
        calculatorNDVI.calculateNDVI();
        FileSaver.save(calculatorNDVI.getResultFeatureCollection(), "testSHP", SaveMode.SHP);
        FileSaver.save(calculatorNDVI.getResultFeatureCollection(), "testSHP", SaveMode.GeoJSON);
    }
}
