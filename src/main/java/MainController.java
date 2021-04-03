import Data.Dataset;
import FeaturesExtractors.ByteFrequenciesFeatures;
import FeaturesExtractors.DividingStringsFeatures;
import Maps.basicMap;
import com.squareup.javapoet.ClassName;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static Data.Dataset.*;

public class MainController {

    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    // DAEMON's HARD-CODED Strings
    private static String TEST_SET = "Test";
    private static String TRAINING_SET = "Train";
    private static String EXPERIMENT_PATH = "/DATA/Ron/Datasets/Drebin";
    private static String ORIGINAL_DATASET_PATH = EXPERIMENT_PATH + "/DrebinDataset";

    // Important Paths
    private static String EXPERIMENT_DATASET_PATH = EXPERIMENT_PATH + "/CurrentDrebinDataset";
    private static String DATASET_LABELS_PATH = EXPERIMENT_PATH + "/Labels.csv";
    private static String NAMES_OF_FAMILIES_IN_DATASET = EXPERIMENT_PATH + "/Families.csv";
    private static String DATASET_OUTPUT_FEATURES = EXPERIMENT_PATH + "/Features";
    private static String EXTRACTIONS_FOR_FUTURE_RE_BUILDING =  EXPERIMENT_PATH + "/Results";

    public static void main(String[] args){
        log.info("Started Building a DAEMON Classifier");

        double TRAIN_TEST_RATE = 0.3;
        int BYTE_SEQUENCE_MAX_LENGTH = 64; // (BYTE_SEQUENCE_MAX_LENGTH/2)=Maximum number of bytes per string-feature
        int MAX_PAIRWISE_FEATURES = 50000; // The maximum number of features (after stage 3)

        Map<String, String> datasetUsed = createDatasetFromLabels(DATASET_LABELS_PATH,
                NAMES_OF_FAMILIES_IN_DATASET, ORIGINAL_DATASET_PATH, EXPERIMENT_DATASET_PATH);

        Map<String, Map<String,String>> dividedDataSet = divideDataSet(datasetUsed, TRAIN_TEST_RATE);
        Map<String, String> test = dividedDataSet.get(TEST_SET);
        Map<String, String> train = dividedDataSet.get(TRAINING_SET);

        Dataset dataset = new Dataset(EXPERIMENT_DATASET_PATH, train, test, EXTRACTIONS_FOR_FUTURE_RE_BUILDING,
                BYTE_SEQUENCE_MAX_LENGTH);

        dataset.completeInitialStages(); //Extract all strings from the malicious families (until stage-2, included)

        log.info("Finished Extraction Of Pair-Wise Separating Strings From Dataset With: " +
                String.valueOf(dataset.getDataSetSize()) + " Files!");

        DividingStringsFeatures dividingFeatures = new DividingStringsFeatures(DATASET_OUTPUT_FEATURES,
                MAX_PAIRWISE_FEATURES, dataset);

        dividingFeatures.computeFeatures();
        dividingFeatures.clearCombinationMaps();

        ByteFrequenciesFeatures byteFrequenciesFeatures = new ByteFrequenciesFeatures(DATASET_OUTPUT_FEATURES, dataset);
        byteFrequenciesFeatures.computeFeatures();
        dataset.clearMaps();

        log.info("Ended extraction of stages 1-4 in DAEMON's algorithm  (feature-vector computation)");
    }

    private static void copyFolder(File source, File destination) //External
    {
        if (source.isDirectory())
        {
            if (!destination.exists())
            {
                destination.mkdirs();
            }

            String files[] = source.list();

            if (files != null) {
                for (String file : files)
                {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);

                    copyFolder(srcFile, destFile);
                }
            }
        }
        else
        {
            InputStream in = null;
            OutputStream out = null;

            try
            {
                in = new FileInputStream(source);
                out = new FileOutputStream(destination);

                byte[] buffer = new byte[1024];

                int length;
                while ((length = in.read(buffer)) > 0)
                {
                    out.write(buffer, 0, length);
                }
            }
            catch (Exception e)
            {
                try
                {
                    if (in != null) {
                        in.close();
                    }
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }

                try
                {
                    if (out != null) {
                        out.close();
                    }
                }
                catch (IOException e1)
                {
                    e1.printStackTrace();
                }
            }
        }
    }

    private static HashSet<String> getWantedFamilies(String requiredFamiliesFile){
        String line, cvsSplitBy = ",";
        HashSet<String> families = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(requiredFamiliesFile))) {
            while ((line = br.readLine()) != null) {
                // use comma as separator
                String[] values = line.split(cvsSplitBy);
                families.add(values[0].replaceAll("\"", ""));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("Extracted Families!");
        log.info(families.toString());
        return families;
    }

    private static Map<String, Map<String,String>> divideDataSet(Map<String, String> createdSubDataSet,
                                                                 double TRAIN_TEST_RATE){
        HashMap<String, Map<String, String>> toReturn = new HashMap<>();
        HashMap<String, String> train = new HashMap<>();
        HashMap<String, String> test = new HashMap<>();
        ConcurrentMap<CharSequence, String> previousTrainSet, previousTestSet;

        //Added case: load existing selection future re-training/model re-building
        File train_map_file = new File(EXTRACTIONS_FOR_FUTURE_RE_BUILDING + TRAIN_FOLDER + "/train.txt");
        File test_map_file = new File(EXTRACTIONS_FOR_FUTURE_RE_BUILDING + TEST_FOLDER + "/test.rxt");

        if(train_map_file.exists() && test_map_file.exists()){
            basicMap testMapReader = new basicMap(test_map_file.getAbsolutePath(), SET_APPROX_SIZE);
            previousTestSet = testMapReader.getSaveReadMap();
            basicMap trainMapReader = new basicMap(train_map_file.getAbsolutePath(), SET_APPROX_SIZE);
            previousTrainSet = trainMapReader.getSaveReadMap();
            log.info("Finished Extracting Previous Test & Train Sets");

            for (Map.Entry<CharSequence, String> entry : previousTestSet.entrySet())
                test.put(String.valueOf(entry.getKey()), entry.getValue());

            for (Map.Entry<CharSequence, String> entry : previousTrainSet.entrySet())
                train.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        else {
            int testSize = (int) (createdSubDataSet.size() * TRAIN_TEST_RATE);
            int trainSize = createdSubDataSet.size() - testSize;

            List<Map.Entry<String, String>> list = new ArrayList<>(createdSubDataSet.entrySet());
            Collections.shuffle(list);

            for (Map.Entry<String, String> entry : list) {
                if (test.size() < testSize)
                    test.put(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<String, String> entry : list) {
                if (train.size() < trainSize && !test.containsKey(entry.getKey()))
                    train.put(entry.getKey(), entry.getValue());
            }

            log.info("Finished Building Test & Train Sets");
        }

        toReturn.put(TRAINING_SET, train);
        toReturn.put(TEST_SET, test);
        log.info("Test Size: " + test.size());
        log.info("Train Size: " + train.size());
        return toReturn;
    }

    private static Map<String, String> createDatasetFromLabels(String prevLabelsPath, String interestingFamiliesPath,
                                                               String dataSetPath, String resultPath){
        createAllDirsOfPath(resultPath);
        Map<CharSequence, String> prevLabels = getLabelsFromCSV(prevLabelsPath, prevLabelsPath);
        HashSet<String> reducedFamiliesMap = getWantedFamilies(interestingFamiliesPath);
        Map<String, String> reducedLabelsMap = new HashMap<>();
        File[] dataSetFiles = new File(dataSetPath).listFiles();
        if(dataSetFiles!=null){
            for(File maliciousSampleDir : dataSetFiles){
                if(maliciousSampleDir.isDirectory()){
                    String fileName = maliciousSampleDir.getName();
                    if(prevLabels.containsKey(fileName)){
                        if(reducedFamiliesMap.contains(prevLabels.get(fileName))){
                            reducedLabelsMap.put(fileName, prevLabels.get(fileName));
                            copyFolder(maliciousSampleDir, new File(resultPath+"/"+fileName));
                        }
                    }
                }
            }
        }

        log.info("Finished Building Fitting Labels Map");
        log.info("New Labels Map Has Size: " + reducedLabelsMap.size());
        return reducedLabelsMap;
    }
}
