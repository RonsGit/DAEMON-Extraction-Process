package Maps;

import net.openhft.chronicle.map.ChronicleMap;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class basicMap {
    private ChronicleMap<CharSequence, String> saveReadMap;
    private String PATH;
    private int guessSize;

    public basicMap(String PATH, int guessSize){
        this.PATH = PATH;
        this.guessSize = guessSize;
        readValuesMap();
    }

    public ChronicleMap<CharSequence, String> getSaveReadMap(){
        return saveReadMap;
    }

    public void saveFile(ConcurrentHashMap<CharSequence, String> map)
    {
        writeHashMap(map, PATH);
    }

    public void saveFile(ConcurrentMap<CharSequence, String> map)
    {
        ConcurrentHashMap<CharSequence, String> toSend = new ConcurrentHashMap<>(map);
        writeHashMap(toSend, PATH);
    }

    public void saveFile(HashMap<CharSequence, String> map)
    {
        writeHashMap(map, PATH);
    }

    static void writeHashMap(HashMap toSave, String PATH){
        writeMap(toSave, PATH);
    }

    static void writeHashMap(ConcurrentHashMap toSave, String PATH){
        writeMap(toSave, PATH);
    }

    private static void writeMap(Map m, String PATH){
        ObjectOutputStream out;
        try {
            out = new ObjectOutputStream(new FileOutputStream(PATH));
            out.writeObject(m);
            out.close();
        } catch (IOException e) {
            System.out.println("Failed To Save Map! " + e.getMessage());
        }
    }

    public void saveFile()
    {
        HashMap<CharSequence, String> toSave = new HashMap<>(saveReadMap);
        writeHashMap(toSave, PATH);
    }

    public ChronicleMap<CharSequence,String> readValuesMap() {
        File toMakeIfNotThere = new File(PATH);
        if (toMakeIfNotThere.exists()) {
            ObjectInputStream in;
            try {
                in = new ObjectInputStream(new FileInputStream(PATH));
                Map<CharSequence, String> map = (Map<CharSequence, String>) in.readObject();
                saveReadMap = ChronicleMap
                        .of(CharSequence.class, String.class)
                        .averageValueSize(4)
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
                    .of(CharSequence.class, String.class)
                    .averageValueSize(4)
                    .averageKey("01jsnpXSAlgw6aPeDxrU")
                    .entries(guessSize)
                    .create();
        }
        return saveReadMap;
    }
}
