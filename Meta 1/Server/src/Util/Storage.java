package Util;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;

public class Storage {


    //temporary copy file system i cant do it in udp i have no clue
    public static boolean copyFile(File file, String serverlocation){
        String fileName = file.getPath();
        //this is awful but it should work
        String [] dir= fileName.split(":?\\\\");
        for(int i=0; i< dir.length; i++) {

            if (dir[i].equals("storage")) {
                dir[i]=null;
                break;
            }
            dir[i]=null;
        }
        String[] newdir = Arrays.stream(dir).filter(Objects::nonNull).toArray(String[]::new);

        String temp = String.join("\\", newdir);

        String location= serverlocation + "\\" + temp;

        File copied = new File(location);

        try (InputStream in = new BufferedInputStream(new FileInputStream(file));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(copied))){

            byte[] buffer = new byte[1024];
            int lengthRead;
            while ((lengthRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, lengthRead);
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;


    }

}
