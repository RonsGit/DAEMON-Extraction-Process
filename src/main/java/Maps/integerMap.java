package Maps;

import net.openhft.chronicle.map.ChronicleMap;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static Maps.basicMap.writeHashMap;

public class integerMap {
    private ChronicleMap<CharSequence, Integer> saveReadMap;
    private String PATH;
    private long guessSize;
    private String keyExample;

    public integerMap(String PATH, long guessSize){
        this.PATH = PATH;
        this.guessSize = guessSize;
        this.keyExample = "01jsnpXSAlgw6aPeDxrU";
        readValuesMap();
    }

    public integerMap(String PATH, long guessSize, String keyExample){
        this.PATH = PATH;
        this.guessSize = guessSize;
        this.keyExample = keyExample;
        readValuesMap();
    }

    public ChronicleMap<CharSequence, Integer> getSaveReadMap(){
        return saveReadMap;
    }

    public void saveFile(HashMap<CharSequence, Integer> map)
    {
        writeHashMap(map, PATH);
    }

    public void saveFile(ConcurrentMap<CharSequence, Integer> map)
    {
        ConcurrentHashMap<CharSequence, Integer> toSend = new ConcurrentHashMap<>(map);
        writeHashMap(toSend, PATH);
    }

    public void saveFile()
    {
        HashMap<CharSequence, Integer> toSave = new HashMap<>(saveReadMap);
        writeHashMap(toSave, PATH);
    }

    private ChronicleMap<CharSequence,Integer> readValuesMap() {
        File toMakeIfNotThere = new File(PATH);
        if (toMakeIfNotThere.exists()) {
            ObjectInputStream in;
            try {
                in = new ObjectInputStream(new FileInputStream(PATH));
                Map<CharSequence, Integer> map = (Map<CharSequence, Integer>) in.readObject();
                saveReadMap = ChronicleMap
                        .of(CharSequence.class, Integer.class)
                        .averageKey(keyExample)
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
                    .of(CharSequence.class, Integer.class)
                    .averageKey(keyExample)
                    .entries(guessSize)
                    .create();
        }
        return saveReadMap;
    }
}
