import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import org.jnativehook.keyboard.NativeKeyEvent;
import org.jnativehook.keyboard.NativeKeyListener;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
// no wildcard imports :)


public class KpsWindow extends JFrame {

    //Volatile allows read from main mem and change to main mem to deal with concurrency
    private volatile boolean active;
    private volatile boolean closed;
    private volatile boolean addWindowOpened;
    private volatile boolean rmWindowOpened;
    private volatile boolean keyVisOn;

    // Components
    private JFrame mainWindow;
    private JPanel keyPanel;
    private JPanel keyVisPanel;
    private JButton keyVisButton;
    private JLabel infoLabel;
    private JPanel infoPanel;
    private JPanel buttonPanel;
    private JButton keyButton;
    private JButton removeButton;
    private Map<Character, KeyLabel> keyLabelMap;
    private Timer keyVisTimer; //

    private volatile long startTime;
    private volatile long lastPressTime;
    private BufferedImage buttonUp;
    private BufferedImage buttonDown;

    private KeyManager manager;
    private ArrayList<Character> keyOrder;

    private static final String fileName = "src/layout.cfg";
    private static final File cfgFile = new File(fileName);


    // constants
    public static final int RECT_INIT_H = 5;
    public static final int RECT_INIT_W = 45;
    public static final int RECT_X_OFFSET = (64-RECT_INIT_W)/2;
    public static final int RECT_Y_OFFSET = 290;
    public static final int TIMEOUT_TIME = 2500;
    public static final int RECT_RM_DIST = 325;
    public static final int RECT_FADE_DIST = 20;
    public static final int RECT_TRAVEL_DIST = 12;
    public static final Color DEFAULT_COLOR = Color.GRAY;

    public KpsWindow(){
        mainWindow = new JFrame("javaKPS");
        mainWindow.setLayout(new BorderLayout());
        manager = new KeyManager();
        buttonPanel = new JPanel();
        keyButton = new JButton();
        Key.buildNativeKeyMap();
        keyOrder = new ArrayList<>();
        keyLabelMap = new HashMap<>();
        keyPanel = new JPanel();
        infoPanel = new JPanel();
        removeButton = new JButton();
        keyVisPanel = new JPanel(){
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);

                g.setColor(Color.WHITE);
                for(Map.Entry<Character,KeyLabel> entry:keyLabelMap.entrySet())
                {
                    KeyLabel kl = entry.getValue();
                    KeyVisRectangle held = kl.getCurRect();
                    ArrayList<KeyVisRectangle> rects = kl.getRects();
                    for(int i = rects.size()-1;i>=0;i--)
                    {
                        KeyVisRectangle rect = rects.get(i);
                        if(rect==null) continue; // idk if i need this but dont want nulls, however my code basically guarantees no nulls are added but just for safety
                        rect.setX(kl.getX()+RECT_X_OFFSET);
                        rect.setY(rect.getY()-RECT_TRAVEL_DIST);
                        if(rect != held)
                        {
                            rect.travel(RECT_TRAVEL_DIST);
                        }
                        else
                        {
                            rect.setH(rect.getH()+RECT_TRAVEL_DIST);
                        }
                        int travel = rect.getTotalTraveled();
                        if(travel < RECT_RM_DIST) { // optimally remove right as it leaves frame so less mem usage, but whatever
                            boolean fadeReset = false;
                            if(travel > RECT_FADE_DIST)
                            {
                                float a = (float)(travel - RECT_FADE_DIST);
                                a *= 255/(float)(RECT_RM_DIST-RECT_FADE_DIST);
                                int alpha = (int)(255-a); // opacity
                                g.setColor(new Color(255,255,255, alpha));
                                fadeReset = true;
                            }
                            g.drawRect(rect.getX(), rect.getY(), rect.getW(), rect.getH());
                            g.fillRect(rect.getX(), rect.getY(), rect.getW(), rect.getH());
                            if(fadeReset)
                            {
                                g.setColor(Color.WHITE);
                            }
                        }
                        else
                        {
                            rects.remove(i); // i hope java's gc actually drops those from mem or thats annoying
                        }
                    }

                }
            }
        };
        keyVisButton = new JButton();
        keyVisTimer = new Timer(16, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (keyVisOn) keyVisPanel.repaint();
            }
        });

        // button images
        try {
           buttonUp = ImageIO.read(new File("assets/button_up.png"));
           buttonDown = ImageIO.read(new File("assets/button_down.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // read from layout.cfg file here
        // gets all the current keys that u want to track from config file, basically save layout

        try {
            if (cfgFile.createNewFile()){ // makes a new cfg file if none.
                // does something in .createNewFile() already
            }
            else
            {
                // MAKE IT CHECK IF EMPTY
                if(cfgFile.length() != 0) {
                    Scanner sc = new Scanner(cfgFile);
                    while (sc.hasNextLine()) {
                        String temp = sc.nextLine();
                        if (temp.length() > 0){
                            char c = Character.toUpperCase(temp.charAt(0));
                            addKey(c);
                         }
                    }
                    sc.close();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        //space focuses and selects a bt to be pressed when space is pressed. instead i just make it do nothing.
        InputMap im = (InputMap) UIManager.get("Button.focusInputMap");
        im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
        im.put(KeyStroke.getKeyStroke("released SPACE"), "none");

        keyVisPanel.setPreferredSize(new Dimension(buttonPanel.getPreferredSize().width,300));
        keyVisPanel.setBackground(DEFAULT_COLOR);

        infoPanel.setPreferredSize(new Dimension(100,100));
        infoLabel = new JLabel("<html><font color='white'>KPS:<br>BPM:<br>TOTAL KEYS:</font></html>", SwingConstants.CENTER);
        infoPanel.add(infoLabel, BorderLayout.PAGE_END);
        infoPanel.setBackground(DEFAULT_COLOR);
        infoLabel.setHorizontalTextPosition(JLabel.CENTER);
        infoLabel.setVerticalTextPosition(JLabel.CENTER);

        keyPanel.setBackground(DEFAULT_COLOR);

        mainWindow.setLocationRelativeTo(null);
        mainWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        mainWindow.getContentPane().setBackground(DEFAULT_COLOR);
        mainWindow.add(keyPanel);
        mainWindow.add(infoPanel, BorderLayout.EAST);
        mainWindow.add(buttonPanel,BorderLayout.SOUTH);
        buttonPanel.setBackground(DEFAULT_COLOR);
        buttonPanel.add(keyButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(keyVisButton);

        // Get the logger for jnativehook and set the level to off.
        Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(Level.OFF);

        try { // global screen for nativeKeyPresses
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
        GlobalScreen.addNativeKeyListener(new KeyTracker());

        //key add bt
        keyButton.setText("Add a Key");
        keyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(addWindowOpened) return;
                addWindowOpened = true;
                JFrame keyFrame = new JFrame("Add a Key");
                keyFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JLabel l = new JLabel("Press a key to add it");
                keyFrame.add(new JPanel().add(l));
                keyFrame.setSize(300, 200);
                keyFrame.setLocationRelativeTo(null);

                NativeKeyListener tempListen = new NativeKeyListener() {

                    @Override
                    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
                        int key = nativeKeyEvent.getKeyCode();
                        Character c = Key.NATIVE_KEY_MAP.get(key);
                        if(c == null) l.setText("Bad key");
                        else if(addKey(c))
                        {
                            addWindowOpened = false;
                            keyFrame.dispose();
                            GlobalScreen.removeNativeKeyListener(this);
                        }
                        else
                        {
                            l.setText("Already added");
                        }
                    }
                    @Override
                    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {}

                    @Override
                    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {}
                };

                GlobalScreen.addNativeKeyListener(tempListen);
                keyFrame.addWindowListener(new WindowAdapter() { // closing window event
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        addWindowOpened = false;
                        GlobalScreen.removeNativeKeyListener(tempListen);
                    }
                });
                keyFrame.setVisible(true);
            }
        }); // opens a new window that prompts for a key

        //key rm bt
        removeButton.setText("Remove a Key");
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(rmWindowOpened) return;
                rmWindowOpened = true;
                JFrame keyFrame = new JFrame("Remove a Key");
                keyFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JLabel l = new JLabel("Press a key to remove it");
                keyFrame.add(new JPanel().add(l));
                keyFrame.setSize(300, 200);
                keyFrame.setLocationRelativeTo(null);


                NativeKeyListener tempListen = new NativeKeyListener() {

                    @Override
                    public void nativeKeyPressed(NativeKeyEvent nativeKeyEvent) {
                        int key = nativeKeyEvent.getKeyCode();
                        Character c = Key.NATIVE_KEY_MAP.get(key);
                        if(c == null) l.setText("Bad Key");
                        else if(removeKey(c))
                        {
                            rmWindowOpened = false;
                            keyFrame.dispose();
                            GlobalScreen.removeNativeKeyListener(this);
                        }
                        else
                        {
                            l.setText("Not a key being tracked");
                        }
                    }
                    @Override
                    public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {}

                    @Override
                    public void nativeKeyReleased(NativeKeyEvent nativeKeyEvent) {}
                };
                GlobalScreen.addNativeKeyListener(tempListen);

                keyFrame.addWindowListener(new WindowAdapter() { // closing window event
                    @Override
                    public void windowClosing(WindowEvent windowEvent) {
                        rmWindowOpened = false;
                        GlobalScreen.removeNativeKeyListener(tempListen);
                    }
                });
                keyFrame.setVisible(true);
            }
        }); // opens a new window that prompts for a key

        //key vis toggle bt
        keyVisButton.setText("Key Visualization Toggle: " +  ((keyVisOn) ? "ON":"OFF"));
        keyVisButton.addActionListener(e -> {
            if(keyVisOn)
            {
                mainWindow.remove(keyVisPanel);
                keyVisTimer.stop();
            }
            else
            {
                mainWindow.add(keyVisPanel,BorderLayout.NORTH);
                keyVisTimer.start();
            }
            keyVisOn = !keyVisOn; // keyVisOn is only modified here so a non-atomic operation seems fine to do
            keyVisButton.setText("Key Vis Toggle: " +  ((keyVisOn) ? "ON":"OFF"));
            mainWindow.pack();
        });


        mainWindow.addWindowListener(new WindowAdapter() { // closing window event
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                closed = true;
            }
        });
        mainWindow.pack();
        mainWindow.setVisible(true);


    }

    public void run()
    {
        while(!closed) // main loop that runs when the program is running
        {
            if(active) {
                long curTime = new Date().getTime();
                manager.updateTime(curTime - startTime);
                updateLabel();
                if(curTime - lastPressTime > TIMEOUT_TIME)
                {
                    active = false;
                    manager.resetSession();
                    updateLabel();
                }
            }
        }

        // save to file here
        try {
            FileWriter write = new FileWriter(cfgFile);
            for(Character k:keyOrder)
            {
                write.write(k + System.lineSeparator());
            }
            write.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        keyVisTimer.stop();
        System.exit(0);
    }
    private void updateLabel()
    {
        if(active)infoLabel.setText("<html><font color='white'>KPS:" + (int)manager.getKps() + "<br>" +
                "BPM:" + manager.getBpm() + "<br>" +
                "TOTAL KEYS:" + manager.getTotalPresses()+ "<br>" +
                "</font></html>");
        else infoLabel.setText("<html><font color='white'>MAX KPS:" + (int)manager.getMaxKps() + "<br>" +
                "BPM:" + manager.getBpm() + "<br>" +
                "TOTAL KEYS:" + manager.getTotalPresses()+ "<br>" +
                "</font></html>");
    }

    private boolean removeKey(char c)
    {
        c = Character.toUpperCase(c);
        for(int i = 0; i < keyOrder.size();i++)
        {
            if(keyOrder.get(i) == c)
            {
                keyOrder.remove(i);
                KeyLabel kl = keyLabelMap.remove(c);
                manager.removeKey(c);
                keyPanel.remove(kl);
                mainWindow.repaint();
                mainWindow.pack();
                return true;
            }
        }
        return false;
    }

    private boolean addKey(char c)
    {
        c = Character.toUpperCase(c);
        Key key = manager.addKey(c);
        if(!keyLabelMap.containsKey(c)) {
            KeyLabel kl = new KeyLabel(new ImageIcon(buttonUp), key);
            kl.setForeground(Color.WHITE);
            keyLabelMap.put(c, kl);
            keyPanel.add(kl);
            keyOrder.add(c);
            mainWindow.pack();
            return true;
        }
        return false;
    }
    public class KeyTracker implements NativeKeyListener {
        /*
        when key is pressed, if the kl's rect is null, create a new rect, set that as the kl's rect. If not null
        hold the rect in place and extend it
        when key is released make that kl's rect null
         */
        @Override
        public void nativeKeyPressed(NativeKeyEvent e) {

            if(!active)
            {
                active = true;
                startTime = new Date().getTime();
            }
                lastPressTime = new Date().getTime();
                int key = e.getKeyCode();
                Character c = Key.NATIVE_KEY_MAP.get(key);
                KeyLabel kl = keyLabelMap.get(c);
                if(kl != null)
                {
                    kl.setIcon(new ImageIcon(buttonDown));
                    lastPressTime = new Date().getTime();
                    if(kl.getCurRect() == null && keyVisOn)
                    {
                        kl.setCurRect(new KeyVisRectangle(kl.getX()+RECT_X_OFFSET,kl.getY()+RECT_Y_OFFSET,RECT_INIT_H,RECT_INIT_W));// so much constants lol
                    }
                }
        }


        @Override
        public void nativeKeyReleased(NativeKeyEvent e) {
            int key = e.getKeyCode();
            Character c = Key.NATIVE_KEY_MAP.get(key);

            KeyLabel kl = keyLabelMap.get(c);

            if(kl != null)
            {
                manager.pressKey(c);
                kl.setText(manager.getKeyInfo(c));
                kl.setIcon(new ImageIcon(buttonUp));
                kl.setCurRect(null);
            }
        }

        @Override
        public void nativeKeyTyped(NativeKeyEvent nativeKeyEvent) {
            //useLess
        }

    }
}

