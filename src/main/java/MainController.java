import Data.Dataset;
import FeaturesExtractors.ByteFrequenciesFeatures;
import FeaturesExtractors.DividingStringsFeatures;
import com.squareup.javapoet.ClassName;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

import static Data.Dataset.dirCreation;
import static Data.Dataset.getLabelsFromCSV;

public class MainController {
    private static final Logger log = Logger.getLogger(ClassName.class.getName());

    public static void main(String[] args){
        log.info("Started Algorithm");

        String dataSetPath = "/DATA/Ron/Datasets/Drebin/DrebinDataset";
        String dividedDatasetPath = "/DATA/Ron/Datasets/Drebin/CurrentDrebinDataset";
        String dataSetLabelsPath = "/DATA/Ron/Datasets/Drebin/Labels.csv";
        String dataSetsFamiliesPath = "/DATA/Ron/Datasets/Drebin/Families.csv";

        double divisionConst = 0.3;
        int maxNumFeatures = 50000, maxDataLength = 64;

        Map<String, String> toUseDataset = createPossiblySubDataSet(dataSetLabelsPath, dataSetsFamiliesPath, dataSetPath, dividedDatasetPath);
        Map<String, Map<String,String>> dividedDataSet = divideDataSet(toUseDataset, divisionConst);
        Map<String, String> test = dividedDataSet.get("Test");
        Map<String, String> train = dividedDataSet.get("Train");

        String resultPath = "/DATA/Ron/Datasets/Drebin/Features";
        String keepExtractedDataPath = "/DATA/Ron/Datasets/Drebin/Results";

        Dataset drebinDataset = new Dataset(dividedDatasetPath, train, test, keepExtractedDataPath,
                maxDataLength);

        drebinDataset.computeSets();
        log.info("Finished Extraction Of Strings From Dataset Of: " +
                String.valueOf(drebinDataset.getDataSetSize()) + " Files!");

        DividingStringsFeatures dividingFeatures = new DividingStringsFeatures(resultPath, maxNumFeatures, drebinDataset);
        dividingFeatures.computeFeatures();
        dividingFeatures.clearCombinationMaps();
        ByteFrequenciesFeatures byteFrequenciesFeatures = new ByteFrequenciesFeatures(resultPath, drebinDataset);
        byteFrequenciesFeatures.computeFeatures();
        drebinDataset.clearMaps();
        log.info("Ended Algorithm");
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

    private static Map<String, Map<String,String>> divideDataSet(Map<String, String> createdSubDataSet, double size){
        HashMap<String, Map<String, String>> toReturn = new HashMap<>();
        HashMap<String, String> train = new HashMap<>();
        HashMap<String, String> test = new HashMap<>();
        int testSize = (int) (createdSubDataSet.size()*size);
        int trainSize = createdSubDataSet.size() - testSize;

        List<Map.Entry<String, String>> list = new ArrayList<>(createdSubDataSet.entrySet());

        for (Map.Entry<String, String> entry : list) {
            if(test.size()<testSize)
                test.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, String> entry : list) {
            if(train.size()<trainSize && !test.containsKey(entry.getKey()))
                train.put(entry.getKey(), entry.getValue());
        }

        toReturn.put("Train", train);
        toReturn.put("Test", test);
        log.info("Finished Building Test & Train Sets");
        log.info("Test Size: " + test.size());
        log.info("Train Size: " +train.size());
        return toReturn;
    }

    private static Map<String, String> createPossiblySubDataSet(String prevLabelsPath, String interestingFamiliesPath, String dataSetPath,
                                                                String resultPath){
        dirCreation(resultPath);
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
        log.info("Finished Building Sub Labels Map");
        log.info("New Labels Map Size: " + reducedLabelsMap.size());
        return reducedLabelsMap;
    }
}
