package com.otway.picasasync.webclient;

import com.otway.picasasync.syncutil.SyncState;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.*;

/**
 * Created by markotway on 29/04/2016.
 */
public class OAuthGUI
{
    private static final Logger log = Logger.getLogger(OAuthGUI.class);
    private static final Dimension frameSize = new Dimension( 650, 500 );
    private static final String SUCCESS_CODE = "Success code=";
    private static final Object lock = new Object();
    private static volatile String token;

    public String initAndShowGUI(final String url, final SyncState state ) {

        log.info("Displaying OAuth Login frame...");

        final JFrame frame = new JFrame("Authenticate Picasa");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.getContentPane().setLayout(null); // do the layout manually

        final JFXPanel fxPanel = new JFXPanel();

        frame.add(fxPanel);
        frame.setVisible(true);

        fxPanel.setSize(frameSize);
        fxPanel.setLocation(0,0);

        frame.getContentPane().setPreferredSize(frameSize);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(screenSize.width / 2 - fxPanel.getSize().width / 2, screenSize.height / 2 - fxPanel.getSize().height / 2);

        frame.pack();
        frame.setResizable(false);

        String authToken = "";

        try {
            Platform.runLater(new Runnable() {
                public void run() {
                    log.info( "Initialising login frame on background thread.");
                    synchronized ( lock ){
                        initWebView(fxPanel, frame, url, state );
                    }
                }
            });

            synchronized ( lock ) {
                lock.wait();

                log.info( "User closed window.");

                authToken = token;
            }
        }
        catch( Exception ex ){
            log.error("Unexpected exception opening interactive login screen.");
        }

        return authToken;
    }

    /* Creates a WebView and fires up google.com */
    private static void initWebView(final JFXPanel fxPanel, final JFrame frame, String url, final SyncState state ) {
        log.info( "Initialising WebView on GUI thread...");

        Group group = new Group();
        Scene scene = new Scene(group);
        fxPanel.setScene(scene);

        WebView webView = new WebView();

        group.getChildren().add(webView);
        webView.setMinSize(frameSize.width, frameSize.height);
        webView.setMaxSize(frameSize.width, frameSize.height);
        webView.setZoom( 0.80 );

        // Obtain the webEngine to navigate
        final WebEngine webEngine = webView.getEngine();

        webEngine.getLoadWorker().stateProperty().addListener(
                new ChangeListener<Worker.State>() {
                    public void changed(ObservableValue ov, Worker.State oldState, Worker.State newState) {

                        HandleWebTitleChange( webEngine, frame, newState, state );
                    }
                });

        webEngine.load(url);
    }

    private static void HandleWebTitleChange(WebEngine webEngine, JFrame frame, Worker.State newState, SyncState state )
    {
        if (newState == Worker.State.SUCCEEDED) {
            log.info("Page refreshed: " + webEngine.getTitle());

            frame.setTitle(webEngine.getTitle());

            if( webEngine.getTitle().startsWith( SUCCESS_CODE ) ) {

                synchronized ( lock ) {
                    token = webEngine.getTitle().substring( SUCCESS_CODE.length() );
                    state.setStatus( "Login successful.");
                    lock.notify();
                }

                log.info("Hiding login panel.");

                frame.setVisible( false );
            }
        }
        else if( newState == Worker.State.FAILED ) {
            log.error("Error loading Google Auth Page!");
            state.setStatus( "Unable to load Google Authentication page.");
            state.cancel( true );
            frame.setVisible(false);
        }
    }}
