package info.brandonharris;

import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by brandon on 5/28/17.
 */
public class Server {
    private static int SERVICE_PORT = 8523;
    private static int BUFFER_SIZE = 1024*8;
    private String rootDirectory = "Speed Sync";

    public Server(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public static long sizeLocalPath(File path) {
        long size = 0;
        if (path.isDirectory()) {
            for (File file: path.listFiles()) {
                size += sizeLocalPath(file);
            }
        } else {
            size += path.length();
        }
        return size;
    }

    public static String toHex(byte[] bytes) {
        return String.format("%x", new BigInteger(1, bytes));
    }

    public static String getChecksum(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");

        FileInputStream fileInputStream = new FileInputStream(file);
        messageDigest.reset();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = 0;

        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            messageDigest.update(buffer, 0, bytesRead);
        }
        byte[] digest = messageDigest.digest();
        return toHex(digest);
    }

    private static class ClientThread extends Thread {
        private static class Functions {
            final private static int SEND_FILE = 1;
            final private static int LIST_FILES = 2;
            final private static int RECEIVE_FILE = 3;
            final private static int GET_SIZE = 4;
            final private static int GET_ACTION_LIST = 5;
        }

        private Socket socket;
        private String rootDirectory;

        public ClientThread(Socket socket, String rootDirectory) {
            this.socket = socket;
            this.rootDirectory = rootDirectory;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = socket.getInputStream();

                int function = inputStream.read();

                if (function == ClientThread.Functions.SEND_FILE) {
                    int nameSize = inputStream.read();

                    byte[] nameBytes = new byte[nameSize];

                    int bytesRead = 0;

                    while (bytesRead < nameSize) {
                        bytesRead += inputStream.read(nameBytes, bytesRead, nameSize - bytesRead);
                    }

                    String filename = new String(nameBytes);

                    //ADDED FOR SECURITY
                    if (filename.contains("..")) {
                        socket.close();
                        return;
                    }
                    //END

                    File file = new File(new File(rootDirectory), filename);
                    if (file.getParentFile() != null) {
                        if (!file.getParentFile().exists()) {
                            file.getParentFile().mkdirs();
                        }
                    }
                    FileOutputStream fileOutputStream = new FileOutputStream(file);

                    byte[] buffer = new byte[BUFFER_SIZE];
                    bytesRead = 0;
                    int highestBytesRead = 0;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                        if (bytesRead > highestBytesRead) {
                            highestBytesRead = bytesRead;
                        }
                    }
                    fileOutputStream.close();
                } else if (function == ClientThread.Functions.LIST_FILES) {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(listPath(new File(rootDirectory)).getBytes());
                    outputStream.close();
                } else if (function == ClientThread.Functions.RECEIVE_FILE) {
                    int nameSize = inputStream.read();

                    byte[] nameBytes = new byte[nameSize];

                    int bytesRead = 0;

                    while (bytesRead < nameSize) {
                        bytesRead += inputStream.read(nameBytes, bytesRead, nameSize - bytesRead);
                    }

                    String name = new String(nameBytes);

                    OutputStream outputStream = socket.getOutputStream();
                    FileInputStream fileInputStream = new FileInputStream(new File(rootDirectory, name));

                    byte[] buffer = new byte[BUFFER_SIZE];
                    bytesRead = 0;

                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    outputStream.close();
                    fileInputStream.close();
                } else if (function == ClientThread.Functions.GET_SIZE) {
                    long size = sizeLocalPath(new File(rootDirectory));
                    byte[] sizeBytes = ByteBuffer.allocate(8).putLong(size).array();
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(sizeBytes);
                    outputStream.close();
                } else if (function == ClientThread.Functions.GET_ACTION_LIST) {
                    byte[] bytes = new byte[4];
                    inputStream.read(bytes);

                    int length = ByteBuffer.wrap(bytes).getInt();

                    byte[] lastModifiedStringBytes = new byte[length];
                    int bytesRead = 0;

                    System.out.println(length);

                    while (bytesRead < length) {
                        bytesRead += inputStream.read(lastModifiedStringBytes, bytesRead, length - bytesRead);
                    }
//                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//
//                    while ((bytesRead = inputStream.read(buffer)) != -1) {
//                        byteArrayOutputStream.write(buffer, 0, bytesRead);
//                    }
//
//                    byteArrayOutputStream.close();

                    StringBuilder fileListBuilder = new StringBuilder();
                    StringBuilder actionListBuilder = new StringBuilder();

                    System.out.println(new String(lastModifiedStringBytes));

                    String[] split = new String(lastModifiedStringBytes).split("\n\n");

                    Map<String, Long> phoneLastModifiedMap = new HashMap<>();
                    Map<String, String> phoneChecksumMap = new HashMap<>();

                    if (split.length > 1) {
                        String[] newSplit = split[1].split("\n");
                        String[] phoneLastModified = new String[newSplit.length];
                        String[] phoneChecksum = new String[newSplit.length];
                        for (int i = 0; i < newSplit.length; i++) {
                            String[] _split = newSplit[i].split(",");
                            phoneLastModified[i] = _split[0];
                            phoneChecksum[i] = _split[1];
                        }

                        String[] phoneFiles = split[0].split("\n");

                        for (int i = 0; i < phoneFiles.length; i++) {
                            String filename = phoneFiles[i];
                            phoneLastModifiedMap.put(filename, Long.valueOf(phoneLastModified[i]));
                            phoneChecksumMap.put(filename, phoneChecksum[i]);
                        }
                    }

                    long localSize = 0;

                    String[] localFiles = listPath(new File(rootDirectory)).split("\n");

                    for (String filename: localFiles) {
                        File file = new File(rootDirectory, filename);


                        if (!file.isDirectory()) { //Added for the instance where there are no files on the server
                            if (phoneLastModifiedMap.containsKey(filename)) {
                                if (getChecksum(file).equals(phoneChecksumMap.get(filename))) {
                                    phoneLastModifiedMap.remove(filename);
                                } else {
                                    if (file.lastModified() > phoneLastModifiedMap.get(filename)) {
                                        fileListBuilder.append(filename);
                                        fileListBuilder.append("\n");
                                        actionListBuilder.append("d\n");
                                        localSize += file.length();
                                        phoneLastModifiedMap.remove(filename);
                                    } else if (file.lastModified() < phoneLastModifiedMap.get(filename)) {
                                        fileListBuilder.append(filename);
                                        fileListBuilder.append("\n");
                                        actionListBuilder.append("u\n");
                                        phoneLastModifiedMap.remove(filename);
                                    } else {
                                        phoneLastModifiedMap.remove(filename);
                                    }
                                }
                            } else {
                                fileListBuilder.append(filename);
                                fileListBuilder.append("\n");
                                actionListBuilder.append("d\n");
                                localSize += file.length();
                            }
                        }
                    }

                    for (String file: phoneLastModifiedMap.keySet()) {
                        fileListBuilder.append(file);
                        fileListBuilder.append("\n");
                        actionListBuilder.append("u\n");
                    }

                    OutputStream outputStream = socket.getOutputStream();

                    fileListBuilder.append("\n");
                    fileListBuilder.append(actionListBuilder);

                    fileListBuilder.append("\n");
                    fileListBuilder.append(localSize);

                    System.out.println(fileListBuilder.toString());

                    outputStream.write(fileListBuilder.toString().getBytes());
                    outputStream.flush();

                    inputStream.close();
                    outputStream.close();
                    socket.close();
                }

                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static String listPath(File path) {
        StringBuilder stringBuilder = new StringBuilder();

        for (File subPath: path.listFiles()) {
            if (subPath.isDirectory()) {
                String name = subPath.getName();
                String[] pathList = listPath(subPath).split("\n");
                for (String item: pathList) {
                    if (!item.isEmpty()) {
                        stringBuilder.append(name);
                        stringBuilder.append("/");
                        stringBuilder.append(item);
                        stringBuilder.append("\n");
                    }
                }
            } else {
                stringBuilder.append(subPath.getName());
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    private ServerSocket serverSocket;
    private boolean running = true;

    public void stop() throws IOException {
        running = false;
        serverSocket.close();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(SERVICE_PORT);

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (running) {
                        ClientThread clientThread = new ClientThread(serverSocket.accept(), rootDirectory);
                        clientThread.start();
                        System.out.println("Client connected!");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
