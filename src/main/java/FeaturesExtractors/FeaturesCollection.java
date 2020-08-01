package FeaturesExtractors;

import Data.Dataset;

import java.util.ArrayList;

import static Data.Dataset.dirCreation;

public abstract class FeaturesCollection {

    private String featureCollectionPath;
    private ArrayList<String> dataFrameHeader;
    private ArrayList<String> featuresThemselves;
    private Dataset toExtractFrom;

    FeaturesCollection(String featureCollectionPath, Dataset toExtractFrom) {
        dirCreation(featureCollectionPath);
        this.featureCollectionPath = featureCollectionPath;
        this.dataFrameHeader = new ArrayList<>();
        this.featuresThemselves = new ArrayList<>();
        this.toExtractFrom = toExtractFrom;
    }

    public abstract void computeFeatures();

    ArrayList<String> getDataFrameHeader() {
        return dataFrameHeader;
    }

    String getFeatureCollectionPath() {
        return featureCollectionPath;
    }

    ArrayList<String> getFeaturesThemselves() {
        return featuresThemselves;
    }

    public Dataset getToExtractFrom() {
        return toExtractFrom;
    }
}
