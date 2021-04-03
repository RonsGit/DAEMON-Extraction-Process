package FeaturesExtractors;
import Data.Dataset;
import Data.FileActions;
import Maps.doubleMap;
import Maps.stringsMap;
import com.google.common.math.DoubleMath;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import com.opencsv.CSVWriter;
import com.squareup.javapoet.ClassName;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import static Data.Dataset.*;
import static Data.FileActions.getSampleName;

public class DividingStringsFeatures extends FeaturesCollection {

    private static String C_EXTENSION = "/Maps", S_EXTENSION ="_Scores.txt", L_EXTENSION="_Lines.txt", R_EXTENSION ="/Results";
    private static Integer SAFETY_MARGIN = 100, COMBINATION_GUESS=5_000_000;
    private stringsMap featuresList;
    private ConcurrentMap<String, doubleMap> combinationsMap;
    private ConcurrentMap<String, ConcurrentMap<CharSequence, Double>> combinationsMaps;
    private int maxAmount;
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    public DividingStringsFeatures(String featureCollectionPath, int maxAmount, Dataset toExtractFrom) {
        super(featureCollectionPath, toExtractFrom);
        this.maxAmount = maxAmount;
        String path = toExtractFrom.getExtractionPath()+"/DividingStrings";
        createAllDirsOfPath(path);
        this.featuresList = new stringsMap(path + "/" + "DividingStrings.txt", toExtractFrom.getTrainSet().size(), maxAmount);
        this.combinationsMap = new ConcurrentHashMap<>();
        this.combinationsMaps = new ConcurrentHashMap<>();
    }

    @Override
    public void computeFeatures() {
        log.info("Started Handling Dividing String Features Computation");
        ConcurrentMap<String, Integer> trainFamilies = this.getToExtractFrom().computeFamiliesWithSizes();
        int combinations = nCr(trainFamilies.size(), 2);
        double calc = Math.floorDiv(maxAmount, combinations);
        int perGroupFeatures = (int) Math.round(calc);
        createAllDirsOfPath(this.getFeatureCollectionPath()+ C_EXTENSION);
        ConcurrentMap<String, Double> prevStringsWithScores =
                getBestStringsFromPrevCombinations(SAFETY_MARGIN *perGroupFeatures, perGroupFeatures);
        Set<String> previousCombinations = extractNamesOfPrevCombinations();
        this.getFeaturesThemselves().addAll(prevStringsWithScores.keySet());
        log.info("Extracted: " + String.valueOf(this.getFeaturesThemselves().size()) + " Previous Strings");
        extractRemainingCombinations(previousCombinations, trainFamilies, perGroupFeatures);
        log.info("Extracted: " + String.valueOf(this.getFeaturesThemselves().size()) + " Strings Totally");
        buildTrainFeatures();
    }


    private void finishWriting(CSVWriter writer) throws IOException {
        writeAllLines(writer);
        writer.flush();
        writer.close();
    }

    private void writeAllLines(CSVWriter writer) {
        List<String> sortedKeys = getSortedKeys(featuresList.getSaveReadMap().keySet());
        for(String s: sortedKeys){
            writer.writeNext(featuresList.getSaveReadMap().get(s));
        }
    }

    public static List<String> getSortedKeys(Set<CharSequence> keySet) {
        List<String> myList = new ArrayList<>();
        for(CharSequence c : keySet)
            myList.add(c.toString());
        Collections.sort(myList);
        return myList;
    }

    static List<String> getSortedStringKeys(Set<String> keySet) {
        List<String> myList = new ArrayList<>(keySet);
        Collections.sort(myList);
        return myList;
    }

    private void extractRemainingCombinations(Set<String> previousCombinations, ConcurrentMap<String, Integer> trainFamilies, int perGroup){
        for(String family1: trainFamilies.keySet()) {
            ConcurrentMap<CharSequence, Integer> firstFamilyMap  = openFamilyDatabase(family1);
            for (String family2 : trainFamilies.keySet()) {
                ConcurrentMap<CharSequence, Integer> secondFamilyMap = openFamilyDatabase(family2);
                if (!family1.equals(family2)) {
                    String prev = family2 + "_" + family1;
                    String current = family1 + "_" + family2;
                    if (!previousCombinations.contains(prev) && !previousCombinations.contains(current)) {
                        previousCombinations.add(current);
                        ConcurrentMap<CharSequence, Double> combinationMap = createOpenCombinationDatabase(current);
                        BoundedPQueue queue = new BoundedPQueue((SAFETY_MARGIN * perGroup));
                        handleCombination(current, firstFamilyMap, secondFamilyMap, combinationMap, queue,
                                family1, family2, trainFamilies, perGroup);
                    }
                }
            }
        }
    }

    private ConcurrentMap<CharSequence, Double> createOpenCombinationDatabase(String combination) {
        String fullPath = this.getFeatureCollectionPath()+ C_EXTENSION +"/"+ combination;
        createAllDirsOfPath(fullPath);
        doubleMap mapReader = new doubleMap(fullPath + "/" + combination + S_EXTENSION, COMBINATION_GUESS);
        return mapReader.getSaveReadMap();
    }

    private ConcurrentMap<CharSequence, Integer> openFamilyDatabase(String familyName){
        return this.getToExtractFrom().getFamilyStrings(familyName).getSaveReadMap();
    }

    private void buildTrainFeatures(){
        String resultPath = this.getFeatureCollectionPath()+ R_EXTENSION;
        createAllDirsOfPath(resultPath);
        buildDataFrameHeader();
        FileWriter outputStringFile;
        try {
            outputStringFile = new FileWriter(resultPath + "/trainStringsAndCounts.csv");
            CSVWriter writer = new CSVWriter(outputStringFile);
            ConcurrentMap<CharSequence, String> trainSet = this.getToExtractFrom().getTrainSet();
            String[] firstLine = this.getDataFrameHeader().toArray(new String[0]);
            writer.writeNext(firstLine);
            File dir = new File(this.getToExtractFrom().getDataSetPath());
            File[] directoryListing = dir.listFiles();
            handleFiles(trainSet, directoryListing);
            finishWriting(writer);
            log.info("Finished Extracting String Repeats From Each Train File");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Note: Training feature vectors computation time (classification time) is computed using a SINGLE CORE
    private void handleFiles(ConcurrentMap<CharSequence, String> filesSet, File[] directoryListing) {
        if (directoryListing != null) {
            ConcurrentMap<CharSequence, Integer> family_sizes = buildTrainOrTestSizes(filesSet);
            log.info(Arrays.toString(family_sizes.entrySet().toArray()));
            for (File maliciousSample : directoryListing) {
                String sampleName = getSampleName(maliciousSample.getName());
                String familyName;
                if (filesSet.containsKey(sampleName)) {
                    familyName = filesSet.get(sampleName);
                    String[] toSearch = new String[this.getFeaturesThemselves().size()];
                    for (int i = 0; i < toSearch.length; i++)
                        toSearch[i] = this.getFeaturesThemselves().get(i);
                    TreeMap<String, String> map = new TreeMap<>();
                    for (String key : toSearch)
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
                        for (int i = 0; i < toSearch.length; i++) {
                            if (hitsMap.containsKey(toSearch[i]))
                                toPrint[i] = (hitsMap.get(toSearch[i])).toString();
                            else
                                toPrint[i] = "0";
                        }
                        toPrint[toPrint.length - 1] = familyName;
                        String fileName = getSampleName(maliciousSample.getName());
                        toPrint[toPrint.length - 2] = fileName;
                        featuresList.getSaveReadMap().put(fileName, toPrint);
                        log.info("Finished Extracting Hits From Train File: " + fileName);
                    }
                }
            }
        }
    }

    private void buildDataFrameHeader() {
        for(String s: this.getFeaturesThemselves()){
            this.getDataFrameHeader().add(s);
        }
        this.getDataFrameHeader().add("FileName");
        this.getDataFrameHeader().add("FamilyName");
    }

    private void handleCombination(String current, ConcurrentMap<CharSequence, Integer> firstFamilyMap,
                                   ConcurrentMap<CharSequence, Integer> secondFamilyMap,
                                   ConcurrentMap<CharSequence, Double> combinationMap, BoundedPQueue queue, String family1,
                                   String family2, ConcurrentMap<String, Integer> trainFamilies, int perGroup) {

        log.info("Started Handling Combination: " + current);
        firstFamilyMap.entrySet().stream().forEach(
                entry -> {
                    String s1 = entry.getKey().toString();
                    Integer counter1 = entry.getValue();
                    long counter2 = 0;
                    if(secondFamilyMap.containsKey(s1))
                        counter2 = secondFamilyMap.get(s1);
                    double score = computeEntropyStringScore(trainFamilies, family1, family2, counter1, counter2);
                    queue.offer(new Pair<>(s1, score));
                });
        log.info("Ended Loop Of Family: " + family1);

        //Handled the first family map, now need to move on to the other family map
        HashSet prev_keys = queue.getKeys();
        secondFamilyMap.entrySet().stream().forEach(
                entry -> {
                    String s1 = entry.getKey().toString();
                    if(!prev_keys.contains(s1)) {
                        Integer counter2 = entry.getValue();
                        double score = computeEntropyStringScore(trainFamilies, family1, family2, 0, counter2);
                        queue.offer(new Pair<>(s1, score));
                    }
                });
        log.info("Ended Loop Of Family: " + family2);

        int counter = 0;
        ArrayList<Pair<String, Double>> sorted = queue.getSorted();
        List<String> lines = new LinkedList<>();
        for(Pair<String, Double> ent: sorted){
            if(!this.getFeaturesThemselves().contains(ent.getKey()) && counter<perGroup) {
                this.getFeaturesThemselves().add(ent.getKey());
                combinationMap.put(ent.getKey(), ent.getValue());
                String str = "Added String: " + ent.getKey() +" To Families: " + current +
                        " With Score: " + ent.getValue();
                log.info(str);
                lines.add(str);
                counter++;
            }
        }

        String fullPath = this.getFeatureCollectionPath()+ C_EXTENSION +"/"+current;
        Path out = Paths.get(fullPath + "/" + current + L_EXTENSION);
        try {
            Files.write(out,lines, Charset.defaultCharset());
        } catch (IOException e) {
            e.printStackTrace();
        }
        doubleMap saveCombinationMap = new doubleMap(fullPath + "/" + current + S_EXTENSION, COMBINATION_GUESS);
        saveCombinationMap.saveFile(combinationMap);
    }

    private int nCr(int n, int r)
    {
        if( r== 0 || n == r)
            return 1;
        else
            return nCr(n-1,r)+ nCr(n-1,r-1);
    }

    private Set<String> extractNamesOfPrevCombinations() {
        Set<String> previousCombinations = new HashSet<>();
        File featuresPath = new File(this.getFeatureCollectionPath() + C_EXTENSION);
        File[] combinations = featuresPath.listFiles();

        if(combinations!=null) {
            for (File combination : combinations) {
                previousCombinations.add(combination.getName());
            }
        }
        return previousCombinations;
    }

    private ConcurrentMap<String, Double> getBestStringsFromPrevCombinations(int takeWithSafety, int perGroup) {
        ConcurrentMap<String, Double> bestStringsWithScores = new ConcurrentHashMap<>();
        File featuresPath = new File(this.getFeatureCollectionPath()+ C_EXTENSION);
        File[] combinations = featuresPath.listFiles();

        if(combinations!=null){
            for(File combination : combinations){
                doubleMap readMap = new doubleMap(this.getFeatureCollectionPath()+ C_EXTENSION  + "/" + combination.getName()+ "/" + combination.getName()+S_EXTENSION, COMBINATION_GUESS);
                ConcurrentMap<CharSequence,Double> prevMap = readMap.getSaveReadMap();
                combinationsMap.put(combination.getName(), readMap);
                combinationsMaps.put(combination.getName(), readMap.getSaveReadMap());
                BoundedPQueue currentQueue = new BoundedPQueue(takeWithSafety);

                for(Map.Entry<CharSequence, Double> prevEntry : prevMap.entrySet()) {
                    currentQueue.offer(new Pair<>(prevEntry.getKey().toString(), prevEntry.getValue()));
                }
                currentQueue.addStrings(bestStringsWithScores, perGroup);
            }
        }
        return bestStringsWithScores;
    }

    private double computeEntropyStringScore(ConcurrentMap<String, Integer> trainFamilies,
                                             String family_1, String family_2, long count1, long count2){
        int family1Size = trainFamilies.get(family_1);
        int family2Size = trainFamilies.get(family_2);
        double bothFamiliesSize = family1Size + family2Size;
        double countBothThere = count1 + count2;
        double notBothThereCount = bothFamiliesSize - countBothThere;
        double r1 = family1Size/bothFamiliesSize;
        double r2 = family2Size/bothFamiliesSize;
        if(notBothThereCount!=0 && countBothThere!=0) {
            double rootEntropy = (-r1 * DoubleMath.log2(r1)) - (r2 * DoubleMath.log2(r2));
            double leftEntropy, rightEntropy;

            if(count1==0) {
                leftEntropy = - (count2 / countBothThere) * DoubleMath.log2((count2 / countBothThere));
                if(count2==family2Size) rightEntropy=0;
                else rightEntropy = - ((family2Size - count2) / notBothThereCount) * DoubleMath.log2(((family2Size - count2) / notBothThereCount));
            }
            else if(count2==0){
                leftEntropy = -(count1 / countBothThere) * DoubleMath.log2((count1 / countBothThere));
                if(count1==family1Size) rightEntropy=0;
                else rightEntropy = -((family1Size - count1) / notBothThereCount) * DoubleMath.log2(((family1Size - count1) / notBothThereCount));
            }
            else {
                leftEntropy = -(count1 / countBothThere) * DoubleMath.log2((count1 / countBothThere)) - (count2 / countBothThere) * DoubleMath.log2((count2 / countBothThere));
                if(count1!=family1Size && count2!=family2Size)
                    rightEntropy = -((family1Size - count1) / notBothThereCount) * DoubleMath.log2(((family1Size - count1) / notBothThereCount)) - ((family2Size - count2) / notBothThereCount) * DoubleMath.log2(((family2Size - count2) / notBothThereCount));
                else if (count1==family1Size)
                    rightEntropy = - ((family2Size - count2) / notBothThereCount) * DoubleMath.log2(((family2Size - count2) / notBothThereCount));
                else
                    rightEntropy = -((family1Size - count1) / notBothThereCount) * DoubleMath.log2(((family1Size - count1) / notBothThereCount));
            }
            double eString = (countBothThere / bothFamiliesSize) * leftEntropy + (notBothThereCount / bothFamiliesSize) * rightEntropy;
            return rootEntropy-eString;
        }
        else {
            return 0;
        }
    }

    public void clearCombinationMaps(){
        for(Map.Entry<String, doubleMap> map: combinationsMap.entrySet())
            map.getValue().saveFile(combinationsMaps.get(map.getKey()));
        featuresList.saveFile();
    }
}
