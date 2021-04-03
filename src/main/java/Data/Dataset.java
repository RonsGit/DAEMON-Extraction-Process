package Data;

import Entropy.CalculateEntropy;
import Maps.basicMap;
import Maps.integerMap;
import com.squareup.javapoet.ClassName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static Data.FileActions.*;

public class Dataset {

    private static long size = Math.floorDiv((long) 10*Integer.MAX_VALUE, 13);

    // Important Public Fields
    public static String TRAIN_FOLDER = "/trainMap";
    public static String TEST_FOLDER = "/testMap";
    public static int SET_APPROX_SIZE =5000;

    //Private Fields
    private String dataSetPath, extractionPath;
    private int dataLength;
    private ConcurrentMap<String, Double> entropyRanges;
    private ConcurrentMap<CharSequence, String> trainSet, testSet;
    private ConcurrentMap<String, integerMap> familiesStrings;
    private ConcurrentMap<String, basicMap> datasetSetsSaveReadMaps;
    private integerMap familyIterationMap;
    private CalculateEntropy entropyRange;
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    //Constructors

    public Dataset(String dataSetPath, Map<String, String> train, Map<String, String> test, String extractionPath,
                   int dataLength){
        this.dataSetPath = dataSetPath;
        log.info("Entered: " + String.valueOf(train.size()+test.size()) + " Files To Mapping");
        this.extractionPath = extractionPath;
        this.dataLength = dataLength;
        this.familiesStrings = new ConcurrentHashMap<>();
        this.datasetSetsSaveReadMaps = new ConcurrentHashMap<>();
        log.info("Created An Iteration Map Of: " + String.valueOf(size) + " Entries");
        String KEY_EXAMPLE = "01jsnpXSAlgw6aPeDxrU01j01jsnpXSAlgwj01jsnpXSAlgwjAlgwj";
        this.familyIterationMap = new integerMap("", size, KEY_EXAMPLE);
        buildDirs(train);
        buildTrainTestSets(train, test);
    }

    private void buildTrainTestSets(Map<String, String> train, Map<String, String> test) {
        trainSet.putAll(train);
        testSet.putAll(test);
    }

    //Methods - Helper Methods - Static Methods
    public static ConcurrentMap<CharSequence, String> getLabelsFromCSV(String filePath, String subNamesPath) { //CSV With Header!
        String line;
        String cvsSplitBy = ",";
        ConcurrentMap<String, String> list = new ConcurrentHashMap<>();
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {

            while ((line = br.readLine()) != null) {

                // use comma as separator
                String[] values = line.split(cvsSplitBy);
                if (count > 0)
                    list.put(values[0].replaceAll("\"", ""), values[1].replaceAll("\"", ""));
                count++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        String newLine;
        ConcurrentHashMap<CharSequence, String> keepOnlyTheseKeys = new ConcurrentHashMap<>();
        try (BufferedReader new_br = new BufferedReader(new FileReader(subNamesPath))) {
            while ((newLine = new_br.readLine()) != null) {
                // use comma as separator
                String[] values = newLine.split(cvsSplitBy);
                String key = values[0].replaceAll("\"", "");
                if(list.containsKey(key)) {
                    keepOnlyTheseKeys.put(key, list.get(key));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return keepOnlyTheseKeys;
    }

    private ConcurrentMap<String, Double> calculateSharedEntropyRange(){
        log.info("Started Computing Entropy");
        entropyRange = new CalculateEntropy(this);
        return entropyRange.computeEntropyRange();
    }

    private ConcurrentMap<String, Integer> buildTrainOrTestSizes() {
        ConcurrentMap<String, Integer> counts = new ConcurrentHashMap<>();
        for (String s : trainSet.values()) {
            counts.compute(s, (k, v) -> (v == null) ? 1 : v + 1);
        }
        return counts;
    }

    private void extractTrainStrings() {
        ConcurrentMap<String, ArrayList<File>> familyFiles = new ConcurrentHashMap<>();
        log.info("Started Train String Extraction");
        File dir = new File(dataSetPath);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            ConcurrentMap<String, Integer> family_sizes = buildTrainOrTestSizes();
            log.info(Arrays.toString(family_sizes.entrySet().toArray()));
            for(File maliciousSample: directoryListing){
                String sampleName = getSampleName(maliciousSample.getName());
                if(!testSet.containsKey(sampleName)){
                    String familyName = trainSet.get(sampleName);
                    if(!familyFiles.containsKey(familyName))
                        familyFiles.put(familyName, new ArrayList<>());
                    familyFiles.get(familyName).add(maliciousSample);
                }
            }
        }

        for(Map.Entry<String, ArrayList<File>> family : familyFiles.entrySet()){
            log.info("Started Computing Family: " + family.getKey() + " Strings!");
            log.info("Computing Strings of: " + family.getValue().size() + " Files!");
            log.info("-----------------------------------------------------");
            int numThreads = (Runtime.getRuntime().availableProcessors());
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(numThreads);
            List<Runnable> todo = new ArrayList<>(family.getValue().size());
            for(File maliciousSample : family.getValue()){
                Runnable runnableObj = () -> threadExtraction(maliciousSample);
                todo.add(runnableObj);
            }
            CompletableFuture<?>[] futures = todo.stream()
                    .map(task -> CompletableFuture.runAsync(task, executor))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
            filterFamilyMap(family.getKey());
            executor.shutdown();
        }
    }

    private String threadExtraction(File maliciousSample){
        Set<String> stringEntered = new HashSet<>();
        String fileBinaryString = getAndroidFileContent(maliciousSample);
        for(String len: entropyRanges.keySet()) {
            int curr_len = Integer.valueOf(len);
            for(int j=0; j<fileBinaryString.length()-curr_len; j++){
                String current_string = fileBinaryString.substring(j, j+curr_len);
                double currentEntropyGap = entropyRanges.get(String.valueOf(curr_len));
                if (this.getEntropyCalculator().getEntropy(current_string) >= currentEntropyGap) {
                    if (!stringEntered.contains(current_string)) {
                        stringEntered.add(current_string);
                        familyIterationMap.getSaveReadMap().compute(current_string, (k, v) -> (v!=null) ? v + 1 : 1);
                    }
                }
            }
        }

        log.info("Finished Handling File: " + getSampleName(maliciousSample.getName()));
        return "Finished Handling File: " + getSampleName(maliciousSample.getName());
    }

    private void reduceMap(ConcurrentMap<String, Integer> familiesWithSizes, String family){
        double minCount = Math.max(2, Math.floorDiv(10*familiesWithSizes.get(family), 100));
        // at least 10 percentages of family size or 2 files if there are less than 20 files
        log.info("Started Map Reducing For Family: " + family);
        log.info("Reducing Map Of: " + String.valueOf(familyIterationMap.getSaveReadMap().longSize()) + " Strings & " + String.valueOf(familiesWithSizes.get(family)) + " Files");
        familyIterationMap.getSaveReadMap().forEachEntry(entry -> {if(entry.value().get()>=minCount)
            familiesStrings.get(family).getSaveReadMap().put(entry.key().get().toString(), entry.value().get());});
        familyIterationMap.getSaveReadMap().clear();
        log.info("Cleared Iteration Map, Now Stands With: " + String.valueOf(familyIterationMap.getSaveReadMap().longSize()) + " Strings");
        log.info("Ended Map Reducing For Family: " + family +
                " With A Total Of: " + String.valueOf(familiesStrings.get(family).getSaveReadMap().size()) + " Strings!");
    }

    private void filterFamilyMap(String family) {
        ConcurrentMap<String, Integer> familiesWithSizes = computeFamiliesWithSizes();
        reduceMap(familiesWithSizes, family);
    }

    public static ConcurrentMap<CharSequence, Integer> buildTrainOrTestSizes(ConcurrentMap<CharSequence, String> set) {
        ConcurrentMap<CharSequence, Integer> counts = new ConcurrentHashMap<>();
        for (String s : set.values()) {
            counts.compute(s, (k, v) -> (v == null) ? 1 : v + 1);
        }
        return counts;
    }

    private void buildDirs(Map<String,String> trainMap) {
        log.info("Started Building/Using Dirs - For Families, Train/Test Maps");
        createAllDirsOfPath(extractionPath);
        //Static Fields
        String STRINGS_FOLDER = "/allTrainStringsInEntropy";
        createAllDirsOfPath(extractionPath + STRINGS_FOLDER);
        for (String family : new HashSet<>(trainMap.values())) {
            String extension = extractionPath + STRINGS_FOLDER + "/" + family;
            createAllDirsOfPath(extension);
            int GUESS_SIZE = 20_000_000;
            integerMap mapReader = new integerMap(extension + "/" + family + ".txt", GUESS_SIZE);
            familiesStrings.putIfAbsent(family, mapReader);
        }

        createAllDirsOfPath(extractionPath+ TRAIN_FOLDER);
        basicMap trainMapReader = new basicMap(extractionPath+ TRAIN_FOLDER + "/train.txt", SET_APPROX_SIZE);
        trainSet = trainMapReader.getSaveReadMap();
        datasetSetsSaveReadMaps.put("Train", trainMapReader);
        createAllDirsOfPath(extractionPath+ TEST_FOLDER);
        basicMap testMapReader = new basicMap(extractionPath+ TEST_FOLDER + "/test.txt", SET_APPROX_SIZE);
        testSet = testMapReader.getSaveReadMap();
        datasetSetsSaveReadMaps.put("Test", testMapReader);
    }

    //Public API/Public Helper Methods

    public void completeInitialStages() {
        log.info("Started Computing Sets!");
        log.info("Built Train Set With: " + String.valueOf(trainSet.size()) + " Files!");
        datasetSetsSaveReadMaps.get("Train").saveFile(trainSet);
        log.info("Built Test Set With: " + String.valueOf(testSet.size()) + " Files!");
        datasetSetsSaveReadMaps.get("Test").saveFile(testSet);
        log.info("Started Calculating: Shared Entropy Range");
        entropyRanges = calculateSharedEntropyRange();
        log.info("Finished Calculating: Shared Entropy Range. Moving on to Families Strings");
        extractTrainStrings();
        log.info("Finished Calculating: Best Families Strings");
    }


    public ConcurrentMap<String, Integer> computeFamiliesWithSizes(){
        ConcurrentMap<String, Integer> toReturn = new ConcurrentHashMap<>();
        for(Map.Entry<CharSequence, String> mapping: trainSet.entrySet()){
            toReturn.compute(mapping.getValue(), (k, v) -> (v == null) ? 1 : v + 1);
        }
        return toReturn;
    }

    public static void createAllDirsOfPath(String path){
        File trainFolderPerPathFile = new File(path);
        if (!trainFolderPerPathFile.exists()) {
            trainFolderPerPathFile.mkdir();
        }
    }

    public ConcurrentMap<CharSequence, String> getTrainSet() {
        return trainSet;
    }

    public ConcurrentMap<CharSequence, String> getStringsTrainSet() {
        return trainSet;
    }

    public ConcurrentMap<CharSequence, String> getTestSet() {
        return testSet;
    }

    public String getDataSetPath() {
        return dataSetPath;
    }

    public int getDataLength() {
        return dataLength;
    }

    public String getExtractionPath() {
            return this.extractionPath;
    }

    public int getDataSetSize() {
        return this.trainSet.size() + this.testSet.size();
    }

    public CalculateEntropy getEntropyCalculator(){
        return this.entropyRange;
    }

    public void clearMaps(){
        for(Map.Entry<String, integerMap> map: familiesStrings.entrySet())
            map.getValue().saveFile();
        for(Map.Entry<String, basicMap> map: datasetSetsSaveReadMaps.entrySet())
            map.getValue().saveFile();
        familyIterationMap.getSaveReadMap().close();
    }

    public integerMap getFamilyStrings(String familyName){
        return this.familiesStrings.get(familyName);
    }
}