package FeaturesExtractors;

import Data.Dataset;

import java.util.ArrayList;

import static Data.Dataset.createAllDirsOfPath;

public abstract class FeaturesCollection {

    private String featureCollectionPath;
    private ArrayList<String> dataFrameHeader;
    private ArrayList<String> featuresThemselves;
    private Dataset toExtractFrom;

    protected FeaturesCollection(String featureCollectionPath, Dataset toExtractFrom) {
        createAllDirsOfPath(featureCollectionPath);
        this.featureCollectionPath = featureCollectionPath;
        this.dataFrameHeader = new ArrayList<>();
        this.featuresThemselves = new ArrayList<>();
        this.toExtractFrom = toExtractFrom;
    }

    public abstract void computeFeatures();

    protected ArrayList<String> getDataFrameHeader() {
        return dataFrameHeader;
    }

    protected String getFeatureCollectionPath() {
        return featureCollectionPath;
    }

    protected ArrayList<String> getFeaturesThemselves() {
        return featuresThemselves;
    }

    public Dataset getToExtractFrom() {
        return toExtractFrom;
    }
}
