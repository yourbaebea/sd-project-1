package Client;

import java.awt.image.FilteredImageSource;
import java.net.*;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.Scanner;
import java.io.*;
import java.util.regex.Matcher;

public class Client implements Runnable {
    public boolean debug;
    public String current_dir="";
    private final Scanner sc;
    private String receive="";
    private String send = "";
    private String data = null;
    private String ipprimary;
    private String ipsecondary;
    private int commandsocketprimary;
    private int commandsocketsecondary;
    private Socket fsocket = null;
    private Socket cmndsocket = null;
    private String workingserver = "";
    int max_retries;
    DataOutputStream out;
    DataInputStream in;
    DataInputStream filein;
    DataOutputStream fileout;
    Thread thread;
    boolean stop = false;
    String fakeusername;
    String fakepassword;
    boolean firstTime = true;

    public Client(boolean debug) throws IOException {
        this.debug=debug;
        sc = new Scanner(System.in);
        portsip();
        thread = new Thread(this);
        thread.start();
    }

    public void portsip() throws IOException {
        //ler ficheiro e obter ports dos comandos e ficheiros primarios e secundario
        Properties prop = new java.util.Properties();
        FileInputStream input = null;

        input = new FileInputStream("client.properties");
        prop.load(input);


        //primary server

        ipprimary = prop.getProperty("server_p_ip");
        commandsocketprimary = Integer.parseInt(prop.getProperty("server_p_port"));
        max_retries= Integer.parseInt(prop.getProperty("max_retries"));

        //secondary server

        ipsecondary = prop.getProperty("server_s_ip");
        commandsocketsecondary = Integer.parseInt(prop.getProperty("server_s_port"));

    }

    @Override
    public void run() {
        System.out.print("Connecting with server....");
        int tries=0;


        while(tries<max_retries && !stop) {
            try (Socket temp = new Socket(ipprimary, commandsocketprimary)) {
                cmndsocket = temp;
                workingserver = "primary";
                toDo();
            } catch (IOException e) {
                try (Socket temp = new Socket(ipsecondary, commandsocketsecondary)) {
                    cmndsocket = temp;
                    workingserver = "secondary";
                    toDo();
                } catch (IOException ex) {
                    tries++;
                    if(debug) System.out.println("Retrying to connect");
                }
            }
        }

    }

    public void toDo() throws IOException {
        if (stop) return;
        boolean a = true;
        if(firstTime) {
            System.out.println("done.");
            if (debug) System.out.println("SOCKET=" + cmndsocket);
            System.out.println("Welcome to UCDrive! Please Login.");
        }
        while (a) {
            if(firstTime) {
                System.out.print("Username: ");
                String username = sc.nextLine();
                fakeusername = username;
                System.out.print("Password: ");
                String password = sc.nextLine();
                fakepassword = password;
                send = "1 " + username + " " + password + " ";
                //System.out.println(send);
            }
            send = "1 " + fakeusername + " " + fakepassword + " ";
            String[] aux = writeRead(cmndsocket);
            if (aux[0].equals("success")) {
                a = false;
                current_dir = aux[1];
                firstTime = false;
                mainMenu(cmndsocket);
            } else {
                System.out.println("Wrong credentials. Please try again.");
                this.firstTime = true;
            }

        }
    }


    public void mainMenu(Socket s) throws IOException {
        if (stop) return;
        boolean a = true;
        boolean b=true;
        while(a) {
            //Runtime.getRuntime().exec("clear");
            printCurrent();
            Scanner sc = new Scanner(System.in);
            String input = sc.nextLine();
            b = true;
            while(b) {
                if (inputProtection(input)) b = false;
                else {
                    System.out.println("Invalid input. Please try again.");
                    input = sc.nextLine();
                }
            }
            int op = Integer.parseInt(input);
            switch (op) {
                case 2:
                    //alterar password: pedir user, pedir passe antiga, pedir passe nova
                    System.out.println("Old password:");
                    sc.nextLine();
                    System.out.println("New password:");
                    String newPassword = sc.nextLine();
                    send = "2 " + newPassword + " ";
                    if(writeRead(s) == null) return;
                    break;
                case 3:
                    //confirar portos e endereços do servidor
                    System.out.println("Primary server new IP:");
                    String ipPrimario = sc.nextLine();
                    System.out.println("Secondary server new IP:");
                    String ipSecundario = sc.nextLine();
                    System.out.println("Primary server new port:");
                    String portoPrimario = sc.nextLine();
                    System.out.println("Secondary server new port:");
                    String portoSecundario = sc.nextLine();
                    ipprimary = ipPrimario;
                    ipsecondary = ipSecundario;
                    commandsocketprimary = Integer.parseInt(portoPrimario);
                    commandsocketsecondary = Integer.parseInt(portoSecundario);
                    try (Socket temp = new Socket(ipprimary, commandsocketprimary)) {
                        cmndsocket = temp;
                        workingserver = "primary";
                        toDo();
                    } catch (IOException e) {
                        System.out.println("IO:" + e.getMessage());
                        try (Socket temp = new Socket(ipsecondary, commandsocketsecondary)) {
                            cmndsocket = temp;
                            workingserver = "secondary";
                            toDo();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    break;

                case 4:
                    send = "4";
                    String[] result = writeRead(s);
                    if(result == null) return;
                    System.out.println(result[1]);
                    break;

                case 5:
                    String aux2="";
                    if(current_dir.equals("")) aux2="\\";
                    System.out.println("server dir: S:" + aux2 + current_dir + " \t\t\t( .. to go back)");
                    String newdir = sc.nextLine();
                    String dir="";

                    boolean aux=true;

                    if (newdir.equals("..")) {
                        if (current_dir.equals("")) {
                            System.out.println("Already in server dir, cant go back");
                            aux=false;
                        }
                        else{
                            dir = Paths.get(current_dir).getParent().toString();
                        }

                    }
                    else {
                        dir = current_dir + "\\"+ newdir;
                    }

                    //System.out.println("directory to send "+ dir + " .");

                    if(aux){
                        send = "5 " + dir;
                        String[] tempdir = writeRead(s);
                        if(tempdir == null) return;
                        if(tempdir.length==1){
                            current_dir="";
                            //System.out.println("DIR IS EMPTY");
                        }
                        else{
                            if (!tempdir[0].equals("error")){
                                current_dir = tempdir[1];
                                //System.out.println(current_dir);
                            }
                            else{
                                System.out.println(tempdir[1]);
                            }
                        }
                    }

                    if(debug) System.out.println("updated dir: "+ current_dir);

                    break;
                case 6:
                    File temp = new File(System.getProperty("user.dir"));
                    System.out.println("Files in local dir " + System.getProperty("user.dir"));
                    String childs[] = temp.list();
                    if (childs == null || childs.length == 0) {
                        System.out.println("local dir is empty");
                        break;
                    }
                    for (String child : childs) {
                        System.out.println(" - " + child);
                    }
                    break;

                case 7:
                    System.out.println("Insert FULL path");
                    String localdir = sc.nextLine();
                    File localf = new File(localdir);
                    if (localf.isDirectory()) {
                        if(debug) System.out.println("dir was updated");
                        System.setProperty("user.dir", localf.getAbsolutePath());
                    }
                    else{
                        System.out.println("Couldnt change directory");
                    }
                    break;

                case 8:
                    //download ficheiros
                    System.out.println("What file do you want to download?");
                    String filename = sc.nextLine();
                    send = "8 " + filename;
                    out.writeUTF(send);

                    String perms = in.readUTF();
                    if (!perms.equals("permsnotok")) {
                        int port = in.readInt();
                        if (workingserver.equals("primary")) {
                            try (Socket tempp = new Socket(ipprimary, port)) {
                                fsocket = tempp;
                                downloadFile(fsocket, filename);
                            }
                        }
                        if (workingserver.equals("secondary")) {
                            try (Socket tempp = new Socket(ipsecondary, port)) {
                                fsocket = tempp;
                                downloadFile(fsocket, filename);
                            }
                        }
                    }
                    else{
                        System.out.println("You do not have permission to download from this folder.");
                    }
                    break;

                case 9:
                    //upload ficheiros
                    System.out.println("What file do you want to upload?");
                    String fname = sc.nextLine();
                    File fileupload = new File(System.getProperty("user.dir") + "\\" + fname);
                    if (fileupload.exists()) { //caso o ficheiro exista, envio pedido de upload ao servidor
                        send = "9 " + fname;
                        out.writeUTF(send);
                        String perms2 = in.readUTF();
                        if(!perms2.equals("permsnotok")) {
                            int port2 = in.readInt(); // ler port em que server abriu o socket de ficheiros
                            if (workingserver.equals("primary")) {
                                try (Socket tempp = new Socket(ipprimary, port2)) { //criar socket no port recebido
                                    fsocket = tempp;
                                    uploadFile(fsocket, fileupload);
                                }
                            }

                            if (workingserver.equals("secondary")) {
                                try (Socket tempp = new Socket(ipsecondary, port2)) {
                                    fsocket = tempp;
                                    uploadFile(fsocket, fileupload);
                                }
                            }
                        }
                        else{
                            System.out.println("You do not have permission to upload to this folder.");
                            break;
                        }
                    }
                    else System.out.println("That file does not exist.");

                    break;


                case 10:
                    //sair: fechar threads e socket
                    a = false;
                    System.out.println("Goodbye!");
                    cmndsocket.close();
                    out.close();
                    in.close();
                    stop = true;
                    return;
            }
        }
    }

    String[] writeRead(Socket s) throws IOException {
        if(stop) return null;
        in = new DataInputStream(s.getInputStream());
        out = new DataOutputStream(s.getOutputStream());


        try { //escreve no socket
            out.writeUTF(send);
        } catch (IOException e) {
            //e.printStackTrace();
            if(workingserver.equals("secondary")) {
                try (Socket temp = new Socket(ipprimary, commandsocketprimary)) {
                    cmndsocket = temp;
                    workingserver = "primary";
                    toDo();
                } catch (IOException r) {
                    //ex.printStackTrace();
                }
            }
            if(workingserver.equals("primary")){
                try (Socket temp = new Socket(ipsecondary, commandsocketsecondary)) {
                    cmndsocket = temp;
                    workingserver = "secondary";
                    toDo();
                } catch (IOException r) {
                    //ex.printStackTrace();
                }
            }

        }
        if(stop) return null;
        try { // le do socket
            data = in.readUTF();
        } catch (IOException e) {
            //e.printStackTrace();
            if(workingserver.equals("secondary")) {
                try (Socket temp = new Socket(ipprimary, commandsocketprimary)) {
                    cmndsocket = temp;
                    workingserver = "primary";
                    toDo();
                } catch (IOException r) {
                    //ex.printStackTrace();
                }
            }
            if(workingserver.equals("primary")){
                try (Socket temp = new Socket(ipsecondary, commandsocketsecondary)) {
                    cmndsocket = temp;
                    workingserver = "secondary";
                    toDo();
                } catch (IOException r) {
                    //ex.printStackTrace();
                }
            }
        }
        //if(debug) System.out.println("Received: " + data );

        String[] print = data.split("#");
        return print;

    }

    boolean inputProtection(String input){

        if (input == null) {
            return false;
        }
        try {
            int num = Integer.parseInt(input);
            if (num <= 10 && num >= 2) return true;
            else return false;
        } catch (NumberFormatException nfe) {
            return false;
        }

    }


    void downloadFile(Socket fsocket, String filename) throws IOException {
        DataInputStream inmessage = new DataInputStream(fsocket.getInputStream());
        String message = inmessage.readUTF();
        if (message.equals("File exists")) {
            byte[] content = new byte[5000];
            FileOutputStream fs = new FileOutputStream(filename);
            BufferedOutputStream bos = new BufferedOutputStream(fs);
            InputStream filein = fsocket.getInputStream();

            int bytesread = 0; // num of bytes read in one read call
            while ((bytesread = filein.read(content)) != -1) bos.write(content, 0, bytesread);
            bos.flush();
            fsocket.close();
            System.out.println("File saved successfully!\n");
        } else System.out.println("File does not exist!\n");
    }
    void uploadFile(Socket fsocket, File fileupload) throws IOException {

        FileInputStream fs = new FileInputStream(fileupload);
        BufferedInputStream bis = new BufferedInputStream(fs);
        OutputStream out_file = fsocket.getOutputStream();  //stream que envia o content do ficheiro
        byte [] filecontent;
        long filelen = fileupload.length();
        long currentlen = 0;
        while(currentlen != filelen) { //se tamanho da copia for diferente do do ficheiro
            int chunk = 5000;
            if (filelen - currentlen >= chunk) { //se o que falta enviar for maior ou igual ao tamanho do chunk
                currentlen += chunk;
            } else {
                chunk = (int) (filelen - currentlen); //tamanho do chunk fica igual ao que falta
                currentlen = filelen; //sai loop
            }
            filecontent = new byte[chunk]; // cria byte array do tamanho do chunk
            bis.read(filecontent, 0, chunk);
            out_file.write(filecontent); //envia chunk
        }
        out_file.flush();
        System.out.println("File sent!\n");
        fsocket.close();
    }


    public void printCurrent(){
        String aux= "";
        if(current_dir.equals("")) aux="\\";
        System.out.println("\t\t\t\t\t\t\t-----------Menu-----------");
        System.out.println("server dir:\t\tS:"+ aux+ current_dir);
        System.out.println("local dir:\t\t"+ System.getProperty("user.dir"));
        System.out.println("2.Change password\t\t\t3.Configure IP or port\t\t\t4.List files in remote dir\n5.Change server dir\t\t\t6.List files in local dir\t\t7.Change client dir\n8.Download file\t\t\t\t9.Upload file\t\t\t\t\t10.Leave");
    }

    public static void main(String args[]) throws IOException {

        new Client(args.length>0);
        // create socket

    }
}