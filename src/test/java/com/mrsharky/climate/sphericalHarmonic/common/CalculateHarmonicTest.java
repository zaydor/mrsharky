/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrsharky.climate.sphericalHarmonic.common;

import com.mrsharky.climate.sphericalHarmonic.AreasForGrid;
import static com.mrsharky.climate.sphericalHarmonic.common.Pca_EigenValVec.CutoffVarExplained;
import com.mrsharky.dataAnalysis.PcaCovJBlas;
import com.mrsharky.discreteSphericalTransform.DiscreteSphericalTransform;
import static com.mrsharky.discreteSphericalTransform.DiscreteSphericalTransform.GetLatitudeCoordinates;
import static com.mrsharky.discreteSphericalTransform.DiscreteSphericalTransform.GetLongitudeCoordinates;
import com.mrsharky.discreteSphericalTransform.InvDiscreteSphericalTransform;
import com.mrsharky.discreteSphericalTransform.SphericalHarmonic;
import com.mrsharky.helpers.ComplexArray;
import static com.mrsharky.helpers.Utilities.RadiansToLongitude;
import com.mrsharky.helpers.DoubleArray;
import static com.mrsharky.helpers.JblasMatrixHelpers.ApacheMath3ToJblas;
import com.mrsharky.helpers.Utilities;
import static com.mrsharky.helpers.Utilities.RadiansToLatitude;
import com.mrsharky.stations.netcdf.AngellKorshoverNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.apache.commons.math3.complex.Complex;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.jblas.ComplexDoubleMatrix;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Julien Pierret
 */
public class CalculateHarmonicTest {
    
    public CalculateHarmonicTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    
    private static Complex GenerateRandomHarmonic(int l, Random rand) {
        double realSign = (rand.nextDouble() > 0.75 ? -1.0 : 1.0);
        double imagSign = (rand.nextDouble() > 0.75 ? -1.0 : 1.0);
        double real = rand.nextDouble()*realSign;
        double imag = rand.nextDouble()*imagSign;
        // No i's on l = 0
        if (l == 0) {
            imag = 0.0;
        }
        return new Complex(real, imag);
    }

    private static Complex GenerateRandomHarmonic(int l) {
        double realSign = (Math.random() > 0.75 ? -1.0 : 1.0);
        double imagSign = (Math.random() > 0.75 ? -1.0 : 1.0);
        double real = Math.random()*realSign;
        double imag = Math.random()*imagSign;
        // No i's on l = 0
        if (l == 0) {
            imag = 0.0;
        }
        return new Complex(real, imag);
    }
    
    @Test
    public void testNonHarmonicFriendlyPoints() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        int latNum = q+1;
        int lonNum = q*2;
        
        AreasForGrid areasForGrid = new AreasForGrid(latNum, lonNum, 1.0);
        double[] preLat = Utilities.LatitudeToRadians(areasForGrid.GetLatitude());
        double[] preLon = Utilities.LongitudeToRadians(areasForGrid.GetLongitude());
        
        double[] lat = new double[latNum * lonNum];
        double[] lon = new double[latNum * lonNum];
        
        {
            int counter = 0;
            for (int i = 0; i < preLat.length; i++) {
                for (int j = 0; j < preLon.length; j++) {
                    lat[counter] = preLat[i];
                    lon[counter] = preLon[j];
                    counter++;
                }
            }
        }
        
        SphericalHarmonic sh = new SphericalHarmonic(q);
        double originalFirstHarmValue = 20.0;
        sh.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
        for (int k = 1; k <= q; k++) {
            for (int l = 0; l <= k; l++) {
                sh.SetHarmonic(k, l, GenerateRandomHarmonic(l));
            }
        }
        
        InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(sh);
        double[][] spatial = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
        double[] spatialVec = new double[latNum*lonNum];
        int counter = 0;
        for (int i = 0; i < spatial.length; i++) {
            for (int j = 0; j < spatial[0].length; j++) {
                spatial[i][j] = spatial[i][j] * Math.random();
                spatialVec[counter] = spatial[i][j];
                counter++;
            }
        }
        DiscreteSphericalTransform dst = new DiscreteSphericalTransform(spatial, q, true);
        sh = dst.GetSpectra();
        
        // Generate PCA
        SphericalHarmonic[] timeseries = new SphericalHarmonic[]{ sh };
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
               
        CalculateHarmonic harmonic = new CalculateHarmonic(q, false, eigenVectors, eigenValues);
        SphericalHarmonic shRebuilt = new SphericalHarmonic(q);
        for (int k = 0; k <= q; k++) {
            for (int l = 0; l <= k; l++) {
                Pair<Complex, double[]> values = harmonic.Process(k, l, lat, lon, spatialVec);
                Complex S_kl = values.getValue0();
                double[] weights = values.getValue1();
                shRebuilt.SetHarmonic(k, l, S_kl);
            }
        }
        
        // Make sure first harmonic is within 10% of original value
        double originalFirstHarmonicValue = sh.GetHarmonic(0, 0).getReal();
        double rebuiltFirstHarmonicValue  = shRebuilt.GetHarmonic(0, 0).getReal();
        
        ComplexArray.Print(sh.GetHalfSpectral());
        ComplexArray.Print(shRebuilt.GetHalfSpectral());
        
        double ratio = rebuiltFirstHarmonicValue/originalFirstHarmonicValue;
        if (Math.abs(ratio-1.0) > 0.10 ) {
            Assert.fail("Error too large between originala and rebuilt");
        }
        
        // Get the area
        double[] gauslat = RadiansToLatitude(DoubleArray.Add(GetLatitudeCoordinates(latNum), -(Math.PI/2.0)));
        double[] gauslon = RadiansToLongitude(DoubleArray.Add(GetLongitudeCoordinates(lonNum), - Math.PI));
        AreasForGrid gausAreasForGrid = new AreasForGrid(gauslat, gauslon, 1.0);
        double[][] areaFraction = DoubleArray.Multiply(gausAreasForGrid.GetAreas(), 1.0/(Math.PI*4.0));
        
        // Original Area
        InvDiscreteSphericalTransform invOrigSh = new InvDiscreteSphericalTransform(sh);
        double[][] orig = invOrigSh.ProcessGaussianDoubleArray(latNum, lonNum);
        double origValue = DoubleArray.SumArray(DoubleArray.Multiply(orig, areaFraction));
        
        // Rebuilt Area
        InvDiscreteSphericalTransform invShRebuilt = new InvDiscreteSphericalTransform(shRebuilt);
        double[][] rebuilt = invShRebuilt.ProcessGaussianDoubleArray(latNum, lonNum);
        double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
        
        System.out.println("Original: " + origValue + ", Rebuilt: " + value);
    }
    
    @Test
    public void testIncreasingFirstHarmonic() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        int q_trunc = 0;
        int latNum = q+1;
        int lonNum = q*2;
        int timePoints = 30;
        
        // Get Lat/Lon & gridbox areas
        double[] lat = RadiansToLatitude(GetLatitudeCoordinates(latNum));
        double[] lon = RadiansToLongitude(GetLongitudeCoordinates(lonNum));
        AreasForGrid areasForGrid = new AreasForGrid(lat, lon, 1.0);
        double[][] areaFraction = DoubleArray.Multiply(areasForGrid.GetAreas(), 1.0/(Math.PI*4.0));
        
        // Generate the baseline
        SphericalHarmonic[] timeseries = new SphericalHarmonic[timePoints];
        double[] origAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            SphericalHarmonic sh = new SphericalHarmonic(q);
            SphericalHarmonic sh_trunc = new SphericalHarmonic(q_trunc);
            double originalFirstHarmValue = 20.0 + 1.0*Math.pow(i,2);//*Math.abs(Math.random());
            sh.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            sh_trunc.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            for (int k = 1; k <= q; k++) {
                for (int l = 0; l <= k; l++) {
                    Complex randHarm = GenerateRandomHarmonic(l);
                    sh.SetHarmonic(k, l, randHarm);
                    if (k <= q_trunc) {
                        sh_trunc.SetHarmonic(k, l, randHarm);
                    }
                }
            }
            timeseries[i] = sh_trunc;
            
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(sh);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            origAverage[i] = value;
        }
        
        // Generate PCA
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
        
        // Recreate the data
        CalculateHarmonic harmonic = new CalculateHarmonic(q, false, eigenVectors, eigenValues);
        SphericalHarmonic[] rebuiltTimeseries = new SphericalHarmonic[timePoints];
        for (int i = 0; i < timePoints; i++) {
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(timeseries[i]);
            
            // Generate random lat/lon coordinates  
            double[] spatial = invSh.ProcessGaussian(latNum, lonNum);
            Pair<double[], double[]> coords = InvDiscreteSphericalTransform.GenerateCoordinatePoints(latNum, lonNum);
            double[] currLat = coords.getValue0();
            double[] currLon = coords.getValue1();
            SphericalHarmonic shRebuilt = new SphericalHarmonic(q_trunc);
            for (int k = 0; k <= q_trunc; k++) {
                for (int l = 0; l <= k; l++) {
                    Pair<Complex, double[]> values = harmonic.Process(k, l, currLat, currLon, spatial);
                    Complex S_kl = values.getValue0();
                    double[] weights = values.getValue1();
                    shRebuilt.SetHarmonic(k, l, S_kl);
                }
            }
            rebuiltTimeseries[i] = shRebuilt;
        }
        
        // get the rebuilt
        double[] rebuiltAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(rebuiltTimeseries[i]);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            rebuiltAverage[i] = value;
        }
        
        double[][] results = DoubleArray.cbind(origAverage, rebuiltAverage);
        System.out.println("Results: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        DoubleArray.Print(results);
    }
    
    @Test
    public void testIncreasingFirstHarmonicRandomPoints() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        int q_trunc = 0;
        int latNum = q+1;
        int lonNum = q*2;
        int timePoints = 50;
        
        int rebuiltNumPoints = 10;
        Random rand = new Random();
        rand.setSeed(12345);
        
        // Get Lat/Lon & gridbox areas
        double[] lat = RadiansToLatitude(GetLatitudeCoordinates(latNum));
        double[] lon = RadiansToLongitude(GetLongitudeCoordinates(lonNum));
        AreasForGrid areasForGrid = new AreasForGrid(lat, lon, 1.0);
        double[][] areaFraction = DoubleArray.Multiply(areasForGrid.GetAreas(), 1.0/(Math.PI*4.0));
        
        // Generate the baseline
        SphericalHarmonic[] timeseries_trunc = new SphericalHarmonic[timePoints];
        SphericalHarmonic[] timeseries = new SphericalHarmonic[timePoints];
        double[] origAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            SphericalHarmonic sh = new SphericalHarmonic(q);
            SphericalHarmonic sh_trunc = new SphericalHarmonic(q_trunc);
            double originalFirstHarmValue = 20.0 + 1.0*Math.pow(i,2)*Math.abs(rand.nextDouble());
            sh.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            sh_trunc.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            for (int k = 1; k <= q; k++) {
                for (int l = 0; l <= k; l++) {
                    //Complex randHarm = GenerateRandomHarmonic(l);
                    Complex randHarm = l== 0 ? new Complex(Math.pow(i,1.2), 0.0) : new Complex(Math.pow(i,1.5), i*3);
                    randHarm = randHarm.add(GenerateRandomHarmonic(l, rand));
                    sh.SetHarmonic(k, l, randHarm);
                    if (k<= q_trunc) {
                        sh_trunc.SetHarmonic(k, l, randHarm);
                    }
                }
            }
            timeseries[i] = sh;
            timeseries_trunc[i] = sh_trunc;
            
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(sh);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            origAverage[i] = value;
        }
        
        // Generate PCA
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries_trunc);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
        
        // Recreate the data
        CalculateHarmonic harmonic = new CalculateHarmonic(q_trunc, false, eigenVectors, eigenValues);
        SphericalHarmonic[] rebuiltTimeseries = new SphericalHarmonic[timePoints];
        for (int i = 0; i < timePoints; i++) {
              
            double[] latPoints = Utilities.randomLatitudeRadians(rebuiltNumPoints, rand);
            double[] lonPoints = Utilities.randomLongitudeRadians(rebuiltNumPoints, rand);
            
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(timeseries[i]);
            double[] spatial = invSh.ProcessPoints(latPoints, lonPoints);
            SphericalHarmonic shRebuilt = new SphericalHarmonic(q_trunc);
            for (int k = 0; k <= q_trunc; k++) {
                for (int l = 0; l <= k; l++) {
                    Pair<Complex, double[]> values = harmonic.Process(k, l, latPoints, lonPoints, spatial);
                    Complex S_kl = values.getValue0();
                    double[] weights = values.getValue1();
                    shRebuilt.SetHarmonic(k, l, S_kl);
                }
            }
            rebuiltTimeseries[i] = shRebuilt;
        }
        
        // get the rebuilt
        double[] rebuiltAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(rebuiltTimeseries[i]);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            rebuiltAverage[i] = value;
        }
        
        double[][] results = DoubleArray.cbind(origAverage, rebuiltAverage);
        System.out.println("Results: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        DoubleArray.Print(results);
    }
    
    @Test
    public void testIncreasingFirstHarmonicGoodPoints() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        int q_trunc = 0;
        int latNum = q+1;
        int lonNum = q*2;
        int timePoints = 50;
        
        Random rand = new Random();
        rand.setSeed(12345);
        
        // Get Lat/Lon & gridbox areas
        double[] lat = RadiansToLatitude(GetLatitudeCoordinates(latNum));
        double[] lon = RadiansToLongitude(GetLongitudeCoordinates(lonNum));
        AreasForGrid areasForGrid = new AreasForGrid(lat, lon, 1.0);
        double[][] areaFraction = DoubleArray.Multiply(areasForGrid.GetAreas(), 1.0/(Math.PI*4.0));
        
        // Generate the baseline
        SphericalHarmonic[] timeseries_trunc = new SphericalHarmonic[timePoints];
        SphericalHarmonic[] timeseries = new SphericalHarmonic[timePoints];
        double[] origAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            SphericalHarmonic sh = new SphericalHarmonic(q);
            SphericalHarmonic sh_trunc = new SphericalHarmonic(q_trunc);
            double originalFirstHarmValue = 20.0 + 1.0*Math.pow(i,2)*Math.abs(rand.nextDouble());
            sh.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            sh_trunc.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
            for (int k = 1; k <= q; k++) {
                for (int l = 0; l <= k; l++) {
                    //Complex randHarm = GenerateRandomHarmonic(l);
                    Complex randHarm = l== 0 ? new Complex(Math.pow(i,1.2), 0.0) : new Complex(Math.pow(i,1.5), i*3);
                    randHarm = randHarm.add(GenerateRandomHarmonic(l, rand));
                    sh.SetHarmonic(k, l, randHarm);
                    if (k<= q_trunc) {
                        sh_trunc.SetHarmonic(k, l, randHarm);
                    }
                }
            }
            timeseries[i] = sh;
            timeseries_trunc[i] = sh_trunc;
            
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(sh);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            origAverage[i] = value;
        }
        
        // Generate PCA
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries_trunc);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
        
        
        double[] latPoints = new double[latNum*lonNum];
        double[] lonPoints = new double[latNum*lonNum];


        int counter = 0;
        for (int i = 0; i < lat.length; i++) {
            for (int j = 0; j < lon.length; j++) {
                latPoints[counter] = Utilities.LatitudeToRadians(lat[i]);
                lonPoints[counter] = Utilities.LongitudeToRadians(lon[j]);
                counter++;
            }
        }
        
        
        // Recreate the data
        CalculateHarmonic harmonic = new CalculateHarmonic(q_trunc, false, eigenVectors, eigenValues);
        SphericalHarmonic[] rebuiltTimeseries = new SphericalHarmonic[timePoints];
        for (int i = 0; i < timePoints; i++) {     
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(timeseries[i]);
            double[] spatial = invSh.ProcessPoints(latPoints, lonPoints);
            SphericalHarmonic shRebuilt = new SphericalHarmonic(q_trunc);
            for (int k = 0; k <= q_trunc; k++) {
                for (int l = 0; l <= k; l++) {
                    Pair<Complex, double[]> values = harmonic.Process(k, l, latPoints, lonPoints, spatial);
                    Complex S_kl = values.getValue0();
                    double[] weights = values.getValue1();
                    shRebuilt.SetHarmonic(k, l, S_kl);
                }
            }
            rebuiltTimeseries[i] = shRebuilt;
        }
        
        // get the rebuilt
        double[] rebuiltAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(rebuiltTimeseries[i]);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            rebuiltAverage[i] = value;
        }
        
        double[][] results = DoubleArray.cbind(origAverage, rebuiltAverage);
        System.out.println("Results: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        DoubleArray.Print(results);
    }
    
    /**
     * Test of Process method, of class CalculateHarmonic.
     */
    @Test
    public void testStrongFirstHarmRandomOther() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        int q_truc = 0;
        int latNum = q+1;
        int lonNum = 2*q;
        int timePoints = 50;
        
        boolean useGriddedPredictors = false;
        int numRandPoints = 100; //latNum*lonNum;
        
        Random rand = new Random();
        rand.setSeed(12345);
        
        // Get Lat/Lon & gridbox areas
        double[][] areaFraction = null;
        {
            double[] lat = RadiansToLatitude(GetLatitudeCoordinates(latNum));
            double[] lon = RadiansToLongitude(GetLongitudeCoordinates(lonNum));
            AreasForGrid areasForGrid = new AreasForGrid(lat, lon, 1.0);
            areaFraction = DoubleArray.Multiply(areasForGrid.GetAreas(), 1.0/(Math.PI*4.0));
        }
        
        Map<Integer, double[][]> originalMap = new HashMap<Integer, double[][]>();
        SphericalHarmonic[] timeseries = new SphericalHarmonic[timePoints];
        double[] origAverage = new double[timePoints];
        for (int i = 0; i < timePoints; i++) {
            SphericalHarmonic sh = new SphericalHarmonic(q);
            for (int k = 0; k <= q; k++) {
                for (int l = 0; l <= k; l++) {
                    sh.SetHarmonic(k, l, GenerateRandomHarmonic(l, rand));
                }
            }
            timeseries[i] = sh;
            
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(sh);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            originalMap.put(i, rebuilt);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            origAverage[i] = value;

        }

        // Generate PCA
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
        double[] rebuiltAverage = new double[timePoints];
        
        // Rebuild the data
        Map<Integer, double[][]> rebuiltMap = new HashMap<Integer, double[][]>();
        SphericalHarmonic[] timeseriesRebuilt = new SphericalHarmonic[timePoints];
        for (int i = 0; i < timePoints; i++) {
            
            double[] spatial = null;
            double[] lat = null;
            double[] lon = null;
            if (useGriddedPredictors) {
                InvDiscreteSphericalTransform invShTrans = new InvDiscreteSphericalTransform(timeseries[i]);
                Pair<double[], double[]> coordinates = invShTrans.GenerateCoordinatePoints(latNum, lonNum);
                spatial = invShTrans.ProcessGaussian(latNum, lonNum);
                lat = coordinates.getValue0();
                lon = coordinates.getValue1();
            } else {
                lat = Utilities.randomLatitudeRadians(numRandPoints, rand);
                lon = Utilities.randomLongitudeRadians(numRandPoints, rand);
                InvDiscreteSphericalTransform invShTrans = new InvDiscreteSphericalTransform(timeseries[i]);
                spatial = invShTrans.ProcessPoints(lat, lon);
            }
            
            CalculateHarmonic harmonic = new CalculateHarmonic(q, false, eigenVectors, eigenValues);
            SphericalHarmonic shRebuilt = new SphericalHarmonic(q);
            for (int k = 0; k <= q_truc; k++) {
                for (int l = 0; l <= k; l++) {
                    Pair<Complex, double[]> values = harmonic.Process(k, l, lat, lon, spatial);
                    Complex S_kl = values.getValue0();
                    double[] weights = values.getValue1();
                    shRebuilt.SetHarmonic(k, l, S_kl);
                }
            }
            
            timeseriesRebuilt[i] = shRebuilt;
            InvDiscreteSphericalTransform invSh = new InvDiscreteSphericalTransform(shRebuilt);
            double[][] rebuilt = invSh.ProcessGaussianDoubleArray(latNum, lonNum);
            rebuiltMap.put(i, rebuilt);
            double value = DoubleArray.SumArray(DoubleArray.Multiply(rebuilt, areaFraction));
            rebuiltAverage[i] = value;
        }
        
        double[][] comparisons = DoubleArray.cbind(origAverage, rebuiltAverage);
        
        // Print before after of the overall value
        DoubleArray.Print(comparisons);
        
        // Print before after of the full harmonic at time = 0
        timeseries[0].PrintHarmonic();
        timeseriesRebuilt[0].PrintHarmonic();
        
        // Print before after of the map at time = 0
        double[][] mapOrig = originalMap.get(0);
        double[][] mapRebu = rebuiltMap.get(0);
        double[][] diff = DoubleArray.Add(mapOrig, DoubleArray.Multiply(mapRebu, -1.0));
        double sse = DoubleArray.SumArray(DoubleArray.Power(diff, 2.0));
        
        DoubleArray.Print(mapOrig);
        DoubleArray.Print(mapRebu);
        System.out.println("SSE: " + sse);
        
        
        
        
    }
    
    private static Triplet<Complex[][], Complex[], double[]> generatePca(SphericalHarmonic[] timeseriesSpherical) throws Exception {
        
        int timeseriesLength = timeseriesSpherical.length;
        int numOfQpoints = timeseriesSpherical[0].GetFullCompressedSpectra().length;
        Complex[][] qRealizations = new Complex[numOfQpoints][timeseriesLength];
        
        for (int j = 0; j < timeseriesLength; j++) {
            Complex[] spectral = timeseriesSpherical[j].GetFullCompressedSpectra();        
            for (int i = 0; i < numOfQpoints; i++) {
                qRealizations[i][j] = spectral[i];
            }
        }
        
        ComplexDoubleMatrix qRealizationsMatrix = ApacheMath3ToJblas(qRealizations);
        ComplexDoubleMatrix qRealizations_trans = qRealizationsMatrix.transpose().conj();
        ComplexDoubleMatrix R_hat_s = (qRealizationsMatrix.mmul(qRealizations_trans)).mul(1/(timeseriesLength + 0.0));
        PcaCovJBlas pca = new PcaCovJBlas(R_hat_s);

        // Get the eigen values & eigen vectors
        Complex[] eigenValues = pca.GetEigenValuesMath3();
        Complex[][] eigenVectors = pca.GetEigenVectorsMath3();                
        double[] varExplained = pca.GetSumOfVarianceExplained();
        
        // Truncate to variance explained
        Triplet<Complex[][], Complex[], double[]> eigens = CutoffVarExplained(0.9, eigenVectors, eigenValues, varExplained);  
        return eigens;
    }
    
    /**
     * Test of Process method, of class CalculateHarmonic.
     */
    @Test
    public void testAngellKorshoverNetworkLocations() throws Exception {
        System.out.println("Testing: " + Thread.currentThread().getStackTrace()[1].getMethodName());
        int q = 5;
        
        SphericalHarmonic sh = new SphericalHarmonic(q);
        double originalFirstHarmValue = 20.0;
        sh.SetHarmonic(0, 0, new Complex(originalFirstHarmValue, 0.0));
        InvDiscreteSphericalTransform invShTrans = new InvDiscreteSphericalTransform(sh);
        for (int k = 1; k <= q; k++) {
            for (int l = 0; l <= k; l++) {
                sh.SetHarmonic(k, l, GenerateRandomHarmonic(l));
            }
        }
        
        AngellKorshoverNetwork akNetwork = new AngellKorshoverNetwork();
        double[] lat = Utilities.LatitudeToRadians(akNetwork.GetLats());
        double[] lon = Utilities.LongitudeToRadians(akNetwork.GetLons());
        double[] value = invShTrans.ProcessPoints(lat, lon);
                
        // Generate PCA
        SphericalHarmonic[] timeseries = new SphericalHarmonic[]{ sh };
        Triplet<Complex[][], Complex[], double[]> eigens = generatePca(timeseries);
        Complex[][] eigenVectors = eigens.getValue0();
        Complex[] eigenValues = eigens.getValue1();
        
        CalculateHarmonic harmonic = new CalculateHarmonic(q, false, eigenVectors, eigenValues);
        
        SphericalHarmonic shRebuilt = new SphericalHarmonic(q);
        for (int k = 0; k <= q; k++) {
            for (int l = 0; l <= k; l++) {
                Pair<Complex, double[]> currHarm = harmonic.Process(k, l, lat, lon, value);
                Complex S_kl = currHarm.getValue0();
                double[] weights = currHarm.getValue1();
                shRebuilt.SetHarmonic(k, l, S_kl);
            }
        }
        
        // Make sure first harmonic is within 30% of original value
        double rebuiltFirstHarmonicValue = shRebuilt.GetHarmonic(0, 0).getReal();
        double ratio = rebuiltFirstHarmonicValue/originalFirstHarmValue;
        if (Math.abs(ratio-1.0) > 0.30 ) {
            Assert.fail("Error too large between originala and rebuilt");
        }
        
        sh.PrintHarmonic();
        shRebuilt.PrintHarmonic();
    }
    
    
    
}
