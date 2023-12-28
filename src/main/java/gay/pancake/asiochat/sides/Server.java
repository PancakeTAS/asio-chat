package gay.pancake.asiochat.sides;

import gay.pancake.asiochat.audio.AudioDevice;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * asio-chat server
 *
 * @author Pancake
 */
public class Server {

    /** The server socket */
    private ServerSocket socket;
    /** The audio device */
    private AudioDevice device;

    /** The socket */
    private Socket client;
    /** The input stream */
    private DataInputStream in;
    /** The output stream */
    private DataOutputStream out;

    /** The exit callback */
    private final CompletableFuture<Void> exitCallback = new CompletableFuture<>();

    /**
     * Connect to the client
     *
     * @param asio The name of the ASIO driver to use
     * @param port The port to listen on
     * @param inputChannel The input channel index
     * @param leftOutputChannel The left output channel index
     * @param rightOutputChannel The right output channel index
     * @throws IOException If the server fails to start
     */
    public void start(String asio, int port, int inputChannel, int leftOutputChannel, int rightOutputChannel) throws IOException {
        // connect to the client
        this.socket = new ServerSocket(port);
        this.client = this.socket.accept();
        this.in = new DataInputStream(client.getInputStream());
        this.out = new DataOutputStream(client.getOutputStream());

        // create device
        this.device = new AudioDevice(asio, this::bufferSwitch);

        var bufferSize = this.device.getBufferSize();
        this.device.onBufferSizeChange(newSize -> {
            if (bufferSize != newSize) {
                System.err.println("Buffer size mismatch: " + bufferSize + " != " + newSize);
                this.stop();
            }
        });

        var sampleRate = this.device.getSampleRate();
        this.device.onSampleRateChange(newRate -> {
            if (sampleRate != newRate) {
                System.err.println("Sample rate mismatch: " + sampleRate + " != " + newRate);
                this.stop();
            }
        });

        // update client
        this.out.writeInt(bufferSize);
        this.out.writeInt(sampleRate);
        this.out.flush();

        // start device
        this.device.start(inputChannel, leftOutputChannel, rightOutputChannel);
    }

    /**
     * Called when the buffer switches
     *
     * @param buffer The buffer
     */
    public void bufferSwitch(byte[] buffer) {
        try {
            // return if the client is closed
            if (this.client.isClosed()) {
                Arrays.fill(buffer, (byte) 0);
                return;
            }

            // read input channel
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
            this.client.close();
            this.socket.close();
        } catch (Exception ignored) {

        }

        this.device.stop();

        this.exitCallback.complete(null);
    }

}
