package Data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;

public class FileActions {

    public static String getSampleName(String s){
        int index = s.lastIndexOf(".");

        if (index > 0) {
            return s.substring(0, index).replaceAll("\"", "\\\\\"");
        }
        else {
            return s.replaceAll("\"", "\\\\\"");
        }
    }

    public static Map.Entry<CharSequence, String> getRandomFile(Map<CharSequence, String> items) {
        int size = items.size();
        int item = new Random().nextInt(size);
        int i = 0;
        for(Map.Entry<CharSequence, String> obj : items.entrySet())
        {
            if (i == item)
                return obj;
            i++;
        }
        return null;
    }

    public static String getAndroidFileContent(File maliciousSampleDir){
       if(maliciousSampleDir.isDirectory()){
           StringBuilder toReturn = new StringBuilder();
           File[] dirFiles = maliciousSampleDir.listFiles();
           if(dirFiles!=null){
               for(File innerFile: dirFiles){
               try {
                   if(!innerFile.isDirectory()) {
                       Path filePath = Paths.get(innerFile.getAbsolutePath());
                       byte[] data = Files.readAllBytes(filePath);
                       toReturn.append(bytesToHex(data));
                   }
               } catch (IOException e) {
                   e.printStackTrace();
               }
             }
           }
           return toReturn.toString();
       }
       else {
           throw new IllegalArgumentException("File given is not a directory!");
       }
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
