package Server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

class UDPConnection extends Thread {

    Server server;
    boolean openReceiveFile=false;
    public boolean main_server;
    int current_udp;
    int files_udp;
    int send_udp;
    int send_files_udp;
    String send_ip;
    String dir;
    DatagramSocket socket = null;
    DatagramSocket socket_files = null;
    byte[] message;
    int i;

    int max_bytes=1024;

    public UDPConnection(Server server) {
        this.server = server;
        this.start();
    }

    @Override
    public void run() {
        if (Server.debug) System.out.println("UDPConnection Started: ping between servers");


        message = stringToByteArray("ping");

        if (Server.primary.current) {
            dir= Server.primary.dir;
            current_udp = Server.primary.udp;
            send_udp = Server.secondary.udp;
            send_ip = Server.secondary.ip;
            files_udp= Server.primary.udp_files;
            send_files_udp= Server.secondary.udp_files;
        } else {
            dir= Server.secondary.dir;
            current_udp = Server.secondary.udp;
            send_udp = Server.primary.udp;
            send_ip = Server.primary.ip;
            files_udp= Server.secondary.udp_files;
            send_files_udp= Server.primary.udp_files;
        }

        try {
            socket = new DatagramSocket(current_udp);
            socket.setSoTimeout(Server.primary.ping);
        } catch (SocketException se) {
            se.printStackTrace();
        }

        if (sendPing("first ping", send_ip, send_udp)) {
            if (Server.debug) System.out.println("There is no current other server, assuming main");
            Server.main_server = true;
            main_server = true;
        } else {
            if (Server.debug) System.out.println("There is other server available, assuming backup");
            Server.main_server = false;
            main_server = false;
        }

        while (true) ping();


    }

    public void ping() {
        if (main_server) {
            System.out.println("---------Main Server----------");
            while (true) {
                i = 0;
                while (i < Server.primary.retries) {
                    if (sendPing("Main server", send_ip, send_udp)) {
                        i++;
                        System.out.println("UDP: Backup server isn't responding");
                    }
                }
                if (Server.debug) System.out.println("UDP: Backup Server failed! \n Retrying pings");

            }

        } else {

            System.out.println("---------Backup Server----------");
            while (true) {
                i = 0;
                while (i < Server.primary.retries) {
                    if (sendPing("Backup server", send_ip, send_udp)) {
                        i++;
                        System.out.println("UDP: Main server isn't responding");
                    }
                }

                try {
                    main_server = true;
                    Server.main_server = true;

                    System.out.println("UDP: Server failed \nAssuming Main Server Status");
                    if (Server.debug) System.out.println("Restarting the pings as main server!");
                    return;

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }
    }

    private boolean sendPing(String text, String ip, int udp) {
        try {

            Thread.sleep(1000);

            if(openReceiveFile){
                openReceiveFile=false;
                if (Server.debug) System.out.println("OPENRECEIVE IS TRUE SHOULD PRINT");
                openReceiveFile=false;

                byte[] temp = stringToByteArray("open receive file socket");
                DatagramPacket toSend = new DatagramPacket(temp, temp.length, InetAddress.getByName(ip), udp);
                socket.send(toSend);
                if (Server.debug) System.out.println("SENDING OPEN RECEIVE FILE SOCKET");
                if (Server.debug) System.out.println(text + " : ping sent");
            }
            else{
                DatagramPacket toSend = new DatagramPacket(message, message.length, InetAddress.getByName(ip), udp);
                socket.send(toSend);
                if (Server.debug) System.out.println(text + " : ping sent");
            }

            byte[] buffer= new byte [max_bytes];
            DatagramPacket toReceive = new DatagramPacket(buffer, buffer.length);
            socket.receive(toReceive);
            byte[] data = new byte[toReceive.getLength()];
            System.arraycopy(buffer, toReceive.getOffset(), data, 0, toReceive.getLength());

            if(byteArrayToString(data).equals("open receive file socket")){
                receiveFile();
            }

            if(Server.debug) System.out.println(text + " : ping received");

            //System.out.println("UDP: ping successful");

        } catch (SocketTimeoutException ste) {
            return true;
        } catch (IOException ioe) {
            System.out.println("Networking Problems");
        } catch (InterruptedException ie) {
            socket.close();
        }
        return false;
    }

    //UDP for files

    private void receiveFile(){
        new Thread(() -> {
            try {
                socket_files = new DatagramSocket(files_udp);
                socket_files.setSoTimeout(Server.primary.ping);

                DatagramPacket toSend;
                DatagramPacket toReceive;


                boolean done=false;
                boolean okay=true;

                byte [] send;
                byte [] receive = new byte[max_bytes];

                File f = null;
                int total_chunks=0;
                int retries=0;

                System.out.println("file: started copy");


                while(!done && retries < Server.primary.retries) {
                    try {
                        if (Server.debug) System.out.println("file: Receiving filename");

                        toReceive = new DatagramPacket(receive, receive.length);
                        socket_files.receive(toReceive);

                        byte[] data = new byte[toReceive.getLength()];
                        System.arraycopy(receive, toReceive.getOffset(), data, 0, toReceive.getLength());

                        String[] a= byteArrayToString(data).split(" ");
                        if(Server.debug) System.out.println("file: filedir "+ a[0] +", number of chunks " +a[1]);
                        if (a.length == 2) {
                            try{
                                total_chunks= Integer.parseInt(a[1]);
                                f = new File(dir + a[0]);
                                if(Server.debug) System.out.println(f.getAbsolutePath());

                            } catch (NumberFormatException e) {
                                System.out.println("file: Couldnt read filename");
                                okay=false;
                            }

                        }

                        if(okay) send= stringToByteArray("accepted");
                        else send=stringToByteArray("refused");

                        toSend = new DatagramPacket(send, send.length, InetAddress.getByName(send_ip), send_files_udp);
                        socket_files.send(toSend);

                        done = true;

                    } catch (IOException e) {
                        retries++;
                    }
                }

                if(!okay || retries>= Server.primary.retries) {
                    if(Server.debug) System.out.println("file: error, returning back");
                    socket_files.close();
                    return;
                }

                if(Server.debug) System.out.println("file: Reading chunks now");


                int count=0;
                retries=0;

                byte[][] chunks= new byte[total_chunks][max_bytes];

                while(count < total_chunks && retries < Server.primary.retries) {
                    if(Server.debug) System.out.println("file: receiving chunk");

                    try {

                        toReceive = new DatagramPacket(receive, receive.length);
                        socket_files.receive(toReceive);
                        byte[] data = new byte[toReceive.getLength()];
                        System.arraycopy(receive, toReceive.getOffset(), data, 0, toReceive.getLength());

                        chunks[count]=data;

                        send= stringToByteArray("success");


                        toSend = new DatagramPacket(send, send.length, InetAddress.getByName(send_ip), send_files_udp);
                        socket_files.send(toSend);

                        count ++;

                    } catch (IOException e) {
                        retries++;
                    }


                }

                if(retries== Server.primary.retries) {
                    socket_files.close();
                    return;
                }


                assert f != null;
                FileOutputStream fos = new FileOutputStream(f);
                for(count=0; count <total_chunks; count++) fos.write(chunks[count]);
                fos.close();

                System.out.println("file received");



            } catch (IOException se) {
                se.printStackTrace();
            }

        }).start();

        if(Server.debug) System.out.println("leaving thread");

    }

    public void sendFile(String filedir){
        if(Server.debug) System.out.println("INSIDE SEND FILE");
        if(Server.debug)System.out.println(files_udp + " " +send_files_udp + " " +current_udp + " " + send_udp);

        System.out.println("started copy of file");

        openReceiveFile=true;

        new Thread(() -> {
            if(Server.debug) System.out.println("inside thread");

            File f= new File(dir + filedir);
            if(Server.debug)System.out.println(dir + filedir + " or\n " + f.getAbsolutePath());
            if(Server.debug)System.out.println("file exists " + f.exists());
            if(!f.exists()){
                System.out.println("File does not exist THIS SHOULD NEVER HAPPEN");
                return;
            }

            byte[][] chunks;
            try {
                chunks = divideArray(Files.readAllBytes(f.toPath()), max_bytes);
            } catch (IOException e) {
                System.out.println("error splitting bytes");
                return;
            }

            String temp= filedir + " " + chunks.length;
            if(Server.debug) System.out.println(temp);
            DatagramPacket toSend;
            DatagramPacket toReceive;

            boolean done=false;
            boolean okay=true;
            int retries;

            byte [] send;
            byte [] receive = new byte[max_bytes];

            try {

                //System.out.println("connecting socket");

                socket_files = new DatagramSocket(files_udp);
                socket_files.setSoTimeout(Server.primary.ping);

                retries=0;

                while(!done && retries < Server.primary.retries) {
                    if(Server.debug)System.out.println("sending filename");
                    try {
                        Thread.sleep(1000);
                        send= stringToByteArray(temp);
                        toSend = new DatagramPacket(send, send.length, InetAddress.getByName(send_ip), send_files_udp);
                        socket_files.send(toSend);
                        //sending file name and number of chunks

                        toReceive = new DatagramPacket(receive, receive.length);
                        socket_files.receive(toReceive);
                        byte[] data = new byte[toReceive.getLength()];
                        System.arraycopy(receive, toReceive.getOffset(), data, 0, toReceive.getLength());
                        done = true;
                        if(byteArrayToString(data).equals("refused")){
                            okay=false;
                            System.out.println("backup server refused to accept the file");
                        }

                    } catch (InterruptedException | IOException e) {
                        retries++;
                    }
                }


                if(!okay || retries>= Server.primary.retries) {
                    System.out.println("Backup Server is not online, file wasnt sent");
                    socket_files.close();
                    return;
                }

                int count=0;
                retries=0;

                if(Server.debug) System.out.println("file: sending chunks");
                while(count < chunks.length && retries < Server.primary.retries) {
                    try {
                        toSend = new DatagramPacket(chunks[count], max_bytes, InetAddress.getByName(send_ip), send_files_udp);
                        socket_files.send(toSend);

                        toReceive = new DatagramPacket(receive, receive.length);
                        socket_files.receive(toReceive);

                        count ++;
                        retries=0;

                        if(Server.debug) System.out.println("chunk "+ count + ": done");



                    } catch (IOException e) {
                        // didnt receive confirmation from the backup server
                        //retrying
                        if(Server.debug) System.out.println("chunk "+ count + ": retrying");
                        retries++;
                    }
                }

                if(retries== Server.primary.retries){
                    System.out.println("Too many tries to send file, aborting.........");
                    socket_files.close();
                    return;
                }

                System.out.println("file sent to Backup Server");

                socket_files.close();

            } catch (IOException se) {
                se.printStackTrace();
            }

        }).start();



    }

    /**
     * https://stackoverflow.com/questions/3405195/divide-array-into-smaller-parts
     */
    public static byte[][] divideArray(byte[] source, int chunksize) {


        byte[][] ret = new byte[(int)Math.ceil(source.length / (double)chunksize)][chunksize];

        int start = 0;

        for(int i = 0; i < ret.length; i++) {
            ret[i] = Arrays.copyOfRange(source,start, start + chunksize);
            start += chunksize ;
        }

        return ret;
    }

    public static byte[] stringToByteArray(String value){
        // string to byte[]
        return value.getBytes(StandardCharsets.UTF_8);
    }

    public static String byteArrayToString(byte[] bytes){
        // byte[] to string
        return new String(bytes, StandardCharsets.UTF_8);
    }


}

