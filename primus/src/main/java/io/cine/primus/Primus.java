package io.cine.primus;

import android.app.Activity;
import android.os.Handler;
import android.util.Log;

import com.koushikdutta.async.ByteBufferList;
import com.koushikdutta.async.DataEmitter;
import com.koushikdutta.async.callback.CompletedCallback;
import com.koushikdutta.async.callback.DataCallback;
import com.koushikdutta.async.http.AsyncHttpClient;
import com.koushikdutta.async.http.WebSocket;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Created by thomas on 9/21/14.
 */
public class Primus {
    private static final String VERSION = "0.0.3";
    private static final String TAG = "Primus";
    private final static String dictionary = "abcdefghijklmnopqrstuvwxyz0123456789_";
    private final Activity activity;
    private final String baseUrl;
    private final String url;
    private final Queue<String> messages;
    private int reconnectAttempt;
    public WebSocket mWebSocket;
    private Handler mHandler;
    private PrimusDataCallback dataCallback;
    private PrimusOpenCallback openCallback;
    private PrimusWebSocketCallback websocketCallback;
    private boolean autoReconnect;
    private int currentTimerRun;
    private static final String INTENTIONAL_SERVER_END = "\"primus::server::close\"";

    Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            currentTimerRun++;
            // send a heartbeat every 10 seconds
            if (!websocketIsOpen()) {
                currentTimerRun = 0;
                Log.v(TAG, "Reconnecting to primus");
                reconnect();
            }
            if (currentTimerRun >= 10) {
                currentTimerRun = 0;
                String ping = "primus::ping::" + System.currentTimeMillis();
                // Log.v(TAG, "SENDING PING - " + ping);
                Log.v(TAG, websocketIsOpen() ? "socket open" : "socket closed");
                sendRawToWebSocket(ping);
            } else {
//                Log.v(TAG, "did not send ping: "+currentTimerRun);
            }
            scheduleHeartbeat();
        }
    };
    private boolean connecting;

    private Primus(Activity activity, String baseUrl) {
        this.activity = activity;
        this.baseUrl = baseUrl;
        url = generatePrimusUrl();
        Log.v(TAG, url);
        mHandler = new Handler();
        currentTimerRun = 0;
        messages = new LinkedList<String>();
        connecting = true;
        autoReconnect = true;
        reconnectAttempt = 0;
        mHandler.post(createNewWebsocket);
    }

    public static Primus connect(Activity activity, String url) {
        return new Primus(activity, url);
    }

    public void setDataCallback(PrimusDataCallback callback) {
        this.dataCallback = callback;
    }

    public void setWebSocketCallback(PrimusWebSocketCallback callback) {
        this.websocketCallback = callback;
    }

    public void setOpenCallback(PrimusOpenCallback callback) {
        this.openCallback = callback;
    }

    private String getProtocolFromUrl() {
        if(url.contains("https://")){
            return "wss";
        } else{
            return "ws";
        }
    }

    Runnable createNewWebsocket = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "connecting to: " + url);
            AsyncHttpClient.getDefaultInstance().websocket(url, getProtocolFromUrl(), new AsyncHttpClient.WebSocketConnectCallback() {

                @Override
                public void onCompleted(Exception ex, WebSocket returnedWebsocket) {
                    Log.v(TAG, "completed");
                    connecting = false;

                    if (ex != null) {
                        Log.v(TAG, "GOT CONNECTION EXCEPTION");
                        ex.printStackTrace();
                        reconnect();
                        return;
                    }
                    reconnectAttempt = 0;
                    mWebSocket = returnedWebsocket;

                    mWebSocket.setDataCallback(new DataCallback() {
                        @Override
                        public void onDataAvailable(DataEmitter dataEmitter, ByteBufferList byteBufferList) {
                            Log.v(TAG, "I got some bytes!");
                            // note that this data has been read
                            byteBufferList.recycle();
                        }
                    });

                    mWebSocket.setClosedCallback(new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception e) {
                            if (e != null) {
                                e.printStackTrace();
                                return;
                            }
                            //on network error
                            Log.v(TAG, "ws: closedCallback onCompleted");

                            if (autoReconnect) {
                                reconnect();
                            } else {
                                cancelHeartbeat();
                            }
                        }
                    });
                    mWebSocket.setEndCallback(new CompletedCallback() {
                        @Override
                        public void onCompleted(Exception e) {
                            if (e != null) {
                                e.printStackTrace();
                                return;
                            }

                            Log.d(TAG, "ws: endCallback onCompleted");
                        }
                    });
                    mWebSocket.setStringCallback(new WebSocket.StringCallback() {
                        public void onStringAvailable(String s) {
                            Log.v(TAG, "got string: " + s);
                            try {
                                JSONObject response;
                                if (s.startsWith("o")) {
                                    if (openCallback != null) {
                                        openCallback.onOpen();
                                    }
                                } else if (s.startsWith("a")) {
                                    Log.v(TAG, "received array");
                                    s = s.substring(1);
                                    JSONArray r = new JSONArray(s);
                                    Log.v(TAG, "parsed array");
                                    String data = r.getString(0);
                                    //it's a message from within primus
                                    if (data.charAt(0) == '"') {
                                        Log.v(TAG, "Primus message");

                                        if (data.equals(INTENTIONAL_SERVER_END)) {
                                            autoReconnect = false;
                                            Log.v(TAG, "SERVER CLOSED");
                                        }
                                        // a json message
                                    } else {
                                        response = new JSONObject(data);
                                        Log.v(TAG, "Parsed user message");
                                        if (dataCallback != null) {
                                            dataCallback.onData(response);
                                        }
                                    }

                                } else {
                                    Log.v(TAG, "received something else");
                                    return;
                                }
                            } catch (JSONException e) {
                                Log.v(TAG, "UNABLE TO PARSE RESPONSE");
                                e.printStackTrace();
                            }
                        }
                    });
                    scheduleHeartbeat();
                    if (websocketCallback != null) {
                        websocketCallback.onWebSocket(mWebSocket);
                    }

                }

            });

        }
    };

    private void scheduleHeartbeat() {
        mHandler.postDelayed(heartbeatTask, 1000);
    }

    private void cancelHeartbeat() {
        mHandler.removeCallbacks(heartbeatTask);
    }

    private char randomCharacterFromDictionary() {
        int rand = (int) (Math.random() * dictionary.length());
        return dictionary.charAt(rand);
    }

    private String randomStringOfLength(int length) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < length; i++) {
            s.append(randomCharacterFromDictionary());
        }
        return s.toString();
    }

    // "ws://cine-io-signaling.herokuapp.com/primus/211/b9__ftym/websocket"
    private String generatePrimusUrl() {
        Random r = new Random();
        int server = r.nextInt(1000);
        String connId = randomStringOfLength(8);
        return baseUrl + "/" + server + "/" + connId + "/websocket";
    }

    private void sendRawToWebSocket(String data) {
        data = "[\"" + data + "\"]";
        Log.v(TAG, data);
        scheduleMessage(data);
    }

    public void end() {
        autoReconnect = false;
        cancelHeartBeatAndCloseWebSocket();
    }

    private void cancelHeartBeatAndCloseWebSocket(){
        cancelHeartbeat();
        if (mWebSocket != null) {
            mWebSocket.end();
        }
    }

    public void send(final JSONObject j) {
        try {
            Log.d(TAG, "QUEUEING TO SEND: " + j.toString());
            scheduleMessage(j.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void scheduleMessage(String j) {
        messages.add(j);
        processScheduledMessages();
    }

    private void processScheduledMessages() {
//        unfortunately there seems to be a broken pipe issue.
        boolean brokenPipe = false;
        if (websocketIsOpen()) {
            while (!brokenPipe && !messages.isEmpty()) {
                String message = messages.remove();
                Log.d(TAG, "ACTUALLY SENDING: " + message);
                actuallySendMessage(message);
            }
        } else if(autoReconnect){
            reconnect();
        } else {
        }
    }

    private boolean websocketIsOpen() {
        if (mWebSocket == null) {
            return false;
        }
        return mWebSocket.isOpen();
    }

    private void reconnect() {
        Log.v(TAG, "wanting to reconnect");
        // if we're already connecting, don't try to autoReconnect;
        if (connecting) {
            return;
        }
        reconnectAttempt += 1;
        connecting = true;
        cancelHeartBeatAndCloseWebSocket();
        long millisecondsToDelay = calculateRetryDelay();
        Log.v(TAG, "Reconnecting in " + millisecondsToDelay +" milliseconds");
        mHandler.postDelayed(createNewWebsocket, millisecondsToDelay);
    }

    // http://dthain.blogspot.nl/2009/02/exponential-backoff-in-distributed.html
    private long calculateRetryDelay() {
        int minDelay = 500;
        int factor = 2;
        long millisecondsToDelay = Math.round((Math.random() + 1) * minDelay * Math.pow(factor, reconnectAttempt));
        return millisecondsToDelay;
    }

    private void actuallySendMessage(final String message) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                mWebSocket.send(message);
            }
        });
    }

    public static interface PrimusWebSocketCallback {
        public void onWebSocket(WebSocket websocket);
    }

    public static interface PrimusOpenCallback {
        public void onOpen();
    }

    public static interface PrimusDataCallback {
        public void onData(JSONObject jsonObject);
    }
}
