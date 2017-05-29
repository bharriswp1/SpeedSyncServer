package info.brandonharris;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.rmi.server.ExportException;

/**
 * Created by brandon on 5/28/17.
 */
public class MainGui {
    private JLabel statusLabel;
    private JPanel mainPanel;
    private JButton changeDirectoryButton;

    public static String getUserDataDirectory() {
        return System.getProperty("user.home") + File.separator + ".speedsync";
    }

    public MainGui() {
        changeDirectoryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Choose root directory");
                fc.setMultiSelectionEnabled(false);
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                int returnVal = fc.showOpenDialog(mainPanel);

                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();

                    try {
                        File rootDirectoryFile = new File(getUserDataDirectory(), "rootDirectory");
                        if (rootDirectoryFile.getParentFile() != null) {
                            if (!rootDirectoryFile.getParentFile().exists()) {
                                rootDirectoryFile.getParentFile().mkdirs();
                            }
                        }
                        if (!rootDirectoryFile.exists()) {
                            rootDirectoryFile.createNewFile();
                        }
                        FileWriter fileWriter = new FileWriter(rootDirectoryFile);
                        fileWriter.write(file.getAbsolutePath());
                        fileWriter.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        String rootDirectory = "Speed Sync";

        try {
            File rootDirectoryFile = new File(getUserDataDirectory(), "rootDirectory");
            BufferedReader bufferedReader = new BufferedReader(new FileReader(rootDirectoryFile));
            rootDirectory = bufferedReader.readLine();
            bufferedReader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("Speed Sync Server");
        MainGui mainGui = new MainGui();
        mainGui.mainPanel.setPreferredSize(new Dimension(300, 200));
        mainGui.mainPanel.updateUI();
        frame.setContentPane(mainGui.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        final Server server = new Server(rootDirectory);
        mainGui.statusLabel.setText("Not running");
        frame.pack();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                super.windowClosing(windowEvent);
                try {
                    server.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            server.start();
            mainGui.statusLabel.setText("Running");
        } catch (Exception e) {
            mainGui.statusLabel.setText("Could not start");
        }
        frame.setVisible(true);
    }
}
