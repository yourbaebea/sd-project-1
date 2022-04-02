package Util;
import java.io.BufferedReader;
import java.io.File;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import Classes.*;

public class RWFiles implements Runnable {
    static List<Person> list;
    static Semaphore semaphore;
    static File file;
    static File txtfile;
    boolean debug = false;

    public RWFiles(final List<Person> list, final Semaphore semaphore, String filename) {
        RWFiles.list = list;
        RWFiles.semaphore = semaphore;
        txtfile= new File(filename +".txt");
        file =  new File(filename +".obj");
    }

    @Override
    public void run() {
        if(debug) System.out.println("Reading file");
        list= readObjectFile();

        if (debug) System.out.println("RWFILES number of people: "+ list.size());

        while (true) {
            try {
                //only available if used the .release()
                semaphore.acquire();
                semaphore.drainPermits();
                if(debug) System.out.println("Updating the file.");
                saveObjectFile();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }
    }

    public synchronized List<Person> readObjectFile() {
        if (debug) System.out.println("reading people obj");
        try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {

            List<Person> listTemp= (List<Person>) ois.readObject();
            ois.close();

            list.addAll(listTemp);


        } catch (final FileNotFoundException | ClassNotFoundException e) {
            if (debug) System.out.println("error reading obj file people, going back to read txt file");
            readTxtFile();
            saveObjectFile();
        } catch (final IOException e) {
            System.out.println("Error reading object file.");
        }

        if (list.size() == 0) {
            if (debug) System.out.println("error reading obj file people, going back to read txt file");
            readTxtFile();
            saveObjectFile();
        }
        else{
            if (debug) System.out.println("obj file people done!");
        }

        if (debug) System.out.println("done reading people obj");


        return list;
    }

    public synchronized void readTxtFile(){
        if (debug) System.out.println("reading people txt");

        BufferedReader br;
        String st;
        try {
            br = new BufferedReader(new FileReader(txtfile));
            while ((st = br.readLine()) != null) {
                if(debug)System.out.println(st);
                String[] buffer;
                buffer = st.split(",");
                if(debug) System.out.println(Arrays.toString(buffer));
                Person p= new Person(buffer[0], buffer[1], Integer.parseInt(buffer[2]), buffer[3],Integer.parseInt(buffer[4]), buffer[5], Boolean.parseBoolean(buffer[6]));
                list.add(p);

            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        if (debug) System.out.println("done reading people txt");
    }


    public synchronized void saveObjectFile() {
        if(debug) System.out.println("what we are saving");
        if(debug) for(Person p: list) System.out.println(p.toString());
        if(debug) System.out.println("done saving");

        try (FileOutputStream fos = new FileOutputStream(file); ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(list);

        } catch (final FileNotFoundException e) {
            System.out.println("Cant find file to write.");
            // do we need to create a new file?
        } catch (final IOException e) {
            System.out.println("Error writing object file.");
        }

        if(debug) System.out.println("people: updated");

    }

}