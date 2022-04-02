package Server;

import Classes.Client;
import Classes.Person;
import Util.RWFiles;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;

/** This RMI server and backup server work via an UDP pinging sequence to detect if any of them is working.
 *  The servers will always be contacting each other and in case the main server fails the backup server
 *  will await for some reconnection retries and after if the problem wasn't solved within the attempts range it
 *  assumes the main server status making the this main server the backup one when it comes back.
 *  Number of retries,timeouts between pings and udp ports can be changed in the server.properties file.
 */
public class Server{

    public static boolean debug;
    public static boolean main_server = true;




    private final String ERROR="error#";
    private final String MESSAGE="message#";
    private final String SUCCESS="success#";

    public class ServerDetails{
        public boolean current=false;
        public String server_name;
        public String ip;
        public int port;
        public String dir;
        public int udp;
        public int udp_files;
        public int ping;
        public int retries;
        public int max_tcp;

        public ServerDetails copy() {
            ServerDetails copy = new ServerDetails();
            copy.server_name=this.server_name;
            copy.ip=this.ip;
            copy.port=this.port;
            copy.dir=this.dir;
            copy.udp=this.udp;
            copy.udp_files=this.udp_files;
            copy.ping=this.ping;
            copy.retries=this.retries;
            copy.max_tcp=this.max_tcp;

            return copy;

        }

    }

    // For communication between servers
    public static ServerDetails primary;
    public static ServerDetails secondary;
    String server_dir;
    UDPConnection connection = null;
    public String server_ip;
    public int server_port;

    //private final static ArrayList<Person> list = new ArrayList<>();
    public static final List<Person> list = Collections.synchronizedList(new ArrayList<Person>());
    private static final Semaphore semaphore= new Semaphore(0);
    private final static String filename="people";
    // read write thread for people.obj
    static Thread rw_thread;


    //@java.io.Serial
    //private static final long serialVersionUID = 5138225684096988535L;


    /* ************************************** RMI setup functions ************************** */

    /**
     * create Server, read files set UDP and TCP
     */
    public Server(String server_ip, int server_port, boolean debug) throws InterruptedException {
        super();



        this.server_ip=server_ip;
        this.server_port= server_port;
        Server.debug =debug;

        if(debug) System.out.println("Server dir"+ System.getProperty("user.dir"));

        serverConfig();

        if(debug) System.out.println(server_dir);


        setConnectionUDP();

        Thread.sleep(1000);

        synchronized (list){
            RWFiles myThread = new RWFiles(list,semaphore, server_dir +"\\", filename);
            rw_thread = new Thread(myThread);
            rw_thread.start();
        }



        //TODO
        setConnectionTCP();

    }


    /**
     * read configs from the server.properties file
     */
    public void serverConfig() {
        Properties prop = new Properties();
        FileInputStream input = null;

        try {
            input = new FileInputStream("server.properties");
            prop.load(input);
            ServerDetails temp= new ServerDetails();

            temp.retries= Integer.parseInt(prop.getProperty("retries"));
            temp.ping = Integer.parseInt(prop.getProperty("ping"));
            temp.max_tcp= Integer.parseInt(prop.getProperty("max_tcp"));

            //primary server

            temp.server_name=  prop.getProperty("server_p_name");
            temp.ip = prop.getProperty("server_p_ip");
            temp.port= Integer.parseInt(prop.getProperty("server_p_port"));
            temp.dir= System.getProperty("user.dir") + "\\" + prop.getProperty("server_p_dir");
            temp.udp= Integer.parseInt(prop.getProperty("udp_p"));
            temp.udp_files= Integer.parseInt(prop.getProperty("udp_p_files"));

            this.primary= temp.copy();

            //secondary server

            temp.server_name=  prop.getProperty("server_s_name");
            temp.ip = prop.getProperty("server_s_ip");
            temp.port= Integer.parseInt(prop.getProperty("server_s_port"));
            temp.dir= System.getProperty("user.dir") + "\\" + prop.getProperty("server_s_dir");
            temp.udp= Integer.parseInt(prop.getProperty("udp_s"));
            temp.udp_files= Integer.parseInt(prop.getProperty("udp_s_files"));

            this.secondary=temp.copy();

            temp=null;

            if(this.server_ip.equals(secondary.ip) && this.server_port== secondary.port){
                secondary.current=true;
                server_dir= secondary.dir;
            }
            else{
                if (!this.server_ip.equals(primary.ip) || this.server_port != primary.port) {
                    //default is always to the
                    System.out.println("IP or PORT is not valid, default is primary: " + primary.ip + " " + primary.port);
                }
                primary.current=true;
                server_dir= primary.dir;

            }

            input.close();
            if(debug) System.out.println("server.properties: done");

        } catch (IOException e) {
            if(debug) System.out.println("server.properties: error in config file");
            e.printStackTrace();
        }
    }


    public void setConnectionUDP() throws InterruptedException {
        Thread.sleep(1000);

        System.out.println("Server Started....");

        if (this.connection == null) {
            this.connection = new UDPConnection(this); // this function uses this.server

        }

        if(debug) System.out.println("UDPConnection sucess, server main is : " + main_server );
    }


    public void setConnectionTCP() {
        int server_port;
        if(primary.current) server_port= primary.port;
        else{
            server_port= secondary.port;
        }
        new Thread(() -> {

            while (true) {
                if(main_server) {
                    try (ServerSocket listenSocket = new ServerSocket(server_port)) {
                        System.out.println("TCP: listening on port " + server_port);
                        if (debug) System.out.println("LISTEN SOCKET=" + listenSocket);

                        while (true) {
                            Socket clientSocket = listenSocket.accept(); // BLOQUEANTE
                            System.out.println("TCP: listening on port " + server_port);
                            if (debug) System.out.println("CLIENT_SOCKET (created at accept())=" + clientSocket);
                            System.out.println("TCP: new client connected, ip: " + clientSocket.getInetAddress().toString() + " port: " + clientSocket.getPort());
                            new TCPConnection(clientSocket, this);
                        }
                    } catch (IOException e) {
                        System.out.println("Listen:" + e.getMessage());
                    }
                }
                else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }).start();

    }


    /* ************************************** TCP functions ************************** */


    public synchronized String loginPerson(Client c,String username, String password) {
        for(Person p: list){
            if(username.equals(p.getUsername())){
                if(p.checkPassword(password)){
                    c.logged=true;
                    c.username=username;
                    c.setAdmin(p.admin);
                    c.setCurrent("\\"+ username);
                    return SUCCESS + c.getCurrent();
                }
                return ERROR + "invalid password";
            }
        }
        return ERROR + "user not in the system";
    }

    public String changePassword(Client c, String password){
        if (!c.logged) return ERROR + "client is not logged in";
        for (Person p : this.list) {
            if (c.username.equals(p.getUsername())) {
                if (p.changePassword(password)) {
                    semaphore.release();
                    try {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e) {
                        //do nothing
                    }
                    connection.sendFile(filename+".obj");
                    return SUCCESS;
                }
                return ERROR;
            }
        }
        return ERROR; //"changePassword: error";
    }

    public String listFiles(Client c) throws RemoteException {
        if(!c.logged) return ERROR + "client is not logged in";

        File f = new File(server_dir+ c.getCurrent());
        if(f.exists()) {
            if(!f.isDirectory()) return ERROR + "dir given is not a directory";

            String[] files = f.list();
            if(files.length==0) return MESSAGE + "Directory is empty";
            StringBuilder output = new StringBuilder(1000);
            output.append("Files in remote dir " + c.getCurrent() +"\n");
            for(String file: files){
                output.append(" - ").append(file).append("\n");
            }
            return MESSAGE + output;
        }

        return ERROR + "dir does not exist";

    }

    public String changeDir(Client c, String dir) throws RemoteException {
        if(!c.logged) return ERROR + "client is not logged in";

        String[] temp= dir.split(Matcher.quoteReplacement(System.getProperty("file.separator")));

        if(temp.length==0){
            if(c.isAdmin()){
                c.setCurrent(dir);
                return SUCCESS; //esta no server folder
            }
            return ERROR + "not in client home folder";
        }
        //System.out.println("temp size"+ temp.length);
        //for(String t: temp) System.out.println(t);
        String value="";
        for(String s : temp) {
            if(s != null && !s.equals("")) {
                value=s;
                break;
            }
        }
        if(!c.isAdmin() && !value.equals(c.username)){
            return ERROR + "not in client home folder";
        }

        File f = new File(server_dir + dir);
        if(f.exists()) {
            if(!f.isDirectory()) return ERROR + "dir given is not a directory";

            c.setCurrent(dir);
            return MESSAGE+ c.getCurrent();
        }

        return ERROR + "dir does not exist";

    }

    public String checkPerms(Client c){
        if(!c.logged) return ERROR + "client is not logged in";
        String[] temp= c.getCurrent().split(Matcher.quoteReplacement(System.getProperty("file.separator")));

        if(temp.length==0){
            return ERROR + "user doesnt have permission to dowload from this folder";
        }
        String value="";
        for(String s : temp) {
            if(s != null && !s.equals("")) {
                value=s;
                break;
            }
        }
        if(!value.equals(c.username)){
            return ERROR + "not in client home folder, user doesnt have permission to dowload from this folder";
        }
        return SUCCESS;
    }

    public String downloadFile(Client c, String op, OutputStream out_file, DataOutputStream out) throws IOException {

        File f = new File(server_dir + c.getCurrent() +"\\"+ op);
        System.out.println(f);
        if(f.exists()) System.out.println("ficheiro existe");
        if(f.exists()) { // se ficheiro existir na diretoria do server
            out.writeUTF("File exists");
            FileInputStream fs = new FileInputStream(server_dir+ c.getCurrent() +"\\"+ op);
            BufferedInputStream bis = new BufferedInputStream(fs);
            byte [] filecontent;
            long filelen = f.length();
            long currentlen = 0;
            while(currentlen != filelen) { //se tamanho atual for diferente do do ficheiro
                int chunk = 5000;
                if (filelen - currentlen >= chunk) { //se o que falta enviar for maior ou igual ao tamanho do chunk
                    currentlen += chunk;
                } else {
                    chunk = (int) (filelen - currentlen); //tamanho do chunk fica igual ao que falta
                    currentlen = filelen; //sai loop
                }
                filecontent = new byte[chunk];
                bis.read(filecontent, 0, chunk); //copia chunk para
                out_file.write(filecontent);
            }
            out_file.flush();



            return SUCCESS;
        }
        else {
            out.writeUTF("File doesnt exist");
            return ERROR + "That file does not exist.";
        }

    }

    public synchronized String uploadFile(Client c, InputStream in_file, String filename) throws IOException {
        if(!c.logged) return ERROR + "client is not logged in";

        String[] temp= c.getCurrent().split(Matcher.quoteReplacement(System.getProperty("file.separator")));

        if(temp.length==0){
            return ERROR + "user doesnt have permission to upload to this folder";
        }

        String value="";
        for(String s : temp) {
            if(s != null && !s.equals("")) {
                value=s;
                break;
            }
        }

        if(!value.equals(c.username)){
            return ERROR + "not in client home folder, user doesnt have permission to upload from this folder";
        }


        File f = new File(server_dir+ c.getCurrent() +"\\"+ filename);
        byte[] content = new byte[5000];
        FileOutputStream fs = new FileOutputStream(f);
        BufferedOutputStream bos = new BufferedOutputStream(fs);

        int bytesread = 0; // num of bytes read in one read call
        while ((bytesread = in_file.read(content)) != -1) bos.write(content, 0, bytesread);
        bos.flush();

        connection.sendFile(c.getCurrent() +"\\"+ filename);

        return SUCCESS;
    }


    /*************************************** RMI Server main ***************************/

    public static void main(String[] args) throws RemoteException,InterruptedException {
        //System.getProperties().put("java.security.policy", "policy.all");
        //System.setSecurityManager(new RMISecurityManager());
        //args[0].equals("primary");

        Server server = new Server(args[0], Integer.parseInt(args[1]), (args.length>2));

    }

}
