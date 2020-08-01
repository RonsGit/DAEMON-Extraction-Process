package Maps;

import net.openhft.chronicle.map.ChronicleMap;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static Maps.basicMap.writeHashMap;

public class doubleMap {
    private ChronicleMap<CharSequence, Double> saveReadMap;
    private String PATH;
    private int guessSize;

    public doubleMap(String PATH, int guessSize){
        this.PATH = PATH;
        this.guessSize = guessSize;
        readValuesMap();
    }

    public ChronicleMap<CharSequence, Double> getSaveReadMap(){
        return saveReadMap;
    }

    public void saveFile(HashMap<CharSequence, Double> map)
    {
        writeHashMap(map, PATH);
    }

    public void saveFile(ConcurrentMap<CharSequence, Double> map)
    {
        ConcurrentHashMap<CharSequence, Double> toSend = new ConcurrentHashMap<>(map);
        writeHashMap(toSend, PATH);
    }

    public void saveFile()
    {
        HashMap<CharSequence, Double> toSave = new HashMap<>(saveReadMap);
        writeHashMap(toSave, PATH);
    }

    public ChronicleMap<CharSequence,Double> readValuesMap() {
        File toMakeIfNotThere = new File(PATH);
        if (toMakeIfNotThere.exists()) {
            ObjectInputStream in;
            try {
                in = new ObjectInputStream(new FileInputStream(PATH));
                Map<CharSequence, Double> map = (Map<CharSequence, Double>) in.readObject();
                saveReadMap = ChronicleMap
                        .of(CharSequence.class, Double.class)
                        .averageKey("01jsnpXSAlgw6aPeDxrU")
                        .entries(guessSize)
                        .create();
                saveReadMap.putAll(map);
                in.close();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Failed To Read Map! " + e.getMessage());
            }
        }
        else {
            saveReadMap = ChronicleMap
                    .of(CharSequence.class, Double.class)
                    .averageKey("01jsnpXSAlgw6aPeDxrU")
                    .entries(guessSize)
                    .create();
        }
        return saveReadMap;
    }
}
