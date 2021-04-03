package Deployment;

import Data.Dataset;
import Data.FileActions;
import FeaturesExtractors.FeaturesCollection;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.opencsv.CSVWriter;
import com.squareup.javapoet.ClassName;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static Data.Dataset.buildTrainOrTestSizes;
import static Data.Dataset.createAllDirsOfPath;
import static Data.FileActions.getAndroidFileContent;
import static Data.FileActions.getSampleName;
import static FeaturesExtractors.ByteFrequenciesFeatures.isInteger;
import static FeaturesExtractors.DividingStringsFeatures.getSortedKeys;

public class DepolymentFeatureExtraction extends FeaturesCollection {

    private final ConcurrentMap<CharSequence, String> testSet;
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    private ConcurrentMap<CharSequence, String[]> testFrequencyMap;
    private ConcurrentMap<CharSequence, String[]> testNGramsMap;
    private String finalTestFeaturesPath;
    private String[] allNGrams;

    DepolymentFeatureExtraction(String featureCollectionPath, Dataset toExtractFrom,
                                String finalTestFeaturesPath) {
        super(featureCollectionPath, toExtractFrom);
        this.testFrequencyMap = new ConcurrentHashMap<>();
        this.testNGramsMap = new ConcurrentHashMap<>();
        this.testSet = this.getToExtractFrom().getTestSet();
        this.finalTestFeaturesPath = finalTestFeaturesPath;
        this.allNGrams = extractAllNGrams();
    }

    private String[] extractAllNGrams() {
        String line;
        String cvsSplitBy = ",";
        Set<String> allNGrams = new HashSet<>();

        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(this.finalTestFeaturesPath))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                String[] values = line.split(cvsSplitBy);
                if (count > 0) //values[0] => feature number, values[1] => feature
                    allNGrams.add(values[1].replaceAll("Android - ", "").replaceAll("\"", ""));
                count++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return  (String[]) allNGrams.toArray();
    }

    private void build1GramsDataFrameHeader() {
        ArrayList<String> header = this.getDataFrameHeader();
        int num_of_characters = 16;
        for(int i=0; i<num_of_characters*num_of_characters; i++){
            if(i<256){
                header.add(Integer.toHexString(i).toUpperCase());
            }
        }
        header.add("FileName");
    }

    private void buildNGramsDataFrameHeader(){
        for(String s: allNGrams){
            this.getDataFrameHeader().add(s);
        }
        this.getDataFrameHeader().add("FileName");
        this.getDataFrameHeader().add("FamilyName");
    }

    //Part of the classification phase -> calculated using a single core! (easily scalable)
    @Override
    public void computeFeatures() {
        log.info("Started Calculating Features of Test Set");
        computeTest1Grams();
        computeTestNGrams(); 
    }

    private void computeTestNGrams() {
        log.info("Started Calculating N-Grams of Test Set");

        String r_EXTENSION = "/Results";
        String resultPath = this.getFeatureCollectionPath()+ r_EXTENSION;
        createAllDirsOfPath(resultPath);
        buildNGramsDataFrameHeader();
        FileWriter outputStringFile;

        try {
            outputStringFile = new FileWriter(resultPath + "/testStringsAndCounts.csv");
            CSVWriter writer = new CSVWriter(outputStringFile);
            File dir = new File(this.getToExtractFrom().getDataSetPath());
            File[] directoryListing = dir.listFiles();

            if (directoryListing != null) {
                ConcurrentMap<CharSequence, Integer> family_sizes = buildTrainOrTestSizes(testSet);
                log.info(Arrays.toString(family_sizes.entrySet().toArray()));

                for (File maliciousSample : directoryListing) {
                    String sampleName = getSampleName(maliciousSample.getName());
                    String familyName;
                    if (testSet.containsKey(sampleName)) {
                        familyName = testSet.get(sampleName);
                        TreeMap<String, String> map = new TreeMap<>();
                        for (String key : allNGrams)
                            map.put(key, key);
                        AhoCorasickDoubleArrayTrie<String> ourTree = new AhoCorasickDoubleArrayTrie<>();
                        ourTree.build(map);
                        final String fileContent = FileActions.getAndroidFileContent(maliciousSample);
                        ConcurrentMap<String, Integer> hitsMap = new ConcurrentHashMap<>();
                        List<AhoCorasickDoubleArrayTrie.Hit<String>> hits = ourTree.parseText(fileContent);
                        for (AhoCorasickDoubleArrayTrie.Hit<String> hit : hits) {
                            hitsMap.putIfAbsent(hit.value, 1);
                            hitsMap.compute(hit.value, (k, v) -> (v != null) ? v + 1 : 1);
                            String[] toPrint = new String[this.getDataFrameHeader().size()];
                            for (int i = 0; i < allNGrams.length; i++) {
                                if (hitsMap.containsKey(allNGrams[i]))
                                    toPrint[i] = (hitsMap.get(allNGrams[i])).toString();
                                else
                                    toPrint[i] = "0";
                            }
                            toPrint[toPrint.length - 1] = familyName;
                            String fileName = getSampleName(maliciousSample.getName());
                            toPrint[toPrint.length - 2] = fileName;
                            testNGramsMap.put(fileName, toPrint);
                            finishWriting(testNGramsMap, writer);
                            log.info("Finished Extracting Hits From Test File: " + fileName);
                        }
                    }
                }
            }
            log.info("Finished Calculating N-Grams of Test Set");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void finishWriting(Map<CharSequence, String[]> featuresMap, CSVWriter writer) {
        writeAllLines(featuresMap, writer);
        try {
            writer.flush();
        } catch (IOException e) {
            log.info("Failed flushing the writer of test strings");
            e.printStackTrace();
        }
        try {
            writer.close();
        } catch (IOException e) {
            log.info("Failed closing the writer of test strings");
            e.printStackTrace();
        }
    }

    private void writeAllLines(Map<CharSequence, String[]> featuresMap, CSVWriter writer) {
        List<String> sortedKeys = getSortedKeys(featuresMap.keySet());
        for(String s: sortedKeys){
            writer.writeNext(featuresMap.get(s));
        }
    }

    private void computeTest1Grams() {
        log.info("Started Calculating 1-Grams of Test Set");
        build1GramsDataFrameHeader();
        FileWriter outputStringFile;

        int numTotal=0;

        try {
            outputStringFile = new FileWriter(this.getFeatureCollectionPath() + "/allTestBytes.csv");
            CSVWriter testWriter = new CSVWriter(outputStringFile);
            ConcurrentMap<CharSequence, String> testSet = this.getToExtractFrom().getTestSet();
            String[] firstLine = this.getDataFrameHeader().toArray(new String[0]);
            testWriter.writeNext(firstLine);
            File dir = new File(this.getToExtractFrom().getDataSetPath());
            File[] directoryListing = dir.listFiles();
            int num_of_characters = 16; //16 HEX Characters
            int freqSize = num_of_characters * num_of_characters;
            if (directoryListing != null) {
                for(File maliciousSample: directoryListing) {
                    String sampleName = getSampleName(maliciousSample.getName());
                    if (testSet.containsKey(sampleName)) {
                        int[] freq = new int[freqSize];
                        String[] realStrings = new String[freq.length + 1];
                        for (int i = 0; i < freq.length; i++)
                            realStrings[i] = "0";
                        realStrings[freq.length] = sampleName;
                        insertKey(sampleName, realStrings);

                        String fileBinaryString = getAndroidFileContent(new File(maliciousSample.getAbsolutePath()));
                        int len = fileBinaryString.length();

                        if (len > 0) {
                            getToExtractFrom().getEntropyCalculator().buildFrequencies(fileBinaryString, len, freq);
                            for (int i = 0; i < freq.length; i++) {
                                String res = String.valueOf(freq[i]);
                                realStrings[i] = res;
                            }
                            replaceOccursIfNeeded(sampleName, realStrings);
                        }

                        log.info("Finished Handling DataSetFile: " + sampleName);
                        numTotal++;
                    }
                }
            }

            log.info("Handled: " + String.valueOf(numTotal) + " Files Bytes!");
            log.info("Actually Handled: " + String.valueOf(testFrequencyMap.size()) + "Test Files");

            writeAllLines(testFrequencyMap, testWriter);
            testWriter.flush();
            testWriter.close();
            log.info("Finished Calculating All Byte Frequencies Features");

        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info("Finished Calculating 1-Grams of Test Set");
    }

    private void replaceOccursIfNeeded(String sampleName, String[] realStrings) {
        String[] infoInsertion = makeInfo(realStrings);
        if (testSet.containsKey(sampleName)) {
            testFrequencyMap.replace(sampleName, infoInsertion);
        }
    }

    private String[] makeInfo(String[] realStrings) {
        String[] toEnter = new String[realStrings.length];
        for(int i=0; i<realStrings.length-1; i++){
            if (isInteger(realStrings[i], 10)) {
                toEnter[i] = realStrings[i];
            }
            else {
                toEnter[i] = "0";
            }
        }
        toEnter[toEnter.length-1] = realStrings[realStrings.length-1];
        return toEnter;
    }

    private void insertKey(String sampleName, String[] realStrings) {
        if (testSet.containsKey(sampleName)) {
            testFrequencyMap.put(sampleName, realStrings);
        }
    }
}