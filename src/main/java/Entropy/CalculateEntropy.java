package Entropy;

import Data.Dataset;
import com.squareup.javapoet.ClassName;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import static Data.FileActions.getAndroidFileContent;
import static Data.FileActions.getRandomFile;
import static FeaturesExtractors.ByteFrequenciesFeatures.isInteger;

public class CalculateEntropy {

    private int maxLength;
    private Map<String,Double> totalEntropies;
    private Map<String,Integer> totalStrings;
    private static int NUM_REP = 447, NUM_EACH = 250;
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    public CalculateEntropy(Dataset d){
        this.maxLength = d.getDataLength();
        Map<String, String> representativesMap = getRepresentativesMap(d.getTrainSet());
        buildTotalEntropiesMap();
        buildTotalStringsMap();
        buildData(d.getDataSetPath(), representativesMap);
    }

    private void buildTotalStringsMap() {
        this.totalStrings = new HashMap<>();
        for(String s: totalEntropies.keySet()){
            totalStrings.put(s, 0);
        }
    }

    private void buildData(String dataSetPath, Map<String, String> representativesMap) {
        File dir = new File(dataSetPath);
        File[] directoryListing = dir.listFiles();
        assert directoryListing != null;
        for (File f : directoryListing) {
            String fileName = f.getName();
            if(f.getName().lastIndexOf('.')>0)
                fileName = fileName.substring(0, f.getName().lastIndexOf('.'));
            fileName = fileName.replaceAll("\"","\\\\\"");
            if(representativesMap.containsKey(fileName) && f.isDirectory()){
                String fileContent = getAndroidFileContent(f);
                HashMap<String, HashSet<String>> strings = computeStringSet(fileContent);
                getStringsEntropies(strings);
                log.info("Finished Calculating Entropies For: " + fileName);
            }
        }
    }

    public static void buildFrequencies(String fileBinaryString, int len, int[] freq) {
        for (int i = 0; i + 2 <= len; i += 2) {
            String current_str = fileBinaryString.substring(i, i + 2);
            if(isInteger(current_str, 16))
                freq[Integer.valueOf(current_str, 16)]++;
        }
    }

    private HashMap<Character, Integer> buildFrequencies(String fileBinaryString, int len) {
        HashMap<Character, Integer> toReturn = new HashMap<>();
        for (int i = 0; i < len; i += 1) {
            Character current_char = fileBinaryString.charAt(i);
            toReturn.compute(current_char, (k, v) -> (v == null) ? 1 : v + 1);
        }
        return toReturn;
    }

    public double getEntropy(String s){
        int len = s.length();
        HashMap<Character, Integer> frequencies_map = buildFrequencies(s, len);

        // compute Shannon entropy
        double entropy = 0.0;
        int sum=0;

        for(Map.Entry<Character, Integer> charFreq : frequencies_map.entrySet()){
            sum+=charFreq.getValue();
        }

        for(Map.Entry<Character, Integer> charFreq : frequencies_map.entrySet()){
            double prob = charFreq.getValue() * 1.0 / sum;
            entropy -= prob * Math.log(prob) / Math.log(2);
        }

        return entropy;
    }

    private void getStringsEntropies(HashMap<String, HashSet<String>> strings){
        for(Map.Entry<String, HashSet<String>> len_entry: strings.entrySet()){
            for(String curr_string_in_len : len_entry.getValue()){
                double toReplace = totalEntropies.get(len_entry.getKey()) + getEntropy(curr_string_in_len);
                totalEntropies.replace(len_entry.getKey(), toReplace);
                int prevNum = totalStrings.get(len_entry.getKey());
                totalStrings.replace(len_entry.getKey(), prevNum+1);
            }
        }
    }

    private HashMap<String, HashSet<String>> computeStringSet(String text){

        HashSet<String> fileStrings = new HashSet<>();
        HashMap<String, HashSet<String>> allFileStrings = new HashMap<>();

        for(String s: totalEntropies.keySet())
            allFileStrings.put(s, new HashSet<>());

        int i=0;
        long startTime = System.currentTimeMillis(); //fetch starting time, making sure it won't run for too long
        int TIME_LIMIT = 5000;
        while (i<NUM_EACH && (System.currentTimeMillis()-startTime)< TIME_LIMIT) {
            Random rnd = new Random();
            int index = rnd.nextInt(Math.max(0, text.length() - (maxLength+1)));
            String lrs = text.substring(index, index + maxLength);
            boolean added = fileStrings.add(lrs);
            if(added)
                i++;
        }

        for(String s: fileStrings){
            for(String len: allFileStrings.keySet()){
                int curr_len = Integer.valueOf(len);
                if(curr_len!=s.length()){
                    for(int j=0; j<s.length()-curr_len; j++){
                        allFileStrings.get(len).add(s.substring(j, j+curr_len));
                    }
                }
                else {
                    allFileStrings.get(len).add(s);
                }
            }
        }

        return allFileStrings;
    }

    public ConcurrentMap<String, Double> computeEntropyRange(){
        ConcurrentMap<String, Double> rangesMap = new ConcurrentHashMap<>();
        for(Map.Entry<String, Double> len_entry: totalEntropies.entrySet()){
            switch(len_entry.getKey()){
                case "16":
                    rangesMap.put(len_entry.getKey(), 1.05*len_entry.getValue()/totalStrings.get(len_entry.getKey()));
                    break;
                case "8":
                    rangesMap.put(len_entry.getKey(), 1.05*len_entry.getValue()/totalStrings.get(len_entry.getKey()));
                    break;
                default:
                    rangesMap.put(len_entry.getKey(), 1.15*len_entry.getValue()/totalStrings.get(len_entry.getKey()));
            }
        }

        log.info("Returning Ranges Map of " + String.valueOf(rangesMap.size()) + " Lengths");
        log.info(Collections.singletonList(rangesMap).toString());

        return rangesMap;
    }

    private void buildTotalEntropiesMap(){
        this.totalEntropies = new HashMap<>();
        int curr_len = maxLength;
        while(curr_len>4){
            totalEntropies.put(String.valueOf(curr_len), 0.0);
            curr_len = curr_len /2;
        }
    }

    private Map<String, String> getRepresentativesMap(ConcurrentMap<CharSequence, String> trainSet) {
        Map<String, String> representatives = new HashMap<>();
        while(representatives.size()< NUM_REP){
            Map.Entry<CharSequence, String> entry = getRandomFile(trainSet);
            assert entry != null;
            representatives.put(entry.getKey().toString(), entry.getValue());
        }
        return representatives;
    }

}