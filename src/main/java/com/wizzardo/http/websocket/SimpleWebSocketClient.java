package com.wizzardo.http.websocket;

import com.wizzardo.tools.misc.BoyerMoore;
import com.wizzardo.tools.misc.UncheckedThrow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: wizzardo
 * Date: 03.10.14
 */
public class SimpleWebSocketClient extends Thread {
    private InputStream in;
    private OutputStream out;
    private byte[] buffer = new byte[1024];
    private volatile int bufferOffset = 0;

    public static class Request {
        private URI uri;

        public Request(String url) throws URISyntaxException {
            URI u = new URI(url.trim());
            if (u.getScheme().equals("wss"))
                throw new IllegalArgumentException("wss not implemented yet");

            if (!u.getScheme().equals("ws"))
                throw new IllegalArgumentException("url must use ws scheme");
            uri = u;
        }

        private Map<String, String> params = new HashMap<>();
        private Map<String, String> headers = new HashMap<>();

        public Request param(String key, String value) {
            try {
                params.put(URLEncoder.encode(key, "utf-8"), URLEncoder.encode(value, "utf-8"));
            } catch (UnsupportedEncodingException e) {
                throw UncheckedThrow.rethrow(e);
            }
            return this;
        }

        public Request header(String key, String value) {
            headers.put(key, value);
            return this;
        }

        public String build() {
            StringBuilder sb = new StringBuilder();
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            sb.append("GET ").append(path.isEmpty() ? "/" : path);
            boolean amp = query != null;
            if (amp || !params.isEmpty())
                sb.append('?');
            if (amp)
                sb.append(query);
            for (Map.Entry<String, String> param : params.entrySet()) {
                if (amp)
                    sb.append('&');
                else
                    amp = true;
                sb.append(param.getKey()).append('=').append(param.getValue());
            }

            sb.append(" HTTP/1.1\r\n");
            sb.append("Host: ").append(uri.getHost());
            if (uri.getPort() != 80)
                sb.append(":").append(uri.getPort());
            sb.append("\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==\r\nSec-WebSocket-Version: 13\r\n");
            sb.append("Origin: http://").append(uri.getHost());
            if (uri.getPort() != 80)
                sb.append(":").append(uri.getPort());
            sb.append("\r\n");

            for (Map.Entry<String, String> header : headers.entrySet())
                sb.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            sb.append("\r\n");

            return sb.toString();
        }

        public String host() {
            return uri.getHost();
        }

        public int port() {
            return uri.getPort();
        }
    }

    public SimpleWebSocketClient(Request request) throws URISyntaxException, IOException {
        handShake(request);
    }

    public SimpleWebSocketClient(String url) throws URISyntaxException, IOException {
        handShake(new Request(url));
    }

    private void handShake(Request request) throws IOException {
        Socket s = new Socket(request.host(), request.port());
        in = s.getInputStream();
        out = s.getOutputStream();

        out.write(request.build().getBytes());
        out.flush();

        BoyerMoore boyerMoore = new BoyerMoore("\r\n\r\n");
        int response = 0;
        while ((bufferOffset += in.read(buffer, bufferOffset, buffer.length - bufferOffset)) != -1) {
//            System.out.println(new String(bytes, 0, r));
            if ((response = boyerMoore.search(buffer, 0, bufferOffset)) >= 0)
                break;
        }

        System.out.println(new String(buffer, 0, response));
        bufferOffset = 0;
    }

    @Override
    public void run() {
        while (true) {
            try {
                waitForMessage();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void waitForMessage() throws IOException {
        Message message = new Message();
        while (!message.isComplete()) {
            message.add(readFrame());
        }
        onMessage(message);
    }

    private Frame readFrame() throws IOException {
        while (!Frame.hasHeaders(buffer, 0, bufferOffset)) {
            bufferOffset += in.read(buffer, bufferOffset, buffer.length - bufferOffset);
        }
        Frame frame = new Frame();
        int r = frame.read(buffer, 0, bufferOffset);
        bufferOffset -= r;
        if (bufferOffset != 0)
            System.arraycopy(buffer, r, buffer, 0, bufferOffset);

        if (frame.isComplete())
            return frame;
        else {
            while (!frame.isComplete()) {
                frame.read(in);
            }
            return frame;
        }
    }

    public void onMessage(Message message) {
    }

    public void send(Message message) throws IOException {
        for (Frame frame : message.getFrames()) {
            frame.mask();
            frame.write(out);
        }
    }

    public void send(String s) throws IOException {
        send(s.getBytes());
    }

    public void send(byte[] data) throws IOException {
        send(data, 0, data.length);
    }

    public void send(byte[] data, int offset, int length) throws IOException {
        Frame frame = new Frame();
        frame.setData(data, offset, length);
        frame.mask();
        frame.write(out);
    }

    public static void main(String[] args) throws URISyntaxException, IOException {
//        byte[] testMessage = new byte[]{-127, 28, 70, 114, 105, 32, 79, 99, 116, 32, 48, 51, 32, 50, 48, 58, 49, 55, 58, 48, 49, 32, 77, 83, 75, 32, 50, 48, 49, 52};
//        System.out.println(new String(testMessage));
//        System.out.println(testMessage.length);
//        WebSocketFrame message = new WebSocketFrame(testMessage, 0, testMessage.length);
//        System.out.println(message);

//        String s = "WebSocketFrame message = new WebSocketFrame";
//        byte[] b = s.getBytes();
//        WebSocketFrame frame = new WebSocketFrame();
//        frame.mask(b, frame.intToBytes(123123), 0, b.length);
//        System.out.println(new String(b));
//        frame.mask(b, frame.intToBytes(123123), 0, b.length);
//        System.out.println(new String(b));


//        SimpleWebSocketClient client = new SimpleWebSocketClient("ws://localhost:8080/BrochureDownloader/test") {
//        SimpleWebSocketClient client = new SimpleWebSocketClient("ws://localhost:8080/BrochureDownloader/echo") {
        SimpleWebSocketClient client = new SimpleWebSocketClient("ws://localhost:8084/echo") {
            //        SimpleWebSocketClient client = new SimpleWebSocketClient("ws://localhost:8084/time") {
            @Override
            public void onMessage(Message message) {
                System.out.println("onMessage: " + message.asString());
            }
        };
        client.start();

        client.send("foo bar");

    }
}
