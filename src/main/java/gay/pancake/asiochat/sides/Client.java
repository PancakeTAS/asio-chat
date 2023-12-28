package gay.pancake.asiochat.sides;

import gay.pancake.asiochat.audio.AudioDevice;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * asio-chat client
 *
 * @author Pancake
 */
public class Client {

    /** The audio device */
    private AudioDevice device;

    /** The socket */
    private Socket socket;
    /** The input stream */
    private DataInputStream in;
    /** The output stream */
    private DataOutputStream out;

    /** The exit callback */
    private final CompletableFuture<Void> exitCallback = new CompletableFuture<>();

    /**
     * Connect to the server
     *
     * @param asio The ASIO driver
     * @param host The host
     * @param port The port
     * @param inputChannel The input channel index
     * @param outputChannelLeft The left output channel index
     * @param outputChannelRight The right output channel index
     * @throws IOException If the connection fails
     */
    public void start(String asio, String host, int port, int inputChannel, int outputChannelLeft, int outputChannelRight) throws IOException {
        // connect to the server
        this.socket = new Socket();
        this.socket.connect(new InetSocketAddress(host, port), 10000);
        this.in = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(this.socket.getOutputStream()));

        // create device
        this.device = new AudioDevice(asio, this::bufferSwitch);

        var bufferSize = this.in.readInt();
        this.device.onBufferSizeChange(newSize -> {
            if (bufferSize != newSize) {
                System.err.println("Buffer size mismatch: " + bufferSize + " != " + newSize);
                this.stop();
            }
        });

        var sampleRate = this.in.readInt();
        this.device.onSampleRateChange(newRate -> {
            if (sampleRate != newRate) {
                System.err.println("Sample rate mismatch: " + sampleRate + " != " + newRate);
                this.stop();
            }
        });

        // start device
        this.device.start(inputChannel, outputChannelLeft, outputChannelRight);
    }

    /**
     * Called when the buffer switches
     *
     * @param buffer The buffer
     */
    public void bufferSwitch(byte[] buffer) {
        try {
            // return if the socket is closed
            if (this.socket.isClosed()) {
                Arrays.fill(buffer, (byte) 0);
                return;
            }

            // write input channels
            this.out.write(buffer, 0, buffer.length);

            // return if not enough data is available
            if (this.in.available() < buffer.length) {
                Arrays.fill(buffer, (byte) 0);
                return;
            }

            // write output channels
            this.in.read(buffer, 0, buffer.length);
        } catch (SocketException e) {
            Arrays.fill(buffer, (byte) 0);
            this.exitCallback.completeExceptionally(e);
        } catch (IOException e) {
            Arrays.fill(buffer, (byte) 0);
            e.printStackTrace();
        }
    }

    /**
     * Return the exit callback
     *
     * @return The exit callback
     */
    public CompletableFuture<Void> getExitCallback() {
        return this.exitCallback;
    }

    /**
     * Stop the ASIO driver
     */
    public void stop() {
        try {
            this.in.close();
            this.out.close();
            this.socket.close();
        } catch (Exception ignored) {

        }

        this.device.stop();

        this.exitCallback.complete(null);
    }

}
