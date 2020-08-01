package FeaturesExtractors;

import Data.Dataset;
import com.opencsv.CSVWriter;
import com.squareup.javapoet.ClassName;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static Data.FileActions.getAndroidFileContent;
import static Data.FileActions.getSampleName;
import static FeaturesExtractors.DividingStringsFeatures.getSortedStringKeys;

public class ByteFrequenciesFeatures extends FeaturesCollection {

    private final ConcurrentMap<CharSequence, String> trainSet;
    private ConcurrentMap<String, String[]> trainFrequencyList;
    private ConcurrentMap<String, String[]> testFrequencyList;
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    public ByteFrequenciesFeatures(String featureCollectionPath, Dataset toExtractFrom) {
        super(featureCollectionPath, toExtractFrom);
        this.trainFrequencyList = new ConcurrentHashMap<>();
        this.testFrequencyList = new ConcurrentHashMap<>();
        this.trainSet = this.getToExtractFrom().getStringsTrainSet();
    }

    @Override
    public void computeFeatures() {
        log.info("Started Calculating All Byte Frequencies Features");
        buildDataFrameHeader();
        FileWriter outputStringFile;
        FileWriter outputTestStringFile;
        int numTotal=0;
        try {
            outputStringFile = new FileWriter(this.getFeatureCollectionPath() + "/allTrainBytes.csv");
            CSVWriter trainWriter = new CSVWriter(outputStringFile);
            outputTestStringFile = new FileWriter(this.getFeatureCollectionPath() + "/allTestBytes.csv");
            CSVWriter testWriter = new CSVWriter(outputTestStringFile);
            ConcurrentMap<CharSequence, String> trainSet = this.getToExtractFrom().getStringsTrainSet();
            ConcurrentMap<CharSequence, String> testSet = this.getToExtractFrom().getTestSet();
            String[] firstLine = this.getDataFrameHeader().toArray(new String[0]);
            trainWriter.writeNext(firstLine);
            testWriter.writeNext(firstLine);
            File dir = new File(this.getToExtractFrom().getDataSetPath());
            File[] directoryListing = dir.listFiles();
            int num_of_characters = 16; //16 HEX Characters
            int freqSize = num_of_characters * num_of_characters;
            if (directoryListing != null) {
                for(File maliciousSample: directoryListing) {
                    String sampleName = getSampleName(maliciousSample.getName());
                    if (trainSet.containsKey(sampleName) || testSet.containsKey(sampleName)) {
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
            int size = (trainFrequencyList.size() +  testFrequencyList.size());
            log.info("Actually Handled: " + String.valueOf(trainFrequencyList.size()) + " Train Files, " +
                    String.valueOf(testFrequencyList.size()) + " Test Files, Total Of: " + String.valueOf(size) + " Files!");

            writeAllLines(trainFrequencyList, trainWriter);
            writeAllLines(testFrequencyList, testWriter);
            trainWriter.flush();
            trainWriter.close();
            testWriter.flush();
            testWriter.close();
            log.info("Finished Calculating All Byte Frequencies Features");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void insertKey(String sampleName, String[] realStrings) {
        if (trainSet.containsKey(sampleName)) {
            trainFrequencyList.put(sampleName, realStrings);
        } else {
            testFrequencyList.put(sampleName, realStrings);
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

    private void replaceOccursIfNeeded(String sampleName, String[] realStrings) {
        String[] infoInsertion = makeInfo(realStrings);
        if (trainSet.containsKey(sampleName)) {
            trainFrequencyList.replace(sampleName, infoInsertion);
        } else {
            testFrequencyList.replace(sampleName, infoInsertion);
        }
    }


    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }

    static void writeAllLines(ConcurrentMap<String, String[]> frequencyList, CSVWriter writer) {
        List<String> sortedKeys = getSortedStringKeys(frequencyList.keySet());
        List<String[]> actualFrequencyList = new LinkedList<>();
        for(String s: sortedKeys) {
            actualFrequencyList.add(frequencyList.get(s));
        }
        writer.writeAll(actualFrequencyList);
    }

    private void buildDataFrameHeader() {
        ArrayList<String> header = this.getDataFrameHeader();
        int num_of_characters = 16;
        for(int i=0; i<num_of_characters*num_of_characters; i++){
            if(i<256){
                header.add("Android - " + Integer.toHexString(i).toUpperCase());
            }
        }
        header.add("FileName");
    }
}
