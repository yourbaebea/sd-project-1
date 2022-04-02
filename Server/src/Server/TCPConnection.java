package Server;

import Classes.Client;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Objects;
import java.util.Scanner;

class TCPConnection extends Thread {

        DataInputStream in;
        DataOutputStream out;
        Socket clientSocket;
        Client c;
        Server server;

        public TCPConnection (Socket aClientSocket, Server server) {
            c = new Client();
            this.server=server;
            try{
                clientSocket = aClientSocket;
                in = new DataInputStream(clientSocket.getInputStream());
                out = new DataOutputStream(clientSocket.getOutputStream());
                this.start();
            }
            catch(IOException e){System.out.println("TCPConnection error:" + e.getMessage());}
        }

        @Override
        public void run() {
            System.out.println("TCPConnection Started for new client");

            String text;
            String temp;
            try {
                while(true){
                    //an echo server
                    String command = in.readUTF();
                    if(Server.debug) System.out.println("TCP: new command: "+ command);

                    temp= handleCommand(command);
                    if(c.username==null || c.username.equals("")) text= "(not logged in)";
                    else text=c.username;
                    if(Server.debug) System.out.println("\n\nTCP: client "+ text +" tried "+ temp );

                    if(!Objects.equals(temp, "") && !temp.equals("permsnotok")) out.writeUTF(temp);
                }
            } catch(EOFException e) {
                System.out.println("EOF:" + e);
            } catch(IOException e) {
                System.out.println("IO:" + e);
            }
        }

    /**
     *
     * @param command
     * 1 user pass
     * 2 newpass
     * 4 directorytolist
     * 5 newdir
     * 6 currentdirfiles
     * @return
     * @throws RemoteException
     */

        public String handleCommand(String command) throws IOException {


            String[] ops= command.split(" ");
            switch (ops[0]) {
                case "1":
                    return server.loginPerson(c,ops[1], ops[2]);
                case "2":
                    return server.changePassword(c, ops[1]);
                case "4":
                    return server.listFiles(c);
                case "5":
                    if(ops.length==1) {return server.changeDir(c, "");}
                    return server.changeDir(c, ops[1]);
                case "8":
                    String perms = server.checkPerms(c);
                    if (perms.equals("success#")) {
                        out.writeUTF("permsok");
                        new Thread(() -> {
                            try (ServerSocket fileSocket = new ServerSocket(0);) { //criar socket files
                                out.writeInt(fileSocket.getLocalPort()); //mandar port do file socket poelo socket de comandos
                                out.flush();
                                Socket clientFileSocket = fileSocket.accept(); //bloqueante; à espera de socket conecte do lado do client
                                OutputStream out_file = clientFileSocket.getOutputStream();
                                DataOutputStream out = new DataOutputStream(clientFileSocket.getOutputStream());
                                String aux = server.downloadFile(c, ops[1], out_file, out);

                                if (aux.equals("success#")) System.out.println("File sent!");
                                else {
                                    System.out.println("Some error downloading file");
                                }

                                fileSocket.close();
                                clientFileSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }).start();
                        return "";
                    }
                    out.writeUTF("permsnotok");
                    return "permsnotok";

                case "9":
                    String perms2 = server.checkPerms(c);
                    if (perms2.equals("success#")) {
                    out.writeUTF("permsok");
                    new Thread(()->{
                        try (ServerSocket fileSocket = new ServerSocket(0);) { //criar socket files
                            out.writeInt(fileSocket.getLocalPort()); //mandar port do file socket pelo socket de comandos
                            out.flush();
                            Socket clientFileSocket = fileSocket.accept(); //bloqueante; à espera de socket conecte do lado do client
                            InputStream in_file = clientFileSocket.getInputStream();
                            String aux= server.uploadFile(c, in_file, ops[1]);

                            if(aux.equals("success#")) System.out.println("File uploaded to server!");
                            else{
                                System.out.println("Some error uploading file");
                            }
                            fileSocket.close();
                            clientFileSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }}).start();
                    return "";
                    }
                    out.writeUTF("permsnotok");
                    return "permsnotok";
                default:
                    System.out.println("Command not found");
            }

            return "handleCommand was not sucessfull";

        }


        public void tcp_file_send() {

        }
}


