package com.otway.picasasync.webclient;

/*
    Copyright 2015 Mark Otway

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.otway.picasasync.config.Settings;
import com.otway.picasasync.syncutil.SyncState;
import com.sun.corba.se.impl.orbutil.concurrent.Sync;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import java.awt.*;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.prefs.Preferences;

import javafx.scene.Group;

import javax.swing.*;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.concurrent.Worker.State;
import org.apache.log4j.Logger;

/**
 * Utility class to authenticate using Oauth 2.0.
 *
 * Displays a browser window if no credentials are available in the
 * prefs storage. Once the user has logged in, stores the refresh
 * token in prefs for next time.
 */
public class GoogleOAuth {
    private static final Logger log = Logger.getLogger(GoogleOAuth.class);

    private static final String SUCCESS_CODE = "Success code=";
    private static volatile String token;
    private static final Object lock = new Object();
    private static final String redirectUrl = "urn:ietf:wg:oauth:2.0:oob";
    private static final String scope = "https://picasaweb.google.com/data/";
    private static final JacksonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    private static final Preferences prefs = Preferences.userNodeForPackage(GoogleOAuth.class);
    private static final Dimension frameSize = new Dimension( 650, 500 );
    private final String clientSecret;
    private final String clientId;

    public GoogleOAuth()
    {
        // These come from a source file somewhere not checked into VCS.
        clientSecret = ClientSecretConsts.clientSecret;
        clientId = ClientSecretConsts.clientId;
    }


    public PicasawebClient authenticatePicasa( Settings settings, boolean allowInteractive, SyncState state ) throws IOException, GeneralSecurityException {
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        log.info("Preparing to authenticate via OAuth...");
        Credential cred = null;

        String refreshToken = settings.getRefreshToken();
        if( refreshToken != null )
        {
            // We have a refresh token - so get some refreshed credentials
            cred = getRefreshedCredentials( refreshToken );
        }

        if( cred == null && allowInteractive ) {

            // Either there was no valid refresh token, or the credentials could not
            // be created (they may have been revoked). So run the auth flow

            log.info("No credentials - beginning OAuth flow...");

            state.setStatus( "Requesting Google Authentication...");

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow(
                    httpTransport, jsonFactory, clientId, clientSecret, Collections.singleton(scope));

            String authorizationUrl = flow.newAuthorizationUrl()
                                .setRedirectUri(redirectUrl)
                                .setAccessType("offline")
                                .setApprovalPrompt("force")
                                .build();

            // Display the interactive GUI for the user to log in via the browser
            String code = initAndShowGUI( authorizationUrl, state );

            log.info("Token received from UI. Requesting credentials...");

            // Now we have the code from the interactive login, set up the
            // credentials request and call it.
            GoogleTokenResponse response = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUrl)
                    .execute();

            // Retrieve the credential from the request response
            cred = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setFromTokenResponse(response);

            state.setStatus( "Google Authentication succeeded.");

            log.info("Credentials received - storing refresh token...");

            // Squirrel this away for next time
            settings.setRefreshToken( cred.getRefreshToken() );
            settings.saveSettings();
        }

        if( cred != null ){

            log.info("Building PicasaWeb Client...");

            // Build a web client using the credentials we created
            return new PicasawebClient( cred );
        }

        return null;
    }

    public Credential getRefreshedCredentials(String refreshCode) throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        log.info("Getting access token for refresh token..");

        try {
            GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                    httpTransport, jsonFactory, refreshCode, clientId, clientSecret )
                    .execute();

            return new GoogleCredential().setAccessToken(response.getAccessToken());

        }
        catch( UnknownHostException ex ){
            log.error( "Unknown host. No web access?");
            throw ex;
        }
        catch (IOException e) {
            log.error( "Exception getting refreshed auth: ", e );
        }
        return null;
    }

    private static String initAndShowGUI(final String url, final SyncState state ) {

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
                new ChangeListener<State>() {
                    public void changed(ObservableValue ov, State oldState, State newState) {

                        HandleWebTitleChange( webEngine, frame, newState, state );
                    }
                });

        webEngine.load(url);
    }

    private static void HandleWebTitleChange( WebEngine webEngine, JFrame frame, State newState, SyncState state )
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
    }
}
