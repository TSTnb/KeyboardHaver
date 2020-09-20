package com.tstman.tstgames.inputinjection;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;

public class Client implements Runnable {
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public DataOutputStream toServer;
    volatile boolean connected;

    byte command;
    int slot, x, y;

    volatile PipedReader pipedReader;
    volatile PipedWriter pipedWriter;

    public Client(PipedReader pipedReader, PipedWriter pipedWriter) {
        this.pipedReader = pipedReader;
        this.pipedWriter = pipedWriter;
    }

    protected boolean sendPassword() throws IOException {
        BufferedReader responseReader = new BufferedReader(new InputStreamReader(inputStream));
        OutputStreamWriter passwordWriter = new OutputStreamWriter(outputStream);
        passwordWriter.write(Connection.secretPhrase + "\n");
        passwordWriter.flush();
        final String response = responseReader.readLine();
        return response.equals("correct");
    }

    public void fixOutputStreamIfOutIsNull() {
        if (!connected) {
            try {
                connectClient();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("tstman", "Unable to start the client", e);
            }
        }
        if (toServer == null) {
            toServer = new DataOutputStream(outputStream);
        }
    }

    public void downAtPoint(int x, int y) throws IOException {
        if (!connected) {
            connectClient();
        }
        toServer.writeByte('d');
        toServer.writeInt(x);
        toServer.writeInt(y);
        toServer.flush();
    }

    public void upAtPoint(int x, int y) throws IOException {
        if (!connected) {
            connectClient();
        }
        toServer.writeByte('u');
        toServer.writeInt(x);
        toServer.writeInt(y);
        toServer.flush();
    }

    public void exit() throws IOException {
        if (connected) {
            toServer.writeByte('e');
            toServer.flush();
        }
    }

    protected void relayCommand() throws IOException {
        command = (byte) pipedReader.read();
        toServer.writeByte(command);
        slot = pipedReader.read();
        toServer.writeInt(slot);
        x = pipedReader.read();
        toServer.writeInt(x);
        y = pipedReader.read();
        toServer.writeInt(y);
        toServer.flush();
    }

    public void connectClient() throws IOException {
        connected = false;
        socket = new Socket("127.0.0.1", Server.PORT);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        if (!sendPassword()) {
            throw new IOException("It was an incorrect password");
        }
        toServer = new DataOutputStream(outputStream);
        connected = true;
        Log.i("tstgames", "Initialized connection to input server");
    }

    public void run() {
        try {
            connectClient();
            while (true) {
                if (!connected) {
                    connectClient();
                }
                relayCommand();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
