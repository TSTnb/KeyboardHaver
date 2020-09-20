package com.tstman.tstgames.inputinjection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    static ServerSocket serverSocket;
    static Socket socket;
    final static int PORT = 19840;
    static String socketFilename;

    public static void main(String[] args) throws InterruptedException {
        try {
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"));
            System.out.println("Server started on " + serverSocket.getInetAddress().toString());
            while (true) {
                socket = serverSocket.accept();
                System.out.println("Client connected from " + socket.getRemoteSocketAddress().toString());
                new Thread(new Connection(socket)).start();
                Thread.sleep(1000);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
